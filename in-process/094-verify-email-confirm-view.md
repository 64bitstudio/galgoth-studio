# 094 — Falta la vista `/verify-email/confirm` (confirmación de correo al registrarse)

## Objetivo
Ticket retroactivo. Hallazgo real durante la verificación en vivo del ticket 093: al registrar una cuenta de prueba real, el correo "confirma tu correo" que auth-core-mc manda apunta a `{origin}/verify-email/confirm?token=...` (mismo mecanismo del ticket 056 de auth-core-mc que `/change-email/confirm` y `/password-reset/confirm` ya usan), pero galgoth-studio nunca implementó esa ruta ni esa vista — un usuario nuevo real no puede confirmar su correo desde la UI hoy, solo ve una página en blanco. Se confirmó el correo de la cuenta de prueba llamando directamente al endpoint real (`POST /api/v1/verify-email/confirm`) para no bloquear la verificación del 093.

## Criterios de aceptación (TDD)
- Ruta pública `/verify-email/confirm` (sin `meta.requiresAuth` — el usuario todavía no puede loguearse hasta confirmar).
- Vista `VerifyEmailConfirmView.vue` (mismo patrón exacto que `EmailChangeConfirmView.vue`/`ResetPasswordView.vue`): sin `token` en la query → "link inválido"; con token → llama a `POST /api/v1/verify-email/confirm` y muestra éxito/error reales.
- Suite de tests en verde.
- Verificado en vivo contra dev: registrar una cuenta real nueva, abrir el link real del correo recibido, confirmar desde la UI (sin llamar al endpoint a mano).

## Hecho
