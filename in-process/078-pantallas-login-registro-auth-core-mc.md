# 078 — Pantallas de login y registro contra auth-core-mc

> Renumerado de 073 → 078: colisionó con el ticket real 073 ("rediseño de detalle de proyecto", ya en `done/`) — ver `fix/renumerar-tickets-auth-072-075`.

## Objetivo
galgoth-studio no tiene hoy ninguna forma de que un usuario se registre o inicie sesión. Construye las 2 pantallas propuestas en `PROP-GS-AUTH-01` (blueprint, Fig. 02/03): consumen directo la API JSON de auth-core-mc (`POST /api/v1/register`, `POST /api/v1/login`, header `X-Client-Id: galgoth-studio`) — ya verificado en vivo en DEV antes de escribir la propuesta.

**Depende de:** `auth-core-mc#054` (CORS) desplegado en el ambiente donde se pruebe — sin eso, el navegador bloquea la llamada. No depende de 077, pero sin 077 tener sesión no protege nada todavía.

## Alcance
**Incluye:**
- Pantalla de registro: nombre, apellidos, email, password → `POST /api/v1/register`; tras éxito, mensaje "revisa tu correo" (no entrega sesión).
- Pantalla de login: identifier + password → `POST /api/v1/login`; guarda `accessToken`/`refreshToken` de la respuesta.
- Interceptor HTTP: adjunta `Authorization: Bearer` a las llamadas a la API propia de galgoth-studio; ante `401`, intenta `POST /api/v1/refresh-token` una vez y reintenta la request original (blueprint, Fig. 06 — cubre el caso de que auth-core-mc se haya reiniciado y rotado su llave de firma).
- Explícitamente **no maneja** el caso `202` (2FA pendiente) — decisión ya tomada en `PROP-GS-AUTH-01` sección 08: se asume que ningún usuario de este tenant tiene 2FA activo en v1.
- El backend de auth-core-mc a apuntar es distinto por ambiente (`auth-dev`/`auth-qa`/`auth.64bitstudio.com`) — **igual que el hallazgo real del ticket 077 con `AUTH_CORE_MC_ISSUER`**, el frontend de galgoth-studio se construye UNA SOLA VEZ y esa misma imagen se promueve dev→qa→prod sin rebuild (ver `Jenkinsfile`, `VITE_API_BASE_URL=` vacío a propósito) — un `VITE_...` de build-time NO sirve aquí. Resolver el host de auth-core-mc en tiempo de ejecución en el navegador (a partir de `window.location.hostname`), no en build-time.

**No incluye:**
- Verificación de email (ticket 079) ni reset de password (ticket 080) — pantallas separadas.
- Login social — el mecanismo de redirect ya existe (`auth-core-mc#055`) y las credenciales de Google/Facebook de galgoth-studio ya están configuradas en los 3 ambientes, pero el botón en sí (UI) queda fuera de este ticket, es trabajo aparte.

## Criterios de aceptación (TDD)
- Registro real crea el usuario en auth-core-mc (visible después vía login) y no entrega tokens.
- Login real con credenciales válidas entrega sesión utilizable contra una ruta protegida por el ticket 077.
- Login con credenciales inválidas muestra un error claro, sin tokens guardados.
- Un `401` de la API propia dispara el refresh automático y reintenta — verificado forzando un token vencido, no solo por temporizador.
- Verificado en vivo contra `studio-dev`: registro + login reales de punta a punta, sesión reflejada en la UI.

## Hecho
- `src/auth/authCoreMcConfig.ts`: resuelve el host de auth-core-mc **en tiempo de ejecución** a partir de `window.location.hostname` (mapa fijo de los 3 dominios reales de galgoth-studio → los 3 de auth-core-mc), no vía `VITE_*` de build-time — hallazgo real: el frontend se construye UNA sola vez y esa imagen se promueve dev→qa→prod sin rebuild (`Jenkinsfile`, `VITE_API_BASE_URL=` vacío a propósito), así que un valor horneado en build-time sería incorrecto en 2 de los 3 ambientes.
- `src/auth/authApi.ts`: `register`/`login`/`refreshAccessToken` contra la API directa de auth-core-mc, con `X-Client-Id: galgoth-studio` (excepto `refresh`, que no lo exige).
- `src/auth/sessionStore.ts` (Pinia): persiste en `sessionStorage` (mismo mecanismo que la propia UI hospedada de auth-core-mc); `register` no crea sesión; `refresh` cierra la sesión si el refresh token también es inválido.
- `src/auth/authenticatedFetch.ts`: adjunta `Authorization: Bearer` a llamadas a la API propia; ante `401`, refresca UNA vez y reintenta (Fig. 06) — sin efecto observable hoy porque ninguna ruta propia exige autenticación todavía (ticket 077).
- `LoginView.vue`/`RegisterView.vue`: pantallas reales, rutas `/login`/`/register`. El caso `202` (2FA) se muestra como error explícito, no se maneja (decisión ya tomada).
- **38 tests nuevos** (6 archivos, 32 en `src/auth` + `authenticatedFetch.spec.ts`), todos con `fetch` real interceptado (`vi.stubGlobal`), sin red real. Suite completa del frontend: 749/749 en verde. `lint`/`vue-tsc -b`/`build` sin hallazgos.
- **Verificado con navegador real** (Vite dev local, `VITE_AUTH_CORE_MC_URL` apuntado a auth-core-mc DEV real): ambas pantallas renderizan correctamente contra los tokens de diseño; el submit de registro golpea la API real y, al no estar `localhost` en el allowlist de CORS de DEV (ticket auth-core-mc#054, allowlist exacta por diseño), el error de red se maneja con gracia (mensaje de fallback, sin crash) — confirma que el manejo de errores funciona ante una falla real, no simulada.
- **Pendiente**: verificación en vivo de punta a punta (registro + login reales, sesión reflejada) contra `studio-dev.galgoth.64bitstudio.com` una vez desplegado — ese origen sí está en el allowlist de CORS real. Se completa después del merge/deploy automático a DEV, antes de mover este ticket a `done/`.
