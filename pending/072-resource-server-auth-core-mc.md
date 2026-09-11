# 072 — Proteger la API propia como OAuth2 Resource Server de auth-core-mc

## Objetivo
Ninguna API de galgoth-studio está protegida hoy — no existe usuario, sesión ni validación de token. Este ticket hace que el backend valide los `accessToken` reales que auth-core-mc emite para el cliente `galgoth-studio` (ya dado de alta en dev/qa/prod, ver `auth-core-mc#052`). Es la pieza central de la propuesta `PROP-GS-AUTH-01` (blueprint, Fig. 04/05) — sin esta, ninguna otra pantalla de login "protege" nada de verdad.

**Depende de:** nada de este repo (es la primera pieza). Bloquea 073 (necesita saber a quién pertenece cada request).

## Alcance
**Incluye:**
- `spring.security.oauth2.resourceserver.jwt.issuer-uri` configurable por ambiente (`auth-dev`/`auth-qa`/`auth.64bitstudio.com`).
- Validador de audiencia explícito: **rechazar cualquier token cuyo `aud` no sea `galgoth-studio`** — sin esto, un token real de cualquier otro cliente futuro de auth-core-mc pasaría igual (blueprint, Fig. 05, el hallazgo más importante del documento).
- Extraer el `sub` (UUID del usuario en auth-core-mc) como identidad del request para las APIs propias.
- Manejo explícito de `401` cuando el token está vencido/inválido — el frontend (ticket 073) depende de que este código de estado sea confiable para disparar su lógica de refresh.

**No incluye:**
- Nada de las pantallas de login/registro (ticket 073).
- Configurar CORS del lado de auth-core-mc (ya es responsabilidad de ese repo, ticket `auth-core-mc#054`) — este ticket asume que ya funciona.

## Criterios de aceptación (TDD)
- Un request sin `Authorization` a una ruta protegida responde `401`.
- Un token real, válido, con `aud=galgoth-studio` → `200`, y el handler puede leer el `sub` como id del usuario.
- Un token real y válidamente firmado pero con `aud` distinto (simulando otro cliente) → `401` — este es el test que demuestra que la frontera de tenant funciona, no solo que "hay un JWT".
- Un token expirado → `401` (no `500` ni excepción sin manejar).
- Verificado en vivo contra `studio-dev`: login real contra auth-core-mc (dev), llamar una ruta protegida con ese token real, confirmar `200`; repetir con el token de otro cliente/tenant y confirmar `401`.

## Hecho
