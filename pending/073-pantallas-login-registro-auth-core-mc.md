# 073 — Pantallas de login y registro contra auth-core-mc

## Objetivo
galgoth-studio no tiene hoy ninguna forma de que un usuario se registre o inicie sesión. Construye las 2 pantallas propuestas en `PROP-GS-AUTH-01` (blueprint, Fig. 02/03): consumen directo la API JSON de auth-core-mc (`POST /api/v1/register`, `POST /api/v1/login`, header `X-Client-Id: galgoth-studio`) — ya verificado en vivo en DEV antes de escribir la propuesta.

**Depende de:** `auth-core-mc#054` (CORS) desplegado en el ambiente donde se pruebe — sin eso, el navegador bloquea la llamada. No depende de 072, pero sin 072 tener sesión no protege nada todavía.

## Alcance
**Incluye:**
- Pantalla de registro: nombre, apellidos, email, password → `POST /api/v1/register`; tras éxito, mensaje "revisa tu correo" (no entrega sesión).
- Pantalla de login: identifier + password → `POST /api/v1/login`; guarda `accessToken`/`refreshToken` de la respuesta.
- Interceptor HTTP: adjunta `Authorization: Bearer` a las llamadas a la API propia de galgoth-studio; ante `401`, intenta `POST /api/v1/refresh-token` una vez y reintenta la request original (blueprint, Fig. 06 — cubre el caso de que auth-core-mc se haya reiniciado y rotado su llave de firma).
- Explícitamente **no maneja** el caso `202` (2FA pendiente) — decisión ya tomada en `PROP-GS-AUTH-01` sección 08: se asume que ningún usuario de este tenant tiene 2FA activo en v1.

**No incluye:**
- Verificación de email (ticket 074) ni reset de password (ticket 075) — pantallas separadas.
- Login social (depende de `auth-core-mc#055`, que además todavía no tiene credenciales de Google/Facebook configuradas para este tenant — ver blueprint 08.2).

## Criterios de aceptación (TDD)
- Registro real crea el usuario en auth-core-mc (visible después vía login) y no entrega tokens.
- Login real con credenciales válidas entrega sesión utilizable contra una ruta protegida por el ticket 072.
- Login con credenciales inválidas muestra un error claro, sin tokens guardados.
- Un `401` de la API propia dispara el refresh automático y reintenta — verificado forzando un token vencido, no solo por temporizador.
- Verificado en vivo contra `studio-dev`: registro + login reales de punta a punta, sesión reflejada en la UI.

## Hecho
