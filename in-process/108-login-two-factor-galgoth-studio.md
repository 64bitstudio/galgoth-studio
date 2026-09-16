# 108 — Completar login con 2FA activo (galgoth-studio)

## Objetivo
El backend (auth-core-mc, tickets `045`/`046`) ya soporta el flujo
completo -- `POST /api/v1/login` y `POST /api/v1/oauth2/social-exchange`
responden `202 { twoFactorRequired: true, pendingToken, method }` para
una cuenta con 2FA activo, y `POST /api/v1/login/2fa-verify` /
`POST /api/v1/login/2fa-resend` completan el login una vez verificado el
código -- pero galgoth-studio nunca lo consumió: tanto `LoginView.vue`
(login con contraseña) como `AuthCallbackView.vue` (login social, ticket
`107`) descartan el `pendingToken`/`method` y solo muestran un mensaje
de error fijo ("todavía no soporta ese flujo"), dejando varada a
cualquier cuenta real con 2FA activo (activado hoy solo desde la UI
propia de auth-core-mc, `cuenta.html`).

Alcance decidido con Marco: solo el tramo de login (esta pantalla de
código), NO se agrega forma de activar 2FA desde Mi Perfil en esta
pasada -- eso queda para un ticket futuro si se pide.

## Criterios de aceptación (TDD)
- `sessionStore.login`/`loginWithSocialCode` exponen el `pendingToken`
  y el `method` reales en vez de descartarlos cuando el resultado es
  `two-factor-required`.
- `LoginView.vue` y `AuthCallbackView.vue` muestran un paso real de
  "ingresa tu código" (reutilizando un componente compartido, no
  duplicado) en vez del mensaje de error fijo actual.
- Ese paso llama a `POST /api/v1/login/2fa-verify` con el `pendingToken`
  + código ingresado; éxito completa el login exactamente igual que un
  login normal (persiste sesión, respeta el `?redirect=`/
  `sessionStorage` pendiente de cada flujo).
- Un botón "Reenviar código" llama a `POST /api/v1/login/2fa-resend`
  cuando el método es `OTP_EMAIL`/`OTP_SMS`; no se muestra para `TOTP`
  (nada que reenviar -- el código vive en la app autenticadora).
- Código incorrecto o `pendingToken` inválido/expirado muestra el error
  real que devuelve auth-core-mc (`400 invalid_token` / `429
  too_many_attempts`), nunca en silencio.
- Suite completa en verde (build + lint + tests).

## Hecho
Implementado, tests reales en verde.

- `authApi.ts`: `verifyTwoFactorLogin(pendingToken, code)` (POST
  `/api/v1/login/2fa-verify`, con `X-Client-Id`, devuelve `LoginSuccess`
  directo -- este endpoint nunca vuelve a responder `twoFactorRequired`)
  y `resendTwoFactorCode(pendingToken)` (POST `/api/v1/login/2fa-resend`)
  nuevos.
- `sessionStore.ts`: `login`/`loginWithSocialCode` ahora devuelven
  `LoginOutcome` (`{ status: 'ok' }` o `{ status: 'two-factor-required',
  pendingToken, method }`) en vez de descartar el `pendingToken`/`method`
  reales. `completeTwoFactorLogin(pendingToken, code)` nuevo -- mismo
  criterio que `login`, guarda la sesión en éxito o lanza (código
  incorrecto/`pendingToken` inválido).
- `TwoFactorChallenge.vue` nuevo, compartido entre `LoginView.vue` y
  `AuthCallbackView.vue` (mismo `pendingToken`/contrato para login con
  contraseña y login social): formulario de código, "Reenviar código"
  visible solo para `OTP_EMAIL`/`OTP_SMS` (no para `TOTP`, nada que
  reenviar), "Volver" para regresar al formulario/login.
- `LoginView.vue`/`AuthCallbackView.vue`: el caso `two-factor-required`
  ahora muestra `TwoFactorChallenge.vue` en vez del mensaje de error
  fijo -- éxito completa el login exactamente igual que uno normal
  (respeta `?redirect=`/`sessionStorage` pendiente de cada flujo).
- Tests: `authApi.spec.ts` (4 casos nuevos), `sessionStore.spec.ts`
  (2 casos nuevos + 2 actualizados para el nuevo `LoginOutcome`),
  `LoginView.spec.ts`/`AuthCallbackView.spec.ts` (casos de 2FA
  actualizados + completar/error/volver), `TwoFactorChallenge.spec.ts`
  nuevo (7 casos: éxito, error, cancelar, TOTP sin botón de reenvío,
  OTP_EMAIL/OTP_SMS con reenvío, reenvío rechazado).
- 911 tests (+18), 0 fallos. Build y lint verdes.

No se agregó forma de ACTIVAR 2FA desde Mi Perfil en esta pasada
(alcance decidido explícitamente con Marco) -- sigue "Próximamente" ahí.
