# 093 — Pantalla "Usuario" (frontend)

## Objetivo
Ver `docs/definiciones/perfil-de-usuario.md` (VoBo de Marco recibido) —
cierre de todas las HUs del lado del frontend. El ítem "Usuario" del
sidebar es hoy un placeholder deliberado (documentado en el Javadoc de
`GSidebar.vue`: "sin el tratamiento de tarjeta de perfil... eso depende
de datos reales de usuario que no existen todavía"). Con auth-core-mc
(`060`-`064`) y galgoth-studio (`091`-`092`) ya construidos, esos datos
ya existen.

**Depende de:** `060`, `061`, `062`, `063`, `064` (auth-core-mc), `091`,
`092` (galgoth-studio).

## Alcance
- **Sí incluye:**
  - Nueva ruta `/usuario` (o `/profile`) con la pantalla completa del
    mockup: cabecera (avatar, nombre, correo, "Miembro desde", badge),
    Información personal, Cuentas conectadas, Seguridad, Preferencias,
    Zona de peligro.
  - Cada sección conectada a su endpoint real (060-064, 091-092) — sin
    datos de relleno.
  - "Verificación en dos pasos" y el toggle "Tema oscuro" se muestran
    deshabilitados con indicación clara ("Próximamente" / "Sin tema
    claro todavía") — nunca fingiendo que funcionan.
  - Confirmación explícita (modal, no solo un clic) antes de "Eliminar
    cuenta" y antes de "Cerrar sesión en todos los dispositivos".
  - `GSidebar.vue`: el ítem "Usuario" navega de verdad a esta pantalla
    (hoy es un ítem de nav sin ruta detrás, mismo gap documentado que
    "Explorar" tenía antes del ticket `088`).
- **No incluye:** nada que no esté ya cubierto por los tickets de
  backend listados.

## Criterios de aceptación (TDD)
- Cada sección de la pantalla refleja datos reales del usuario
  autenticado (no mocks).
- Editar información personal, cambiar contraseña, revocar una sesión,
  vincular una cuenta social, guardar preferencias y eliminar la cuenta
  funcionan de punta a punta contra los endpoints reales.
- Suite de tests del frontend en verde (Vitest).
- Verificación en vivo contra DEV: recorrido completo de la pantalla con
  una cuenta de prueba real, incluyendo vincular una cuenta de Google
  real y, al final, eliminar esa cuenta de prueba.

## Hecho
