# 084 — Ownership real de proyectos al crear/listar

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — HU-1 y HU-2. Hoy `ProjectEntity.owner_ref` existe en
el esquema pero `ProjectService.create()` siempre lo deja en `null`, y
`ProjectService.list()` devuelve TODOS los proyectos de TODOS los
usuarios sin filtrar. Este ticket es la base de todo lo demás: sin
ownership real poblado, ningún control de acceso (ticket `085`) ni
visibilidad (ticket `086`) tiene sentido.

**Depende de:** nada nuevo — reutiliza el `JwtDecoder` ya construido en
el ticket `077`.

## Alcance
- **Sí incluye:**
  - Migración `V5`: `projects.visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE'`
    + `CHECK (visibility IN ('PRIVATE', 'PUBLIC'))` (ver sección "Diseño
    técnico → 1" del documento de definición).
  - `ProjectEntity.visibility` (campo nuevo) + getter/setter.
  - `ProjectController.create`/`ProjectService.create(name, ownerId)`:
    toma el `sub` del JWT (`@AuthenticationPrincipal Jwt jwt`) y lo graba
    como `ownerRef`; `visibility` nace siempre `PRIVATE`.
  - `ProjectService.list(ownerId)`: nuevo método de repositorio
    (`findByOwnerRefAndDeletedAtIsNullOrderByUpdatedAtDesc`) — reemplaza
    el `list()` global. `ProjectController.list` exige `Jwt` no nulo
    (401 si falta).
  - `GET /api/mobs/recent` (`MobRecentController`/servicio detrás):
    mismo criterio — filtra por dueño autenticado, deja de ser global.
  - Limpieza de datos: borrar (soft-delete) el único proyecto de DEV con
    `owner_ref IS NULL` — es dato de prueba anterior a que existiera
    ownership, no hay equivalente en QA.
- **No incluye:** proteger las rutas de mutación/lectura de proyectos
  ajenos (ticket `085`), visibilidad pública/Explorar (ticket `086`),
  nada de frontend (tickets `087`/`088`).

## Criterios de aceptación (TDD)
- `ProjectServiceTest`: crear un proyecto autenticado graba `ownerRef` =
  el `sub` del JWT y `visibility = PRIVATE`.
- `list(ownerId)` solo devuelve proyectos de ese dueño, nunca los de
  otro — test explícito con dos usuarios distintos.
- Suite completa del backend en verde.
- Verificación en vivo contra DEV: crear un proyecto autenticado, leer
  la fila directamente en la base, confirmar `owner_ref`/`visibility`
  reales (no simulados).

## Hecho
