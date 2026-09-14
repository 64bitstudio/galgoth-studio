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
