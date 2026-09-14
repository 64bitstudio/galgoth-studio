# 080 — Pantalla de reset de contraseña

> Renumerado de 075 → 080: colisionó con el ticket real 075 ("fix resize viewport al togglear sidebar", ya en `done/`) — ver `fix/renumerar-tickets-auth-072-075`.

## Objetivo
El endpoint de recuperación de contraseña ya existe en auth-core-mc, pero galgoth-studio no tiene pantalla para usarlo. Decisión explícita de Marco (`PROP-GS-AUTH-01`, sección 08): entra en el alcance de v1.

**Depende de:** 078 (misma superficie de login/registro).

## Alcance
**Incluye:**
- Pantalla "olvidé mi contraseña" (pide email/teléfono, dispara `POST /api/v1/password-reset/request` de auth-core-mc). Sin diseño de referencia específico — mismo tratamiento visual de 081/082, formulario propio.
- Pantalla de confirmación (nueva contraseña + token del link recibido) — diseño aportado por Marco, mismo fondo que la anterior.
- **Ruta de la pantalla de confirmación fija a `/password-reset/confirm`**, sin margen de elección: es exactamente el path que auth-core-mc ya usa en los links reales de correo desde el ticket 056 de ese repo (`ownUiOrigin() + hostedPath.replace('/ui','')`). Cualquier otro nombre de ruta rompería los links ya en producción.
- **"¿Olvidaste tu contraseña?" en `LoginView.vue`**, hoy deshabilitado con "Próximamente" (ticket 081) — se activa como link real a la pantalla de "olvidé mi contraseña", ahora que la funcionalidad existe.

**No incluye:**
- Cambiar la contraseña desde una sesión ya iniciada (eso sería parte de una pantalla de cuenta, fuera de alcance de este ticket).

## Hallazgo real (copy del mockup vs. regla real del backend)
El mockup de Marco dice "una letra mayúscula y un número" — la regla real
de `PasswordPolicy` en auth-core-mc (`validate`) exige **8+ caracteres,
al menos una letra (cualquiera, no necesariamente mayúscula) y un
número**. Se usa el texto correcto ("al menos una letra y un número"),
no el del mockup literal, para no mostrar un requisito que no es real.

## Criterios de aceptación (TDD)
- Pedir el reset con un email/teléfono real dispara el correo/SMS real (auth-core-mc ya lo hace — este ticket solo verifica que la UI lo dispara correctamente); la respuesta es siempre el mismo mensaje genérico, nunca revela si la cuenta existe (mismo comportamiento que ya documenta auth-core-mc).
- Un token de reset válido permite fijar una contraseña nueva y loguearse con ella después.
- Un token expirado/inválido muestra un error claro, sin cambiar la contraseña.
- "Nueva contraseña"/"Confirmar contraseña" no coincidentes bloquean el envío del lado del cliente (mismo patrón del ticket 082).
- Verificación en vivo contra `studio-dev.galgoth.64bitstudio.com`: disparar un reset real, recibir el correo real, confirmar que el link `/password-reset/confirm?token=...` carga la pantalla y que el flujo completo (fijar contraseña nueva → iniciar sesión con ella) funciona de punta a punta.

## Hecho

Implementado como se describe arriba: `authApi.ts` (`requestPasswordReset`,
`confirmPasswordReset`), `ForgotPasswordView.vue` (/forgot-password),
`ResetPasswordView.vue` (/password-reset/confirm, ruta fija), mismo
tratamiento visual split-screen de 081/082 con los fondos aportados por
Marco. `LoginView.vue`: "¿Olvidaste tu contraseña?" activado como link
real.

**Tests**: 9 tests nuevos (authApi +3, ForgotPasswordView +2,
ResetPasswordView +4) + `LoginView.spec.ts` actualizado con la ruta
nueva. Suite completa del frontend: **765/765 tests**, lint y
`vue-tsc -b` limpios.

**PR #111** (junto con el ticket 083 — el checkbox de términos de
Register depende de que `/terms` exista) mergeado a `dev`, deploy
automático confirmado (Jenkins build #110 de `dev`, SUCCESS).

**Verificación en vivo de punta a punta contra DEV real** (no simulada):
1. `/forgot-password` real con `marcocortes1234.mc@gmail.com` → "Revisa
   tu correo" → correo real recibido, confirmado por Marco: el link es
   `https://studio-dev.galgoth.64bitstudio.com/password-reset/confirm?token=...`
   (ruta fija correcta).
2. Token real leído directamente de Redis (`token:password-reset:...`,
   dev) para completar la prueba sin pedirle a Marco que pegue el token
   en el chat (el hook de secretos ya lo bloquea, con razón).
3. Un primer intento con datos sin verificar dejó el token quemado sin
   completar el reset (hallazgo real: `PasswordResetService.confirmReset`
   consume el token ANTES de validar la contraseña — un intento fallido
   igual invalida el token, por diseño de un solo uso). Se repitió todo
   el flujo desde cero, verificando cada campo con zoom antes de enviar.
4. Con el token fresco: "Nueva contraseña"/"Confirmar contraseña"
   llenados y verificados → `Actualizar contraseña` → "Contraseña
   actualizada" real.
5. **Login real con la contraseña nueva** → navegó correctamente al home
   autenticado. Flujo de punta a punta confirmado funcionando.

## Hallazgo de proceso (no del código)
El primer intento de verificación (paso 3 arriba) reveló que un token de
reset se quema con cualquier intento de confirmación, exitoso o no — un
comportamiento de seguridad correcto y ya documentado en el
comportamiento de `PasswordResetService`, pero que exige verificar cada
campo antes de enviar en cualquier prueba manual/automatizada de este
flujo (no reintentar "a ciegas" con el mismo link).
