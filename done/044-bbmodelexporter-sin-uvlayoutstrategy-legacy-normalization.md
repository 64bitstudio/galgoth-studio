# 044 — BBModelExporterV5/V4: export(model) sin UvLayoutStrategy + LegacyUvNormalizationService

**Milestone:** M7 · **Depende de:** 010, 014, 041 · **HUs:** HU-43 · **Épica:** O (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §3 — reversión directa de una decisión previa, explícita en el documento: el exportador nunca debe calcular UV). `BBModelExporterV4`/`V5` cambian de firma `export(model, uvLayoutStrategy)` a `export(model)`: nunca invocan ninguna estrategia de layout, nunca mutan el `UvLayout` recibido, serializan EXACTAMENTE `model.uv()`/`model.texture()`. Para revisiones legacy de Fase 1+2 (que dependían del recompute automático), se agrega `LegacyUvNormalizationService` como paso explícito y separado, invocado por `MobExportService` ANTES del exportador.

## Criterios de aceptación (TDD)
- Dado `BBModelExporterV5.export(model)` (nueva firma, un solo argumento), cuando exporta cualquier `MobProjectModel`, entonces NUNCA invoca `UvLayoutSelector`/`AlphaAutoPackStrategy`/`StableUvStrategy` — verificado con un mock/spy que falla el test si se invoca cualquiera de esas clases.
- Dado el mismo modelo, cuando se exporta dos veces sin cambios entre medio, entonces produce bytes idénticos (determinismo, sin dependencia de estado externo).
- Dado los tests/fixtures existentes de 010/011/012/013/014 (Blockbench real), cuando se actualizan a la nueva firma, entonces producen exactamente el mismo `.bbmodel` que antes del cambio — regresión cero, ningún fixture nuevo necesario para este caso.
- Dado `LegacyUvNormalizationService.normalizeIfSafe(model)`, cuando el modelo NO tiene ninguna región `PAINTED`/`ORPHAN` Y su `UvLayout` almacenado difiere estructuralmente de lo que `AlphaAutoPackStrategy` calcularía hoy para esa geometría, entonces devuelve un `MobProjectModel` con el `UvLayout` recalculado — transitorio, en memoria, NUNCA persistido de vuelta a la Revision.
- Dado un modelo con al menos una región `PAINTED`, cuando se invoca `normalizeIfSafe`, entonces es un no-op (devuelve el modelo sin tocar) — nunca normaliza sobre contenido pintado.
- Dado una fixture real de una revisión de Fase 1+2 (pre-existente, sin campo `status`), cuando se exporta con el pipeline completo (`normalizeIfSafe` → `export`), entonces el `.bbmodel` resultante es idéntico al que producía el exportador antes de este ticket — test de regresión explícito con esa fixture.
- Dado un modelo con textura real pintada, cuando se exporta, entonces la UV serializada es EXACTAMENTE la almacenada en la Revision (sin recompute) y el `.bbmodel` abre en Blockbench real sin diálogo de reparación.

## Hecho

Implementado tal como lo describe el ticket, con dos hallazgos reales
documentados abajo (no ocultados, no improvisados).

**Cambio de firma:**
- `BBModelExporterV5.export(MobProjectModel)` y `BBModelExporterV4.export(MobProjectModel)`
  son ahora el ÚNICO overload de cada exportador — el de 2 argumentos
  (`export(model, uvLayoutStrategy)`) se eliminó por completo. Ninguna de
  las dos clases importa ni referencia `UvLayoutStrategy`/`UvLayoutSelector`/
  `AlphaAutoPackStrategy`/`StableUvStrategy` — cero dependencia de
  `domain.uv`, garantía estructural (no solo verificada en runtime).
  Serializan exactamente `model.uv()`/`model.cuboids()`/`model.texture()`
  tal como llegan, sin excepción para el caso "cero regiones pintadas".
- `BBModelExportSupport.withFreshUv(...)` se eliminó (ya no tiene ningún
  caller).
- `buildPlaceholderTexture(...)` (ticket 011, mecanismo ORTOGONAL a la UV:
  solo genera un checkerboard con las dimensiones de `model.texture()`
  para que Blockbench/`FmmCompatibilityValidator` puedan resolver el
  índice de textura que ya trae cada `Face`) se mantiene sin cambios de
  fondo — sigue siendo el único overload que lo embebe, ahora siempre
  (antes solo lo hacía el overload de 2 argumentos).

