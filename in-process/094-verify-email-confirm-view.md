# 094 — Falta la vista `/verify-email/confirm` + `/register` nunca pedía el correo de verificación

## Objetivo
Ticket retroactivo. Dos hallazgos reales encadenados, ambos durante la verificación en vivo del ticket 093, en el mismo flujo de "confirma tu correo":

1. **Falta la vista.** El correo "confirma tu correo" que auth-core-mc manda apunta a `{origin}/verify-email/confirm?token=...` (mismo mecanismo del ticket 056 de auth-core-mc que `/change-email/confirm` y `/password-reset/confirm` ya usan), pero galgoth-studio nunca implementó esa ruta ni esa vista — un usuario nuevo real no podía confirmar su correo desde la UI, solo veía una página en blanco.
2. **`/register` nunca pedía el correo, para ningún usuario, nunca.** Investigando por qué una segunda cuenta de prueba real no recibía ningún correo (ni viejo ni nuevo), se confirmó con Redis (`token:email-verify:*`) que el registro no generaba ningún token — `RegistrationService.register` (auth-core-mc) nunca dispara el envío por sí solo, es responsabilidad del CALLER pedirlo explícitamente vía `POST /api/v1/verify-email/request`. El docstring original de `RegisterView.vue` (ticket 078) asumía lo contrario. Resultado real: **"Revisa tu correo" era una pantalla que mentía** — se mostraba igual aunque auth-core-mc nunca hubiera recibido la orden de mandar nada. Confirmado también que llamar `/verify-email/request` a mano SÍ genera un token real y el correo se puede confirmar de punta a punta desde la UI ya con la vista nueva.

## Criterios de aceptación (TDD)
- Ruta pública `/verify-email/confirm` (sin `meta.requiresAuth` — el usuario todavía no puede loguearse hasta confirmar).
- Vista `VerifyEmailConfirmView.vue` (mismo patrón exacto que `EmailChangeConfirmView.vue`/`ResetPasswordView.vue`): sin `token` en la query → "link inválido"; con token → llama a `POST /api/v1/verify-email/confirm` y muestra éxito/error reales.
- `RegisterView.vue`: tras un registro exitoso, pide el correo de verificación (`POST /api/v1/verify-email/request`) — si esa llamada falla, no bloquea "Revisa tu correo" (el registro ya fue exitoso), pero deja rastro en consola.
- Suite de tests en verde.
- Verificado en vivo contra dev: registrar una cuenta real nueva, confirmar que un token real se genera, confirmar el correo desde la vista nueva (sin llamar al endpoint de confirmación a mano).

## Hecho
