# 024 — Asset-service: subida de imagen de referencia

**Milestone:** M4 · **Depende de:** 022 · **HUs:** HU-10

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Épica C). Endpoint y UI para subir la imagen de concept art de referencia, asociada a un mob (`reference_images`), como primer paso del wizard de generación IA.

## Criterios de aceptación (TDD)
- Dado el paso "Referencia" del wizard, cuando se sube una imagen válida, entonces se persiste en storage S3-compatible (MinIO) y queda referenciada al mob.
- Dado un archivo con formato o tamaño no soportado, cuando se intenta subir, entonces se rechaza con un mensaje claro (límites concretos definidos en este ticket, no en el documento de definición).
- Dado una imagen subida, cuando se consulta el mob, entonces la referencia incluye ancho/alto/content-type persistidos.

## Hecho

**Decisión de alcance explícita, sin re-preguntar**: el ticket 027 (Wizard 4 pasos UI) depende directamente de este y su propio AC dice literalmente "Dado el paso 'Referencia' con una imagen subida (**vía 024**)" -- confirma que la UI real del wizard es responsabilidad de 027, no de este ticket. Este ticket es **backend-only**, mismo patrón ya establecido por el ticket 020 (draft persistence backend-only, UI llegó en 021).

**Límites concretos de formato/tamaño**, dejados abiertos a propósito por el documento de definición para resolverse aquí -- VoBo explícito del Product Owner vía `AskUserQuestion`: solo `image/png`/`image/jpeg`, máximo 10MB por archivo (cubre holgadamente la muestra real del build pack, `carcomido_reference.png`, ~3MB).

### Implementado
- `backend/.../project/persistence/{ReferenceImageEntity,ReferenceImageRepository}`: mapea `reference_images` (ya existía desde el ticket 003) -- append-only, inmutable, mismo criterio que `MobRevisionEntity`.
- `backend/.../project/reference/{ReferenceImageService,ReferenceImageSummary,StoredReferenceImage,InvalidReferenceImageException}`: valida mob existente, content-type (normalizado a `tipo/subtipo`, whitelist PNG/JPEG), tamaño (≤10MB), y que los bytes decodifiquen como una imagen real (`ImageIO.read`) -- `width`/`height` SIEMPRE se derivan de los bytes reales, nunca de un valor del cliente (mismo criterio de autoridad server-side que `baseType` en `MobService`). Reutiliza `AssetStorageService` (023) sin cambios -- confirma que quedó genuinamente genérico.
- `backend/.../project/api/MobReferenceImageController.java`: `POST`/`GET /api/mobs/{mobId}/references`, `GET /api/mobs/{mobId}/references/{id}` -- mismo estilo mob-scoped y de body crudo (sin multipart) que `MobThumbnailController`.
- `ApiExceptionHandler` extendido con `INVALID_REFERENCE_IMAGE` (400).
- `docs/API.md`/`docs/ARQUITECTURA.md` actualizados. `postman/galgoth-studio/` extendido con la carpeta "Imágenes de referencia (ticket 024)" (3 requests).

### Hallazgo real, corregido en este mismo ticket
Comparar el header `Content-Type` recibido por igualdad de string exacta contra la whitelist (`"image/png"`) **rechaza imágenes válidas**: confirmado real con el propio `MockHttpServletRequestBuilder` de Spring Test, que agrega `;charset=UTF-8` a CUALQUIER content-type (no solo texto) -- las primeras 4 pruebas de subida fallaron con 400 antes de identificar la causa. Corregido normalizando siempre a `tipo/subtipo` (`MediaType.parseMediaType(...)`) antes de comparar, descartando cualquier parámetro adicional. Cubierto por una prueba dedicada que documenta el hallazgo inline.

### Tests
`MobReferenceImageControllerTest` (10, Testcontainers Postgres+MinIO reales + MockMvc): subida PNG/JPEG válida con ancho/alto/content-type reales (AC #1/#3), round-trip de bytes idéntico en la descarga, listado en orden de subida, rechazo de formato no soportado / tamaño excedido / bytes no decodificables (AC #2, los 3 casos), 404 en mob inexistente (subir/listar) y en referencia inexistente. **121 tests backend, 0 fallos** (111 + 10 nuevos).

### Verificación en vivo (backend + Postgres + MinIO reales vía `docker compose` + `./gradlew bootRun`, curl directo -- sin frontend en este ticket)
Subida real de `carcomido_reference.png` (3,093,533 bytes, la muestra real del build pack) contra un mob real: `201 Created` con `width:1254, height:1254, contentType:"image/png"` -- decodificado correctamente de una imagen real, no de un fixture sintético. Descarga posterior byte-a-byte IDÉNTICA al archivo original (`cmp` sin diferencias). Confirmado en Postgres: `storage_key` guarda la key interna de MinIO (`mobs/{mobId}/references/{id}.png`), distinta del `url` servible que devuelve la API. Rechazos verificados en vivo: `image/gif` → 400 `INVALID_REFERENCE_IMAGE`; archivo de 10,485,761 bytes (10MB+1) → 400 con el tamaño exacto en el mensaje; mob inexistente → 404 `MOB_NOT_FOUND`.

### AC verificados
- ✅ Imagen válida se persiste en MinIO y queda referenciada al mob (verificado en vivo con un archivo real de 3MB, no solo un fixture de test).
- ✅ Formato o tamaño no soportado se rechaza con mensaje claro (límites de PNG/JPEG/10MB definidos en este ticket con VoBo del PO).
- ✅ La referencia incluye ancho/alto/content-type persistidos, derivados siempre de los bytes reales.
