# 096 — Actualizar `requestEmailChange` para el fix de seguridad de auth-core-mc#071

## Objetivo
Ticket retroactivo. auth-core-mc#071 (hallazgo real de seguridad,
2026-09-15) cerró un secuestro de cuenta explotable: `/api/v1/change-email/request`
confiaba un `userId` mandado tal cual en el body, sin autenticación
real. La cadena real usaba `ProjectSummary.avatarUrl` de este mismo repo
(expone el UUID real del dueño de un proyecto público, sin sesión) como
la forma de obtener el `userId` de una víctima. El endpoint ahora exige
Bearer real y ya no acepta `userId` — este repo, como único consumidor
real de ese endpoint, necesitaba actualizarse en lockstep o "Cambiar
correo" en Mi Perfil se hubiera roto en cuanto el fix de auth-core-mc
llegara a DEV.

## Criterios de aceptación (TDD)
- `requestEmailChange` ya no manda `userId` en el body — vive en
  `accountApi.ts` (Bearer real, mismo patrón que el resto de ese
  archivo) en vez de `authApi.ts` (sin sesión).
- `requestEmailVerification` no cambia — auth-core-mc lo dejó
  deliberadamente sin migrar (mismo repo, motivo distinto: solo reenvía
  a la dirección ya registrada, y este repo lo usa sin sesión justo
  después de `/register`).
- Suite completa en verde tras el cambio.

## Hecho
Implementado y mergeado en PR #146 (`fix/auth-core-mc-071-bearer-email-endpoints`).

- `requestEmailChange` se movió de `authApi.ts` a `accountApi.ts`, perdió
  su parámetro `userId`.
- `accountApi.ts`'s `request()`: `202` (además de `204`) ahora se trata
  como "sin body" — `change-email/request` responde `202` sin JSON,
  `.json()` reventaba contra un body vacío real (hallazgo propio,
  encontrado al escribir el test nuevo, corregido antes de mergear).
- `UserView.vue` actualizado al nuevo call site.
- Test nuevo en `accountApi.spec.ts` (Bearer real adjunto, sin `userId`
  en el body).
- 876 tests en verde, build y lint verdes.
- El primer intento de deploy a DEV (commit `7564c09`) falló por un
  crash del daemon de Gradle en el runner de Jenkins — infraestructura,
  no código (`Gradle build daemon disappeared unexpectedly`). La
  redelivery del webhook de GitHub no lo reintentó (Jenkins no re-builda
  una revisión ya vista) — el deploy real se reintenta con el commit de
  este mismo ticket de cierre.

Referencia: `done/071-fix-trust-boundary-userid-2fa-emails.md` de
auth-core-mc para el detalle completo del hallazgo original.
