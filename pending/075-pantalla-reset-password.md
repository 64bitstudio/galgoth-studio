# 075 — Pantalla de reset de contraseña

## Objetivo
El endpoint de recuperación de contraseña ya existe en auth-core-mc, pero galgoth-studio no tiene pantalla para usarlo. Decisión explícita de Marco (`PROP-GS-AUTH-01`, sección 08): entra en el alcance de v1.

**Depende de:** 073 (misma superficie de login/registro).

## Alcance
**Incluye:**
- Pantalla "olvidé mi contraseña" (pide email/teléfono, dispara `POST /api/v1/password-reset/**` de auth-core-mc).
- Pantalla de confirmación (nueva contraseña + token del link recibido).

**No incluye:**
- Cambiar la contraseña desde una sesión ya iniciada (eso sería parte de una pantalla de cuenta, fuera de alcance de este ticket).

## Criterios de aceptación (TDD)
- Pedir el reset con un email/teléfono real dispara el correo/SMS real (auth-core-mc ya lo hace — este ticket solo verifica que la UI lo dispara correctamente).
- Un token de reset válido permite fijar una contraseña nueva y loguearse con ella después.
- Un token expirado/inválido muestra un error claro, sin cambiar la contraseña.

## Hecho
