# 066 — Fix crítico: la textura ya persistida no cargaba al recargar el editor

**Milestone:** post-M8 (hallazgo en producción/dev) · **Relacionado:** 045, 047, 054-065 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Reportado por el PO tras confirmar que "Modelo completo" ya generaba contenido real (065): "el preview 3D no carga el atlas y cuando recargo y vuelvo a abrir la sección de texturas ya no carga las texturas previamente hechas". Reproducido en vivo contra `studio-dev` en una pestaña nueva: tanto el canvas 2D como el preview 3D mostraban el atlas **completamente vacío**, a pesar de que la revisión aplicada (hash-verificada contra el PNG real generado) ya existía en la base de datos y en MinIO.

**Root cause**: `TextureCanvas.vue` (`loadModelAtlas`) NUNCA leía los bytes de la textura ya persistida -- solo llamaba `textureEditorStore.loadAtlas(width, height)` (sin `pixels`), dimensionando un atlas VACÍO sin importar lo que hubiera guardado. Auditado el backend: `TextureService`/`MobTextureController` solo tenían `upload` (`PUT`) -- **nunca existió un endpoint de lectura** para recuperar la textura vigente de un mob. Nadie lo detectó antes porque hasta el ticket 065 nunca hubo una textura real sustancial que valiera la pena recargar -- el flujo de pintado manual siempre mantuvo el atlas en memoria durante una sola sesión.

## Criterios de aceptación (TDD)
- Nuevo `GET /api/mobs/{mobId}/texture`: devuelve los bytes PNG de la textura vigente (resuelta desde el draft, nunca una revisión vieja o un valor cacheado); 404 si el mob no tiene draft, o el draft no tiene ninguna textura real (`storageKey` null), o el asset ya no existe.
- `TextureCanvas.vue` descarga y decodifica esa textura al montar (si `model.texture.storageKey` existe) y carga el atlas con esos píxeles reales -- nunca un atlas vacío cuando hay algo que cargar.
- Si la descarga/decodificación falla, se muestra un error VISIBLE y se bloquea "Guardar" -- nunca se abre un atlas vacío en silencio (riesgo real: pintar sobre "la nada" y guardar sobrescribiría la textura persistida con un lienzo casi en blanco).
- Suite completa (frontend + backend) en verde.

## Hecho
Implementado:
- `backend/src/main/java/.../project/texture/TextureService.java`: nuevo `download(UUID mobId): Optional<byte[]>` -- resuelve `storageKey` desde `DraftPersistenceService.getDraft(mobId).model().texture().storageKey()` (nueva dependencia inyectada), `Optional.empty()` si es null o el asset no existe. Un mob sin ningún draft deja propagar `DraftNotFoundException` (ya mapea a 404).
- `backend/src/main/java/.../project/api/MobTextureController.java`: nuevo `@GetMapping`, mismo patrón de respuesta que `MobThumbnailController#download`.
- `backend/src/test/java/.../project/api/MobTextureControllerTest.java`: +4 tests de integración (Testcontainers reales) -- descarga exitosa (bytes exactos), draft sin textura aún (404), mob sin ningún draft (404, `DRAFT_NOT_FOUND`), mob inexistente (404, `MOB_NOT_FOUND`).
- `frontend/src/api/textureUploadApi.ts`: nuevo `downloadTexture(mobId)` -- `null` en 404 (nunca lanza para ESE caso), `ApiError` para cualquier otro fallo HTTP.
- `frontend/src/editor/texture/pngImportDecode.ts`: renombrado `decodePngFileToAtlasBuffer(file: File)` → `decodePngBytesToAtlasBuffer(bytes: Blob)` -- ahora se reutiliza tanto para importar un archivo elegido por el usuario como para decodificar la textura descargada del backend (un `Blob` de `fetch`, nunca un `File` real). Actualizados sus 4 call sites (`TextureImportPanel.vue` + 3 archivos de test).
- `frontend/src/editor/texture/TextureCanvas.vue`: `loadModelAtlas` ahora es async -- si `model.texture.storageKey` existe, descarga+decodifica antes de `loadAtlas`; nuevo estado `atlasLoadError` (banner visible `role="alert"` + bloquea "Guardar") si la descarga/decodificación falla.
- `frontend/src/editor/texture/__tests__/TextureCanvas.spec.ts`: +3 tests dedicados (carga real con píxeles decodificados, nunca llama a `downloadTexture` sin `storageKey`, error visible + Guardar bloqueado ante un fallo).
- `frontend/src/api/__tests__/textureUploadApi.spec.ts`: +3 tests para `downloadTexture`.

**TDD real**: confirmado que los tests nuevos fallan contra el código sin este fix, tanto en backend (`git stash`, 4 `AssertionError`) como en frontend (`git stash`, 2 `AssertionError` -- el tercero, "nunca llama sin storageKey", ya pasaba antes por ser una aserción negativa) antes de aplicar la corrección.

**Tests**: backend 427/427 (+4 desde 065), frontend 577/577 (+6 desde el estado previo a esta sesión), sin regresiones. `vue-tsc -b` y `npm run lint` sin hallazgos.

**Verificación en vivo pendiente**: repetir el ciclo completo (generar con IA -> aplicar -> recargar la página -> confirmar que la textura sigue ahí) contra `studio-dev` una vez mergeado y desplegado.

**Mejora continua**: mismo patrón de fondo que 059 (un campo/endpoint que "debería estar ahí" pero nunca se construyó porque nadie llegó a necesitarlo de verdad hasta este punto de la cadena) -- la asimetría upload-sin-download en `TextureService` es exactamente análoga a la de `MobThumbnailController` (que sí tuvo ambos desde el ticket 023) y hubiera sido detectable antes con una revisión explícita de "¿todo lo que se sube tiene también una forma de bajarse?" al cerrar el ticket 045.
