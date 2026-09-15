# 093 — Pantalla "Usuario" (frontend)

## Objetivo
Ver `docs/definiciones/perfil-de-usuario.md` (VoBo de Marco recibido) —
cierre de todas las HUs del lado del frontend. El ítem "Usuario" del
sidebar es hoy un placeholder deliberado (documentado en el Javadoc de
`GSidebar.vue`: "sin el tratamiento de tarjeta de perfil... eso depende
de datos reales de usuario que no existen todavía"). Con auth-core-mc
(`060`-`064`) y galgoth-studio (`091`-`092`) ya construidos, esos datos
ya existen.

**Depende de:** `060`, `061`, `062`, `063`, `064` (auth-core-mc), `091`,
`092` (galgoth-studio).

## Alcance
- **Sí incluye:**
  - Nueva ruta `/usuario` (o `/profile`) con la pantalla completa del
    mockup: cabecera (avatar, nombre, correo, "Miembro desde", badge),
    Información personal, Cuentas conectadas, Seguridad, Preferencias,
    Zona de peligro.
  - Cada sección conectada a su endpoint real (060-064, 091-092) — sin
    datos de relleno.
  - "Verificación en dos pasos" y el toggle "Tema oscuro" se muestran
    deshabilitados con indicación clara ("Próximamente" / "Sin tema
    claro todavía") — nunca fingiendo que funcionan.
  - Confirmación explícita (modal, no solo un clic) antes de "Eliminar
    cuenta" y antes de "Cerrar sesión en todos los dispositivos".
  - `GSidebar.vue`: el ítem "Usuario" navega de verdad a esta pantalla
    (hoy es un ítem de nav sin ruta detrás, mismo gap documentado que
    "Explorar" tenía antes del ticket `088`).
- **No incluye:** nada que no esté ya cubierto por los tickets de
  backend listados.

## Criterios de aceptación (TDD)
- Cada sección de la pantalla refleja datos reales del usuario
  autenticado (no mocks).
- Editar información personal, cambiar contraseña, revocar una sesión,
  vincular una cuenta social, guardar preferencias y eliminar la cuenta
  funcionan de punta a punta contra los endpoints reales.
- Suite de tests del frontend en verde (Vitest).
- Verificación en vivo contra DEV: recorrido completo de la pantalla con
  una cuenta de prueba real, incluyendo vincular una cuenta de Google
  real y, al final, eliminar esa cuenta de prueba.

## Hecho
Nueva pantalla `/usuario` (`meta.requiresAuth`, a diferencia de `/explore` que es deliberadamente pública) con las 6 secciones del mockup, cada una contra su endpoint real:

- **Cabecera**: avatar con click-to-upload (fallback de inicial de texto, mismo patrón que `ExploreProjectCard.vue`), nombre, badge "Creador", correo, "Miembro desde" (usa `createdAt`, ticket 065 de auth-core-mc).
- **Información personal**: nombre/apellidos/país/nombre de usuario editables (`PATCH /api/v1/account/profile`); correo con el flujo de confirmación de 2 pasos ya existente (`ChangeEmailModal` + nueva `EmailChangeConfirmView.vue` en la ruta pública fija `/change-email/confirm`).
- **Cuentas conectadas**: Google/Facebook, "Conectar" dispara el mismo mecanismo OAuth2 que el login social (`link-provider/{provider}`, ticket 063 de auth-core-mc).
- **Seguridad**: cambiar/establecer contraseña según `hasPassword`, 2FA marcado "Próximamente", sesiones activas con revocación individual (sin botón en la sesión actual).
- **Preferencias**: 3 toggles reales (galgoth-studio, ticket 091) persistidos en una tabla propia de producto; "Tema oscuro" deshabilitado (sin tema claro implementado).
- **Zona de peligro**: "Cerrar sesión en todos los dispositivos" y "Eliminar cuenta", ambas con `ConfirmDialog`/`DeleteAccountDialog` antes de ejecutar (nunca un solo clic) — usa `AccountDeletionController` de auth-core-mc, ticket 064.

Clientes API nuevos: `accountApi.ts` (Bearer contra auth-core-mc) y `productProfileApi.ts` (contra el backend propio de galgoth-studio). `GSidebar.vue` y los 8 lugares con `handleSidebarSelect` ahora enrutan "Usuario" a `/usuario` — el gap ya no existe.

849 tests en verde (50 nuevos en 7 specs nuevos/modificados), `vue-tsc -b` y `eslint --max-warnings 0` limpios. PR #133, CI de Jenkins verde, self-merge.

**Hallazgos reales durante la verificación en vivo** (no parte del alcance original de este ticket, pero descubiertos por ser el primer caller real, cross-origin, de varios mecanismos de auth-core-mc que hasta ahora solo se habían probado same-origin o vía MockMvc) — cada uno resuelto como su propio ticket, ver `auth-core-mc/docs/ARQUITECTURA.md` y `platform/docs/ARQUITECTURA.md` para el detalle completo:
- auth-core-mc#065: `UserResponse` nunca exponía `createdAt`.
- auth-core-mc#066: CORS no permitía el header `X-Current-Refresh-Token` que `listSessions` necesita.
- auth-core-mc#067: `link-provider` devolvía una URL relativa + la cookie de sesión de vínculo se perdía cross-origin (CORS credentials + `SameSite=None`, VoBo explícito de Marco).
- auth-core-mc#068 + platform#008: Traefik/Spring construían el `redirect_uri` de OAuth2 con esquema `http://` en vez de `https://` detrás del proxy, y Google lo rechazaba con `redirect_uri_mismatch` (VoBo explícito de Marco para tocar infra compartida).

**Verificación en vivo contra dev, recorrido completo con una cuenta de prueba real** (`ticket093qa`, registrada y confirmada vía el endpoint real de verificación de correo): editar información personal (nombre/país/username) — guardado y persistido; cambiar contraseña — verificado con re-login exitoso usando la nueva; sesiones activas — mostrada correctamente tras el fix de CORS; **vincular una cuenta de Google real de punta a punta**, incluyendo el consentimiento real de Google (con VoBo explícito de Marco sobre qué cuenta usar) — "Cuenta de Google vinculada correctamente", confirmado sin duplicar el usuario (`connected-providers` refleja `GOOGLE: linked=true` en el mismo `userId`); alternar preferencias — persistido tras recargar; "Cerrar sesión en todos los dispositivos" — cerró la sesión y redirigió a `/login`; **eliminar la cuenta de prueba al final** — confirmado con un segundo intento de login que responde "This account has been deleted".

**Hallazgo real adicional, no resuelto en este ticket** (registro de una cuenta nueva): el link de "confirma tu correo" que se manda por email apunta a `/verify-email/confirm?token=...`, pero galgoth-studio nunca implementó esa ruta/vista (a diferencia de `/change-email/confirm` y `/password-reset/confirm`, que sí existen) — un usuario nuevo real no puede confirmar su correo desde la UI hoy. Se confirmó el correo de la cuenta de prueba llamando directamente al endpoint real (`POST /api/v1/verify-email/confirm`) para no bloquear esta verificación. Reportado como hallazgo separado para un ticket de seguimiento (fuera del alcance de "Usuario").