**Los 3 callers reales, `UvLayoutStrategy` desapareció POR COMPLETO de los tres**
(ninguno lo necesitaba para nada más que exportar — confirmado leyendo
cada clase completa antes de tocarla):
- `MobExportService` (`project/export/`): perdió el campo/parámetro
  `@Qualifier("alphaAutoPackStrategy") UvLayoutStrategy uvLayoutStrategy`
  por completo. Gana `LegacyUvNormalizationService legacyUvNormalizationService`
  inyectado, invocado en AMBOS puntos donde exporta desde una Revision
  persistida (`getStatus`/`exportBbmodel`) ANTES de `BBModelExporterV5.export(...)`
  — nunca dentro del exportador.
- `MobGenerationService` (`aiorchestrator/`): perdió el campo/parámetro
  igual. Exporta un modelo fresco del pipeline de generación de ESTA
  sesión (nunca una Revision legacy) — sin `LegacyUvNormalizationService`,
  no lo necesita.
- `GenerationResultService` (`aiorchestrator/`): mismo caso — exporta una
  propuesta (`ai_jobs.proposal_jsonb`) recién salida del pipeline, no una
  Revision persistida — sin `LegacyUvNormalizationService`.

**`LegacyUvNormalizationService` nuevo** (`domain/uv/`, `@Service`):
`normalizeIfSafe(model)` es no-op salvo que (a) `model.uv().regions()` no
tenga ningún `PAINTED`/`ORPHAN` Y (b) el conjunto de `UvRegion` almacenado
difiera (comparación por `Set`, sin importar orden) del que
`AlphaAutoPackStrategy.layout(cuboids, w, h)` calcula hoy para la misma
geometría. Cuando normaliza, el `UvLayout`/`cuboids` recalculados viven
SOLO en el `MobProjectModel` devuelto — nunca se persiste de vuelta (se
verificó explícitamente con test que el modelo original de entrada queda
intacto). Si la geometría de una revisión legacy ya no cupiera hoy en su
propio atlas, `AlphaAutoPackStrategy` propaga `UvAtlasOverflowException`
tal cual — no se captura ni se oculta (edge case documentado, sin AC que
lo cubra, sin ocurrencia conocida en datos reales).

**Tests reales corridos en verde (`./gradlew clean test`, build limpia):
292 tests, 0 failures, 0 errors, 0 skipped** (baseline pre-ticket: 285;
+7 tests nuevos: 5 de `LegacyUvNormalizationServiceTest`, 1 de
`LegacyRevisionExportRegressionTest`, 1 de `BBModelExporterV5PaintedUvTest`
— el resto de los archivos tocados mantiene su conteo de tests, solo con
cuerpos/asserts adaptados a la firma nueva).

Tests nuevos/reescritos:
- `LegacyUvNormalizationServiceTest` (nuevo): las 5 combinaciones de las
  condiciones (a)/(b) — PAINTED siempre no-op, ORPHAN siempre no-op, UV ya
  coincidente no-op, UV distinta SÍ normaliza (y preserva `reservations`
  intactas, y no muta el modelo original), y el caso trivial sin cuboids.
- `LegacyRevisionExportRegressionTest` (nuevo): deserializa una fixture
  JSON de una revisión Fase 1+2 (sin `status` en regiones, sin
  `reservations` en `uv` — misma convención que
  `UvRegionUvReservationContractTest`, ticket 040) con una UV almacenada
  DELIBERADAMENTE distinta de la que calcularía hoy `AlphaAutoPackStrategy`
  (ejercita la rama que sí normaliza, la más exigente), y confirma que
  `normalizeIfSafe → export` produce EXACTAMENTE el mismo `.bbmodel` que
  el pipeline de antes de este ticket (que siempre recomputaba, sin
  importar lo almacenado — reconstruido inline en el test para no
  depender del overload eliminado).
- `BBModelExporterV5PaintedUvTest` (nuevo): una región `PAINTED` con `rect`
  fuera de donde `AlphaAutoPackStrategy` la ubicaría se exporta con esa UV
  EXACTA (sin recompute) y el `.bbmodel` resultante pasa
  `FmmCompatibilityValidator` sin hallazgos.
- `BBModelExporterV5PlaceholderTextureTest` (reescrito, no eliminado): sus
  3 AC originales (ticket 011) probaban exactamente el comportamiento que
  este ticket revierte — recompute + `UvAtlasOverflowException` AL
  EXPORTAR. Se reescribió para el contrato nuevo: los modelos llegan con
  la UV YA resuelta (como la entregaría hoy `GeometryEngine`/
  `UvLayoutSelector`), se agregó el test de determinismo explícito del AC
  #2 del ticket 044, y el caso de overflow se invirtió a su contraparte
  correcta: una UV que excedería el atlas según `AlphaAutoPackStrategy` se
  exporta SIN fallar y con la UV intacta (prueba indirecta de que ninguna
  estrategia se invocó).
