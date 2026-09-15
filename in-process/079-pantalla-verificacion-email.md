# 079 — Pantalla de verificación de email

> Renumerado de 074 → 079: colisionó con el ticket real 074 ("rediseño de wizard crear con IA", ya en `done/`) — ver `fix/renumerar-tickets-auth-072-075`.

## Objetivo
auth-core-mc ya envía un correo real de verificación al registrarse (ticket 078, pantalla de registro), pero galgoth-studio no tiene ninguna pantalla para confirmarlo ni para reenviarlo si expira o no llega. Decisión explícita de Marco (`PROP-GS-AUTH-01`, sección 08): pantalla completa en v1, no un mensaje mínimo.

**Depende de:** 078 (el registro que dispara el correo).

## Alcance
**Incluye:**
- Pantalla que confirma el token del link de verificación (`GET /api/v1/verify-email/**` de auth-core-mc).
- Botón de reenvío del correo si el link expiró o no llegó.
- Estado visible del usuario (`emailVerified: true/false`) reflejado en la UI tras confirmar.

**No incluye:**
- Bloquear el uso de galgoth-studio mientras el email no esté verificado — eso es una decisión de producto aparte, no asumida aquí.

## Criterios de aceptación (TDD)
- Un link de verificación válido confirma el correo y lo refleja en la UI.
- Un link expirado/inválido muestra un error claro con opción de reenviar.
- El reenvío dispara un correo real nuevo (verificado en vivo, no solo que la llamada responda 200).

## Hecho
**Ya cubierto por el ticket 094** (descubierto DESPUÉS de escribir ese ticket -- 079 ya existía en `pending/` y no se consultó antes; se retoma acá en vez de duplicar): la pantalla que confirma el token (`VerifyEmailConfirmView.vue`, ruta `/verify-email/confirm`) y el hallazgo de que `/register` nunca pedía el correo por sí solo.

**Nuevo en este ticket:**
- `UserView.vue`, fila de "Correo": badge `Verificado`/`Sin verificar` (mismos tokens de color que `GStatusPill`, accent/warning + su variante `-soft`) reflejando `authProfile.emailVerified` en vivo.
- Botón "Reenviar correo de verificación" junto al badge, visible solo si hay correo y sigue sin verificar -- llama a `authApi.requestEmailVerification(userId)` (ya agregada en el 094).

**Decisión real sobre el AC "un link expirado/inválido muestra un error claro con opción de reenviar"**: el reenvío exige un `userId` real (`POST /api/v1/verify-email/request`, mismo criterio de "confianza temporal" que auth-core-mc ya documenta para este endpoint) -- inalcanzable desde `VerifyEmailConfirmView.vue` en su estado de error, porque ahí el visitante no tiene sesión (solo tenía un token ya muerto). En vez de inventar un mecanismo nuevo de reenvío-sin-sesión (fuera de alcance, cambiaría el modelo de confianza del endpoint), la pantalla de error dirige a iniciar sesión -- decisión ya explícita del ticket ("no bloquea el uso mientras no esté verificado"), así que el usuario SÍ puede entrar y reenviar desde "Usuario" con la fila nueva de arriba.

Tests: 857/857 en verde (+3 nuevos en `UserView.spec.ts`). `vue-tsc -b` y `eslint --max-warnings 0` limpios.

CI de Jenkins verde (ver PR). [Pendiente: verificación en vivo contra dev antes de cerrar -- ver sección de cierre.]
