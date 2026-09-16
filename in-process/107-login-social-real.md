# 107 — Login social real (Google/Facebook) desde Login/Registro

## Objetivo
Cerraba el hueco que la propia UI venía marcando como "Próximamente"
desde el ticket 081/082: los botones de Google/Facebook en `/login` y
`/register` estaban ahí, fieles al mockup, pero `disabled` -- el login
social nunca se cableó en galgoth-studio. Todo el backend ya estaba
listo (`hosts_own_login_ui`, credenciales reales de Google/Facebook para
el tenant en los 3 ambientes, `/api/v1/oauth2/social-exchange`) salvo el
último tramo: auth-core-mc#072 (mergeado) agrega el endpoint público que
resuelve la URL real de redirect sin que este frontend tenga que conocer
el UUID interno del `IdentityClient`.

## Criterios de aceptación (TDD)
- Los botones de Google/Facebook en `/login` y `/register` navegan el
  navegador completo a la URL real que devuelve auth-core-mc (nunca una
  URL armada a mano).
- `/auth/callback` (ruta fija, ya registrada como `redirect_uri` en los
  3 ambientes) canjea `?code=` por una sesión real; `?error=social_login_cancelled`
  muestra un mensaje explícito sin llamar al backend.
- Un `?redirect=` pendiente en `/login` (mismo mecanismo que ya usa el
  login con password, ticket 087) sobrevive el viaje de ida y vuelta al
  proveedor -- se guarda en `sessionStorage` antes de navegar fuera de
  la SPA, se consume una sola vez al volver.
- 2FA activo muestra el mismo mensaje explícito que el login con
  password (galgoth-studio no soporta ese flujo todavía) -- nunca falla
  en silencio.
- Suite completa en verde.

## Hecho
Implementado, tests reales en verde.

- `authApi.ts`: `socialLoginUrl(provider)` (GET, sin sesión) y
  `exchangeSocialCode(code)` (POST, mismo shape `LoginSuccess |
  TwoFactorRequired` que `login()`) nuevos.
- `sessionStore.ts`: `loginWithSocialCode(code)`, mismo contrato que
  `login()`.
- `LoginView.vue`/`RegisterView.vue`: botones de Google/Facebook
  habilitados, `connectSocial(provider)` pide la URL real y navega el
  navegador completo (mismo criterio que "vincular cuenta" en Mi
  Perfil -- nunca XHR). `LoginView.vue` además guarda `?redirect=` en
  `sessionStorage` antes de navegar, si viene uno interno válido.
- `AuthCallbackView.vue` nuevo, ruta fija `/auth/callback`: canjea
  `?code=`, maneja `?error=social_login_cancelled` explícitamente, 2FA
  con el mismo mensaje que `LoginView.vue`, y retoma el `?redirect=`
  guardado (o `/` si no había ninguno).
- Tests: `authApi.spec.ts`/`sessionStore.spec.ts` (2 funciones nuevas),
  `LoginView.spec.ts`/`RegisterView.spec.ts` (navegación real del
  navegador, mismo patrón que el test ya existente de "Conectar" en Mi
  Perfil), `AuthCallbackView.spec.ts` nuevo (6 casos: éxito, éxito con
  redirect pendiente, cancelado, código inválido, 2FA activo, ni code ni
  error).
- 893 tests, 0 fallos. Build y lint verdes.

**Verificado en vivo, parcial**: confirmé por navegador que el deploy a
DEV carga sin errores de consola. El consentimiento real de Google/
Facebook (clic en el botón, pantalla del proveedor, vuelta con `?code=`
real) requiere una cuenta real de Google/Facebook para probarse de
punta a punta -- pendiente de que Marco lo confirme en DEV.
