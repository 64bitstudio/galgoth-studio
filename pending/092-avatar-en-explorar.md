# 092 — Mostrar el avatar del autor en Explorar

## Objetivo
Ver `docs/definiciones/perfil-de-usuario.md` (VoBo de Marco recibido) —
HU-4. Con `user_profile.avatar_key` ya existiendo (ticket `091`), la
sección Explorar (ticket `086`/`088`) puede mostrar la foto real del
autor de cada proyecto público en vez de solo su nombre.

**Depende de:** `091` (columna `avatar_key`), `086`/`088` (Explorar, ya
hecho).

## Alcance
- **Sí incluye:**
  - El endpoint de Explorar (`GET /api/explore/projects`) y el detalle
    público de un proyecto (`GET /api/projects/{id}` en modo lectura)
    hacen join/lookup contra `user_profile` por `owner_ref` y agregan la
    URL del avatar a la respuesta.
  - Sin avatar (`avatar_key IS NULL`), se omite el campo — el frontend
    ya sabe mostrar un avatar por defecto (inicial del nombre).
- **No incluye:** cambios a la subida/gestión del avatar en sí (ticket
  `091`).

## Criterios de aceptación (TDD)
- Un proyecto público cuyo dueño tiene avatar → la respuesta de
  Explorar/detalle incluye su URL.
- Un proyecto público cuyo dueño NO tiene avatar → el campo es `null`,
  nunca una URL rota.
- Suite completa en verde.
- Verificación en vivo contra DEV.

## Hecho
