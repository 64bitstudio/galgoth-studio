# 074 — Pantalla de verificación de email

## Objetivo
auth-core-mc ya envía un correo real de verificación al registrarse (ticket 073), pero galgoth-studio no tiene ninguna pantalla para confirmarlo ni para reenviarlo si expira o no llega. Decisión explícita de Marco (`PROP-GS-AUTH-01`, sección 08): pantalla completa en v1, no un mensaje mínimo.

**Depende de:** 073 (el registro que dispara el correo).

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
