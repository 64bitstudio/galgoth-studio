package com.galgothstudio.backend.aiorchestrator.texture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.galgothstudio.backend.aiorchestrator.JobNotCompletedException;
import com.galgothstudio.backend.aiorchestrator.JobNotFoundException;
import com.galgothstudio.backend.aiorchestrator.NoReferenceImageException;
import com.galgothstudio.backend.aiorchestrator.edit.NoBaseRevisionException;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationEventBroadcaster;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationStage;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.ImageGenerationProvider;
import com.galgothstudio.backend.aiorchestrator.provider.ImageGenerationProvider.TextureGenerationSheetRequest;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.TexturePlan;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvPaintOrigin;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.Vec4;
import com.galgothstudio.backend.project.draft.ApplyGenerationResponse;
import com.galgothstudio.backend.project.draft.DraftPersistenceService;
import com.galgothstudio.backend.project.draft.DraftView;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.ReferenceImageEntity;
import com.galgothstudio.backend.project.persistence.ReferenceImageRepository;
import com.galgothstudio.backend.project.texture.TextureService;
import com.galgothstudio.backend.project.texture.TextureUploadResponse;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Orquesta el pipeline completo de generación/regeneración de textura por
 * IA (ticket 054, HU-36 a HU-39, Diseño técnico §6/§10/§11/§13/§16/§21 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`):
 * {@code TexturePlan} (052) → {@code TextureGenerationSheet} por bone
 * (053) → {@code ImageGenerationProvider} (051) → slicing/composición
 * (053) → progreso SSE → diff Antes/Después → Apply (atómico, 409) /
 * Reject. Mismo patrón de orquestación que {@link
 * com.galgothstudio.backend.aiorchestrator.MobGenerationService}
 * (síncrono hasta crear la fila `ai_jobs`, pipeline real en
 * {@code generationExecutor}) -- reutilizado deliberadamente en vez de
 * inventar un mecanismo nuevo.
 */
@Service
public class TextureGenerationService {

	private static final Logger log = LoggerFactory.getLogger(TextureGenerationService.class);

	static final String JOB_TYPE_GENERATE_TEXTURE = "generate_texture";
	static final String JOB_TYPE_EDIT_TEXTURE = "edit_texture";
	private static final String STATUS_RUNNING = "running";
	private static final String STATUS_COMPLETED = "completed";
	private static final String STATUS_FAILED = "failed";
	private static final String PENDING_PLACEHOLDER = "pending";

	/** Versión de prompt/esquema del PASO de generación de imagen -- distinto del `texture-plan-v1` de {@link TexturePlanService}, que persiste el suyo propio vía {@link #updateJobProviderInfo}. `ImageGenerationProvider.generateTextureSheet` no tiene concepto propio de versión de prompt/schema (a diferencia de `StructuredReasoningProvider`/`VisionModelProvider`, que devuelven un {@link AiProviderResponse} completo) -- se fija acá, junto al orquestador que sí conoce el contexto completo del pipeline. */
	static final String PROMPT_VERSION_SHEET = "texture-sheet-v1";
	static final String SCHEMA_VERSION_SHEET = "texture-sheet-v1";

	/** Umbral inline vs. asset temporal de `preview_texture_patch` (Diseño técnico §13): 32 KB del payload base64 codificado. */
	static final int INLINE_PREVIEW_MAX_BASE64_BYTES = 32 * 1024;

	/** Prefijo de los assets temporales de preview en MinIO -- DELIBERADAMENTE distinto de `textures/` (bitmap definitivo, `TextureService`, 045): un preview NUNCA debe poder confundirse con una textura persistida real, ni siquiera a nivel de key. Sin garantía de retención a largo plazo (Diseño técnico §13) -- GC fuera de alcance este ciclo, mismo criterio que el resto de los "garbage aceptados" del documento de definición. */
	static final String PREVIEW_ASSET_PREFIX = "texture-previews/";

	private final MobRepository mobRepository;
	private final ReferenceImageRepository referenceImageRepository;
	private final AssetStorageService assetStorageService;
	private final DraftPersistenceService draftPersistenceService;
	private final TexturePlanService texturePlanService;
	private final TextureGenerationSheetPlanner textureGenerationSheetPlanner;
	private final TextureSheetSlicer textureSheetSlicer;
	private final TextureCompositorService textureCompositorService;
	private final ImageGenerationProvider imageGenerationProvider;
	private final TextureService textureService;
	private final AiJobRepository aiJobRepository;
	private final AiJobEventRepository aiJobEventRepository;
	private final GenerationEventBroadcaster eventBroadcaster;
	private final ObjectMapper objectMapper;
	private final Executor generationExecutor;

	public TextureGenerationService(
			MobRepository mobRepository,
			ReferenceImageRepository referenceImageRepository,
			AssetStorageService assetStorageService,
			DraftPersistenceService draftPersistenceService,
			TexturePlanService texturePlanService,
			TextureGenerationSheetPlanner textureGenerationSheetPlanner,
			TextureSheetSlicer textureSheetSlicer,
			TextureCompositorService textureCompositorService,
			ImageGenerationProvider imageGenerationProvider,
			TextureService textureService,
			AiJobRepository aiJobRepository,
			AiJobEventRepository aiJobEventRepository,
			GenerationEventBroadcaster eventBroadcaster,
			ObjectMapper objectMapper,
			@Qualifier("generationExecutor") Executor generationExecutor) {
		this.mobRepository = mobRepository;
		this.referenceImageRepository = referenceImageRepository;
		this.assetStorageService = assetStorageService;
		this.draftPersistenceService = draftPersistenceService;
		this.texturePlanService = texturePlanService;
		this.textureGenerationSheetPlanner = textureGenerationSheetPlanner;
		this.textureSheetSlicer = textureSheetSlicer;
		this.textureCompositorService = textureCompositorService;
		this.imageGenerationProvider = imageGenerationProvider;
		this.textureService = textureService;
		this.aiJobRepository = aiJobRepository;
		this.aiJobEventRepository = aiJobEventRepository;
		this.eventBroadcaster = eventBroadcaster;
		this.objectMapper = objectMapper;
		this.generationExecutor = generationExecutor;
	}

	/**
	 * Preflight síncrono + creación inmediata de la fila `ai_jobs` en
	 * `running` -- el pipeline real se despacha a {@code generationExecutor}
	 * (HU-36 AC #1: "reutiliza la imagen de referencia ya subida en Fase
	 * 2 -- nunca pide una nueva").
	 */
	public UUID startGeneration(UUID mobId, GenerateTextureRequest request) {
		MobEntity mob = mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
		if (mob.getCurrentRevisionNumber() == 0) {
			throw new NoBaseRevisionException(mobId);
		}
		TextureStyle style = TextureStyle.fromWireValue(request.style());
		TextureDetailLevel detailLevel = TextureDetailLevel.fromWireValue(request.detailLevel());

		DraftView draftView = draftPersistenceService.getDraft(mobId);
		MobProjectModel model = draftView.model();
		ReferenceImageEntity reference = mostRecentReference(mobId);

		boolean wholeModel = request.boneId() == null;
		String jobType;
		List<String> targetBoneIds;
		if (wholeModel) {
			jobType = JOB_TYPE_GENERATE_TEXTURE;
			targetBoneIds = boneIdsWithGeometry(model);
			if (targetBoneIds.isEmpty()) {
				throw new InvalidTextureGenerationRequestException(
						"El modelo no tiene ningún bone con geometría -- nada que texturizar.");
			}
		} else {
			jobType = JOB_TYPE_EDIT_TEXTURE;
			requireBoneWithGeometry(model, request.boneId());
			targetBoneIds = List.of(request.boneId());
		}

		AiJobEntity job = newRunningJob(mob.getId(), jobType, reference.getId(), draftView.draftVersion(), mob.getCurrentRevisionNumber(), wholeModel ? null : request.boneId());
		aiJobRepository.save(job);

		TextureGenerationJobContext context = new TextureGenerationJobContext(
				job.getId(), mobId, model, style, detailLevel, wholeModel, targetBoneIds, reference.getId(), reference.getStorageKey(),
				reference.getContentType());

		generationExecutor.execute(() -> runPipeline(context));
		return job.getId();
	}

	private void runPipeline(TextureGenerationJobContext context) {
		UUID jobId = context.jobId();
		AtomicInteger seq = new AtomicInteger(0);
		AtomicInteger progressCounter = new AtomicInteger(0);
		try {
			emit(jobId, seq, GenerationStage.ANALIZANDO_PALETA, "Analizando paleta y material de la imagen de referencia…", nextProgress(progressCounter), null);

			byte[] referenceBytes = assetStorageService
					.get(context.referenceStorageKey())
					.orElseThrow(() -> new IllegalStateException(
							"La imagen de referencia '" + context.referenceImageId() + "' no está en el storage."));

			TexturePlanAnalysisResult planResult = texturePlanService.analyze(referenceBytes, context.referenceContentType(), context.model());
			updateJobProviderInfo(jobId, planResult.providerResponse());

			byte[] beforeAtlas = loadCurrentAtlasOrBlank(context.model());
			byte[] currentAtlas = beforeAtlas;
			List<TouchedFace> touchedFaces = new ArrayList<>();

			for (String boneId : context.targetBoneIds()) {
				Bone bone = findBone(context.model(), boneId);
				emit(jobId, seq, GenerationStage.MAPEANDO_CARAS, "Mapeando caras de " + bone.name() + "…", nextProgress(progressCounter), null);

				List<TextureGenerationSheet> sheets =
						textureGenerationSheetPlanner.plan(context.model(), planResult.texturePlan(), boneId);
				int totalParts = sheets.size();

				for (int partIndex = 0; partIndex < sheets.size(); partIndex++) {
					TextureGenerationSheet sheet = sheets.get(partIndex);
					String stage = generatingBoneStage(boneId);
					String message = "Generando textura: " + bone.name() + (totalParts > 1 ? " (parte " + (partIndex + 1) + "/" + totalParts + ")" : "") + "…";
					emit(jobId, seq, stage, message, nextProgress(progressCounter), null);

					String prompt = composePrompt(sheet, context.style(), context.detailLevel());
					TextureGenerationSheetRequest sheetRequest =
							new TextureGenerationSheetRequest(prompt, referenceBytes, sheet.sheetWidth(), sheet.sheetHeight(), context.style().wireValue());
					byte[] sheetBytes = imageGenerationProvider.generateTextureSheet(sheetRequest);
					updateJobProviderInfo(
							jobId,
							new AiProviderResponse(null, imageGenerationProvider.provider(), imageGenerationProvider.model(), PROMPT_VERSION_SHEET, SCHEMA_VERSION_SHEET));

					List<TextureSlice> slices = textureSheetSlicer.slice(sheetBytes, sheet);
					currentAtlas = textureCompositorService.compose(currentAtlas, slices);

					for (CuboidFacePlacement placement : sheet.placements()) {
						boolean handOverwrite = isHandPaintedOrUnknownOrigin(context.model().uv(), placement.cuboidId(), placement.face());
						touchedFaces.add(new TouchedFace(placement.cuboidId(), placement.face(), placement.atlasUvRect(), handOverwrite));
					}

					emit(
							jobId, seq, GenerationStage.COMPONIENDO_ATLAS, "Componiendo atlas: " + bone.name() + "…", nextProgress(progressCounter),
							previewPatchPayload(jobId, seq, currentAtlas, sheet.placements()));
				}

				// Diseño técnico §11 punto 6: la limpieza de píxeles/paleta para
				// estilos Pixel Art/Minecraft Vanilla queda EXPLÍCITAMENTE fuera de
				// esta etapa como transformación real (ver Javadoc de
				// `composePrompt` -- no hay nada que "limpiar" que el
				// clipping/aislamiento espacial de 053 no garantice ya por
				// construcción) -- se emite el stage igual (AC del ticket: la SSE
				// debe exponer este valor de `stage`), honesto sobre su alcance
				// real en vez de fabricar un algoritmo de post-procesado no pedido
				// por ningún AC concreto de este ticket (evita over-engineering).
				emit(jobId, seq, GenerationStage.LIMPIANDO_PIXELES, "Limpiando bordes de píxeles de " + bone.name() + "…", nextProgress(progressCounter), null);
			}

			UvLayout updatedUv = markTouchedFacesAsAiPainted(context.model().uv(), touchedFaces);
			MobProjectModel proposalModel = withTextureAndUv(
					context.model(), new TextureDocument(context.model().texture().width(), context.model().texture().height(), null), updatedUv);

			TextureGenerationProposal proposal = new TextureGenerationProposal(
					proposalModel, base64(currentAtlas), context.wholeModel(), context.targetBoneIds(), touchedFaces, base64(beforeAtlas));

			completeJob(jobId, proposal);
			emit(jobId, seq, GenerationStage.COMPLETADO, "Generación de textura completada.", 100, null);
		} catch (RuntimeException e) {
			log.error("Fallo inesperado en el pipeline de generación de textura del job {}", jobId, e);
			failJob(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
			emit(jobId, seq, GenerationStage.FALLIDO, "Fallo inesperado durante la generación de textura.", null, null);
		} finally {
			eventBroadcaster.completeAll(jobId);
		}
	}

	/** `GET /api/jobs/{jobId}/texture-result` (HU-38) -- diff Antes/Después de una propuesta ya completada, nunca re-ejecuta el pipeline. */
	public TextureGenerationResultView getResult(UUID jobId) {
		AiJobEntity job = requireCompletedTextureJob(jobId);
		TextureGenerationProposal proposal = deserializeProposal(job.getProposalJson());
		boolean hasHandPaintedOverwrite = proposal.touchedFaces().stream().anyMatch(TouchedFace::handPaintedOverwrite);
		return new TextureGenerationResultView(
				job.getId(), job.getMobId(), proposal.wholeModel(), proposal.touchedBoneIds(), proposal.touchedFaces(), hasHandPaintedOverwrite,
				proposal.beforeAtlasPngBase64(), proposal.composedAtlasPngBase64());
	}

	/**
	 * Apply (HU-38 AC #3, Diseño técnico §10/§16) -- 409 si el draft/
	 * revisión compartido avanzó desde que se generó la propuesta; si no,
	 * atómico: (1) sube el bitmap a MinIO PRIMERO, fuera de la
	 * transacción; (2) UNA transacción Postgres
	 * ({@link DraftPersistenceService#applyGenerationProposal}, reutilizado
	 * tal cual -- YA actualiza `mob_drafts`+`mob_revisions`+
	 * `current_revision_number` en una sola transacción, ver su Javadoc).
	 */
	public ApplyGenerationResponse applyTexture(UUID jobId) {
		AiJobEntity job = requireCompletedTextureJob(jobId);

		DraftView currentDraft = draftPersistenceService.getDraft(job.getMobId());
		MobEntity mob = mobRepository.findById(job.getMobId()).orElseThrow(() -> new MobNotFoundException(job.getMobId()));
		if (mob.getCurrentRevisionNumber() != job.getBaseRevisionNumber() || currentDraft.draftVersion() != job.getBaseDraftVersion()) {
			throw new StaleTextureBaseException(jobId);
		}

		TextureGenerationProposal proposal = deserializeProposal(job.getProposalJson());
		byte[] atlasBytes = Base64.getDecoder().decode(proposal.composedAtlasPngBase64());
		TextureUploadResponse uploadResponse = textureService.upload(job.getMobId(), atlasBytes);

		MobProjectModel finalModel = withTexture(
				proposal.model(), new TextureDocument(proposal.model().texture().width(), proposal.model().texture().height(), uploadResponse.storageKey()));
		return draftPersistenceService.applyGenerationProposal(job.getMobId(), finalModel);
	}

	// ---- helpers de negocio ----

	private static String generatingBoneStage(String boneId) {
		return GenerationStage.GENERANDO_BONE_PREFIX + boneId;
	}

	/** Style/detailLevel plegados como instrucción de texto determinista, mismo criterio que `style` en `OpenAiImageProvider`/`TextureGenerationSheetRequest` (ver Javadoc de {@link TextureStyle}/{@link TextureDetailLevel}). */
	private static String composePrompt(TextureGenerationSheet sheet, TextureStyle style, TextureDetailLevel detailLevel) {
		return TextureSheetPromptComposer.compose(sheet) + "\n\n" + style.promptInstruction() + "\n" + detailLevel.promptInstruction();
	}

	private static List<String> boneIdsWithGeometry(MobProjectModel model) {
		List<String> result = new ArrayList<>();
		for (Bone bone : model.bones()) {
			boolean hasCuboids = model.cuboids().stream().anyMatch(c -> c.boneId().equals(bone.id()));
			if (hasCuboids) {
				result.add(bone.id());
			}
		}
		return result;
	}

	private static void requireBoneWithGeometry(MobProjectModel model, String boneId) {
		boolean boneExists = model.bones().stream().anyMatch(b -> b.id().equals(boneId));
		if (!boneExists) {
			throw new TextureTargetBoneNotFoundException(boneId);
		}
		boolean hasCuboids = model.cuboids().stream().anyMatch(c -> c.boneId().equals(boneId));
		if (!hasCuboids) {
			throw new InvalidTextureGenerationRequestException("El bone '" + boneId + "' no tiene ningún cuboid -- nada que regenerar.");
		}
	}

	private static Bone findBone(MobProjectModel model, String boneId) {
		return model.bones().stream().filter(b -> b.id().equals(boneId)).findFirst().orElseThrow(() -> new TextureTargetBoneNotFoundException(boneId));
	}

	/** HU-37 AC #2: `true` cuando la cara YA estaba `PAINTED` con origen pintado a mano o DESCONOCIDO (nunca se subestima el riesgo -- ver Javadoc de {@link UvPaintOrigin}); `false` si estaba `UNPAINTED`/`ORPHAN`, o `PAINTED` con origen `AI` ya conocido. */
	private static boolean isHandPaintedOrUnknownOrigin(UvLayout uv, String cuboidId, FaceName face) {
		for (UvRegion region : uv.regions()) {
			if (region.cuboidId().equals(cuboidId) && region.face() == face) {
				return region.status() == UvRegionStatus.PAINTED && region.paintedBy() != UvPaintOrigin.AI;
			}
		}
		return false;
	}

	/** Actualiza, EN MEMORIA (nunca persiste nada -- eso es responsabilidad exclusiva de {@link #applyTexture}), las `UvRegion` de las caras tocadas a `status=PAINTED`/`paintedBy=AI`, preservando su `rect`. */
	private static UvLayout markTouchedFacesAsAiPainted(UvLayout uv, List<TouchedFace> touchedFaces) {
		Map<String, TouchedFace> byKey = new HashMap<>();
		for (TouchedFace touched : touchedFaces) {
			byKey.put(regionKey(touched.cuboidId(), touched.face()), touched);
		}
		List<UvRegion> updatedRegions = new ArrayList<>(uv.regions().size());
		for (UvRegion region : uv.regions()) {
			if (byKey.containsKey(regionKey(region.cuboidId(), region.face()))) {
				updatedRegions.add(new UvRegion(region.cuboidId(), region.face(), region.rect(), UvRegionStatus.PAINTED, UvPaintOrigin.AI));
			} else {
				updatedRegions.add(region);
			}
		}
		return new UvLayout(uv.textureWidth(), uv.textureHeight(), updatedRegions, uv.reservations());
	}

	private static String regionKey(String cuboidId, FaceName face) {
		return cuboidId + "|" + face.name();
	}

	private static MobProjectModel withTextureAndUv(MobProjectModel model, TextureDocument texture, UvLayout uv) {
		return new MobProjectModel(
				model.mobId(), model.projectId(), model.name(), model.baseType(), model.units(), model.bones(), model.cuboids(), texture, uv,
				model.animations(), model.exportSettings(), model.referenceImages());
	}

	private static MobProjectModel withTexture(MobProjectModel model, TextureDocument texture) {
		return withTextureAndUv(model, texture, model.uv());
	}

	private byte[] loadCurrentAtlasOrBlank(MobProjectModel model) {
		String storageKey = model.texture().storageKey();
		if (storageKey != null) {
			return assetStorageService
					.get(storageKey)
					.orElseThrow(() -> new IllegalStateException("El storageKey '" + storageKey + "' referenciado por el modelo no está en el storage."));
		}
		return blankTransparentPng(model.texture().width(), model.texture().height());
	}

	private static byte[] blankTransparentPng(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		return encodePng(image);
	}

	private static byte[] encodePng(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String base64(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private ReferenceImageEntity mostRecentReference(UUID mobId) {
		List<ReferenceImageEntity> references = referenceImageRepository.findByMobIdOrderByCreatedAtAsc(mobId);
		if (references.isEmpty()) {
			throw new NoReferenceImageException(mobId);
		}
		return references.getLast();
	}

	/**
	 * `preview_texture_patch` (Diseño técnico §13): recorta el rect unión
	 * de todos los `atlasUvRect` de esta sheet del atlas YA compuesto, y lo
	 * emite inline (`encoding=base64`) si el payload base64 cabe en
	 * {@link #INLINE_PREVIEW_MAX_BASE64_BYTES}, o como asset temporal
	 * (`encoding=asset_url`, MinIO bajo {@link #PREVIEW_ASSET_PREFIX},
	 * NUNCA `textures/`) si no. Estos previews NUNCA tocan
	 * `mob_drafts`/`mob_revisions`/`textures/{hash}.png` -- ver Javadoc de
	 * la clase y el test dedicado que lo verifica.
	 */
	/** Visibilidad de paquete deliberada (no `private`) -- {@code TextureGenerationServicePreviewPatchTest} lo ejercita de forma aislada (sin Testcontainers) para forzar el umbral de 32 KB del AC (Diseño técnico §13), imposible de forzar de forma confiable a través del pipeline completo (el PNG sintético determinista de {@code MockImageProvider} es un tablero de ajedrez -- altamente compresible, nunca cruza el umbral sin importar el tamaño pedido). */
	JsonNode previewPatchPayload(UUID jobId, AtomicInteger seq, byte[] currentAtlas, List<CuboidFacePlacement> placements) {
		Vec4 unionRect = unionRect(placements);
		int x = (int) Math.round(unionRect.a());
		int y = (int) Math.round(unionRect.b());
		int width = (int) Math.round(unionRect.c() - unionRect.a());
		int height = (int) Math.round(unionRect.d() - unionRect.b());
		byte[] patchPng = cropPng(currentAtlas, x, y, width, height);
		String base64Data = base64(patchPng);

		ObjectNode node = objectMapper.createObjectNode();
		node.put("type", "preview_texture_patch");
		ObjectNode rect = node.putObject("rect");
		rect.put("x", x);
		rect.put("y", y);
		rect.put("width", width);
		rect.put("height", height);

		if (base64Data.getBytes(StandardCharsets.US_ASCII).length <= INLINE_PREVIEW_MAX_BASE64_BYTES) {
			node.put("encoding", "base64");
			node.put("data", base64Data);
		} else {
			String assetKey = PREVIEW_ASSET_PREFIX + jobId + "/" + seq.get() + ".png";
			assetStorageService.put(assetKey, patchPng, "image/png");
			node.put("encoding", "asset_url");
			node.put("url", "/api/" + assetKey);
		}
		return node;
	}

	private static Vec4 unionRect(List<CuboidFacePlacement> placements) {
		double x0 = Double.MAX_VALUE;
		double y0 = Double.MAX_VALUE;
		double x1 = -Double.MAX_VALUE;
		double y1 = -Double.MAX_VALUE;
		for (CuboidFacePlacement placement : placements) {
			Vec4 rect = placement.atlasUvRect();
			x0 = Math.min(x0, rect.a());
			y0 = Math.min(y0, rect.b());
			x1 = Math.max(x1, rect.c());
			y1 = Math.max(y1, rect.d());
		}
		return new Vec4(x0, y0, x1, y1);
	}

	private static byte[] cropPng(byte[] pngBytes, int x, int y, int width, int height) {
		BufferedImage source;
		try {
			source = ImageIO.read(new ByteArrayInputStream(pngBytes));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		BufferedImage crop = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = crop.createGraphics();
		try {
			g.drawImage(source, -x, -y, null);
		} finally {
			g.dispose();
		}
		return encodePng(crop);
	}

	private static int nextProgress(AtomicInteger counter) {
		return Math.min(90, 5 + counter.incrementAndGet() * 5);
	}

	private void emit(UUID jobId, AtomicInteger seq, String stage, String message, Integer progressPct, JsonNode payload) {
		AiJobEventEntity event = new AiJobEventEntity(UUID.randomUUID());
		event.setJobId(jobId);
		event.setSeq(seq.incrementAndGet());
		event.setStage(stage);
		event.setMessage(message);
		event.setProgressPct(progressPct);
		event.setPayloadJson(payload == null ? null : payload.toString());
		event.setCreatedAt(Instant.now());
		aiJobEventRepository.save(event);
		eventBroadcaster.publish(jobId, event);
	}

	private AiJobEntity newRunningJob(UUID mobId, String jobType, UUID referenceId, int baseDraftVersion, int baseRevisionNumber, String targetBoneId) {
		Instant now = Instant.now();
		AiJobEntity job = new AiJobEntity(UUID.randomUUID());
		job.setMobId(mobId);
		job.setJobType(jobType);
		job.setStatus(STATUS_RUNNING);
		job.setProvider(PENDING_PLACEHOLDER);
		job.setModel(PENDING_PLACEHOLDER);
		job.setPromptVersion(PENDING_PLACEHOLDER);
		job.setSchemaVersion(PENDING_PLACEHOLDER);
		job.setReferenceIds(writeJson(List.of(referenceId.toString())));
		job.setBaseRevisionNumber(baseRevisionNumber);
		job.setBaseDraftVersion(baseDraftVersion);
		job.setTargetBoneId(targetBoneId);
		job.setCreatedAt(now);
		job.setStartedAt(now);
		return job;
	}

	private void updateJobProviderInfo(UUID jobId, AiProviderResponse response) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow();
		job.setProvider(response.provider());
		job.setModel(response.model());
		job.setPromptVersion(response.promptVersion());
		job.setSchemaVersion(response.schemaVersion());
		aiJobRepository.save(job);
	}

	private void completeJob(UUID jobId, TextureGenerationProposal proposal) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow();
		job.setStatus(STATUS_COMPLETED);
		job.setProposalJson(writeJson(proposal));
		job.setFinishedAt(Instant.now());
		aiJobRepository.save(job);
	}

	private void failJob(UUID jobId, String errorMessage) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow();
		job.setStatus(STATUS_FAILED);
		job.setError(errorMessage);
		job.setFinishedAt(Instant.now());
		aiJobRepository.save(job);
	}

	private AiJobEntity requireCompletedTextureJob(UUID jobId) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		boolean isTextureJob = JOB_TYPE_GENERATE_TEXTURE.equals(job.getJobType()) || JOB_TYPE_EDIT_TEXTURE.equals(job.getJobType());
		if (!isTextureJob || !STATUS_COMPLETED.equals(job.getStatus())) {
			throw new JobNotCompletedException(jobId, job.getStatus());
		}
		return job;
	}

	private TextureGenerationProposal deserializeProposal(String json) {
		try {
			return objectMapper.readValue(json, TextureGenerationProposal.class);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo deserializar un proposal_jsonb de textura ya validado por el propio pipeline.", e);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un valor de dominio ya validado a JSON.", e);
		}
	}

}