- `BBModelExporterV5SnapshotTest` (golden regenerado, no tocado a mano): el
  golden (`bbmodel-golden/model-spec-example.bbmodel.json`) tenía
  `"textures": []` porque su fixture predata la textura placeholder de
  011 en esa ruta (usaba el overload de 1 argumento, que nunca la
  embebía); al fusionar ambos overloads en uno solo, ahora SIEMPRE la
  embebe. Se regeneró ejecutando el exportador real una vez (mismo
  procedimiento documentado en el propio test) y se revisó a mano el
  diff: el ÚNICO cambio es la entrada nueva de `textures` (ver hallazgo
  del UUID fijo abajo) — cero cambios en `groups`/`outliner`/`elements`.
- `FmmCompatibilityValidatorTest`, `E2eAcceptanceBbmodelExportTest`: call
  sites actualizados a la firma nueva. En `FmmCompatibilityValidatorTest`
  el modelo ahora se construye con la UV ya resuelta por
  `AlphaAutoPackStrategy` ANTES de exportar (responsabilidad que antes
  tenía el propio exportador). En `E2eAcceptanceBbmodelExportTest` no hizo
  falta ese paso — el modelo sale de `GeometryPlannerService.plan(...)`,
  que ya lo entrega con UV resuelta.

**Nota sobre el AC "verificado con un mock/spy que falla el test si se
invoca cualquiera de esas clases"**: con la firma nueva (`export(model)`,
sin parámetro de estrategia) no existe ningún punto de inyección donde
poner un spy — es, literalmente, imposible que el exportador invoque
`UvLayoutStrategy`/`AlphaAutoPackStrategy`/`StableUvStrategy`/`UvLayoutSelector`,
porque ninguna de las dos clases las importa ni las referencia (verificado
con `grep` sobre los `.java` compilados, no solo leído). Es una garantía
MÁS fuerte que un spy en runtime (que solo prueba "no se invocó en ESTE
test", no "no puede invocarse nunca"). El test de comportamiento
(`unaUvQueExcederiaElAtlasSegunAlphaAutoPackSeExportaSinFallarYSinTocarla`,
`BBModelExporterV5PlaceholderTextureTest`) es la prueba indirecta que el
AC pide en la práctica: si el exportador invocara la estrategia, ese test
fallaría con `UvAtlasOverflowException`.

**Hallazgo real #1 (corregido, no solo reportado)**: `BBModelExportSupport
.buildPlaceholderTexture` asignaba `UUID.randomUUID()` al `uuid` de la
textura embebida — inofensivo mientras el exportador SIEMPRE recomputaba
UV en cada export (ningún test ni caller dependía de bytes estables), pero
directamente incompatible con el AC #2 de este mismo ticket
("exportar el mismo modelo dos veces produce bytes idénticos") una vez que
`export(model)` es el único overload y también el que embebe la textura.
Corregido a un UUID fijo (`00000000-0000-4000-8000-000000000001`). Sin
esto, el AC de determinismo era literalmente imposible de cumplir para
cualquier modelo cuyas caras ya tuvieran un índice de textura asignado
(que es el 100% de los modelos reales, vía `AlphaAutoPackStrategy`/
`StableUvStrategy`).

**Hallazgo real #2 (reportado explícitamente, no resuelto en este ticket —
fuera de alcance declarado)**: `LegacyUvNormalizationService.normalizeIfSafe`
puede propagar `UvAtlasOverflowException` si la geometría de una revisión
legacy ya no cupiera en su propio atlas según el algoritmo de
`AlphaAutoPackStrategy` vigente HOY (p. ej. si un ajuste futuro del
box-unwrap necesitara más espacio que el de una iteración anterior). El
diseño técnico §3 menciona la posibilidad de "ajustes menores entre
iteraciones de 006/007" como motivo de ser de este servicio, pero no
especifica qué hacer si el propio recompute de seguridad falla. No se negó
en silencio ni se atrapó la excepción — se decidió dejarla propagar
(consistente con "el atlas nunca crece en silencio", regla ya establecida
en 006), documentado acá para que el PO decida si amerita un ticket
propio (p. ej. degradar a no-op con un log de warning en vez de romper el
export de una revisión vieja).

**Propuesta de mejora continua**: los tickets 041/042/044 de esta misma
sesión encontraron, cada uno, al menos un hallazgo real de determinismo o
de cobertura no evidente hasta escribir el test (UUID aleatorio en un
export que se asume determinista, en este caso). Una regla de arquitectura tipo "ningún método bajo `domain/export` o
`domain/uv` puede usar `UUID.randomUUID()`/`Math.random()`/`Instant.now()`
directamente sin pasar por una fuente inyectada" (ArchUnit, el proyecto
no lo usa todavía) prevendría esta clase de hallazgo por construcción en
vez de depender de un code review manual.
