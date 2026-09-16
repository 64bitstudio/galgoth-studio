# 106 — La URL pública del avatar no debe exponer el `userId` real

## Objetivo
Encontrado al cerrar auth-core-mc#071 (hallazgo real de seguridad): la
cadena de secuestro de cuenta que ese fix cerró usaba
`ProjectSummary.avatarUrl` (`/api/account/avatar/{userId}`, público, sin
sesión) como la forma de obtener el UUID real de identidad de la
víctima -- el mismo `sub` que auth-core-mc usa en sus JWTs. Ese lado de
la cadena ya está cerrado en auth-core-mc (los endpoints que confiaban
ese `userId` ahora exigen JWT real), pero el leak en sí seguía existiendo
acá: cualquier visitante de Explorar, sin sesión, podía leer el UUID de
identidad real de cualquier dueño de un proyecto público con avatar.

Marco pidió corregir esto como parte de la limpieza de la vulnerabilidad,
en el orden que mejor conviniera.

## Criterios de aceptación (TDD)
- La URL pública del avatar (`GET /api/account/avatar/{id}`) usa un
  identificador de servicio distinto del `userId` real, sin relación
  reconstruible con él.
- Ese identificador es estable entre resubidas del mismo avatar (no
  cambia con cada `POST`, solo se genera una vez por perfil).
- Ninguna fila existente pierde su avatar ni cambia de contenido -- la
  migración es puramente aditiva.
- El `userId` real deja de ser una ruta válida en absoluto para esta
  descarga (ni siquiera como fallback).

## Hecho
Implementado, tests reales en verde. Mergeado vía PR #150.

- Migración `V8__user_profile_public_avatar_id.sql`: columna
  `public_avatar_id UUID NOT NULL DEFAULT gen_random_uuid()` + índice
  único -- puramente aditiva, rellena filas existentes y nuevas por
  igual.
- `UserProfileEntity`: nuevo campo `publicAvatarId`, generado en Java
  (`UUID.randomUUID()`) en el constructor -- mismo criterio ya usado ahí
  para las 3 preferencias, no confía en el DEFAULT de la columna para
  las filas nuevas.
- `UserProfileRepository.findByPublicAvatarId(UUID)` nuevo.
- `UserProfileService`: `uploadAvatar`/`downloadAvatar`/`avatarUrlIfPresent`/`toResponse`
  ahora resuelven y sirven por `publicAvatarId`, nunca por `userId`. La
  subida (`POST`, autenticada) sigue usando el `userId` real
  internamente -- ese id nunca estuvo expuesto ahí, no había nada que
  cambiar en ese sentido.
- `AccountAvatarController`: `GET /{userId}` → `GET /{publicAvatarId}`.
- Docstrings/`docs/API.md`/`docs/BASE_DE_DATOS.md` actualizados con el
  hallazgo y la razón del cambio.
- Tests: `AccountAvatarControllerTest` reescrito para capturar la URL
  real de la respuesta (nunca asumir el formato), con un test nuevo que
  demuestra explícitamente que el `userId` real ya NO es una ruta
  válida. `ExploreProjectControllerTest`/`ProjectControllerTest`
  actualizados (sus inserts de SQL crudo ahora fijan `public_avatar_id`
  explícito para poder aseverar sobre él). `SchemaMigrationReversibilityTest`
  actualizado (7 → 8 migraciones).
- Suite completa: 488 tests, 0 fallos, 0 errores. Frontend sin cambios
  de lógica (`avatarUrl`/`ProductProfile.avatarUrl` ya se tratan como
  ruta opaca de principio a fin) -- solo 2 comentarios corregidos. 876
  tests de frontend, build y lint verdes.
