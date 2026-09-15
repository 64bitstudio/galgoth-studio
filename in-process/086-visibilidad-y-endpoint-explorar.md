# 086 — Cambiar visibilidad de un proyecto + endpoint de Explorar

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — HU-4 y HU-5. Con ownership real (`084`) y enforcement
(`085`) ya en pie, falta la pieza que le da valor de producto a todo
esto: que el dueño pueda publicar un proyecto, y que exista una API de
lectura pública para listarlos.

**Depende de:** `084` y `085`.

## Alcance
- **Sí incluye:**
  - `PATCH /api/projects/{projectId}/visibility` (o el shape que se
    decida en implementación — detalle de API, no de producto): cambia
    `PRIVATE ↔ PUBLIC` de un proyecto propio. No-dueño → `404` (mismo
    criterio de "Riesgos y preguntas abiertas" del documento: no se
    distingue "existe pero no es tuyo" de "no existe").
  - `GET /api/explore/projects` (nuevo, `permitAll()`): lista proyectos
    `visibility = PUBLIC`, orden por `updated_at desc` — mismo orden que
    "Mis proyectos" hoy. Sin búsqueda/filtros/paginación en esta primera
    pasada (ver "No incluye" del documento).
  - `projects.owner_display_name` (columna nueva, migración `V6`):
    capturado del `nombre`/`apellidos` de la sesión del frontend al
    crear el proyecto (decisión recomendada del documento, aprobada en
    el VoBo) — se envía en `CreateProjectRequest`, se graba tal cual, sin
    resolverlo después contra auth-core-mc.
  - Detalle de solo lectura de un proyecto público ajeno: extiende
    `ProjectController.get` (ya cubierto por `requireViewable` del
    ticket `085`) para incluir `ownerDisplayName` y la lista de mobs
    (nombre + miniatura + estado) en la respuesta — sin acciones de
    edición en el payload (el frontend decide qué mostrar, pero el
    contrato ya distingue "es mío" vs "ajeno público" en la respuesta).
- **No incluye:** visor 3D en modo lectura, duplicar un proyecto ajeno,
  moderación, perfiles públicos — todo explícitamente fuera de alcance
  en el documento de definición.

## Criterios de aceptación (TDD)
- Dueño cambia la visibilidad de su proyecto → aparece/desaparece de
  `GET /api/explore/projects` de inmediato.
- No-dueño intenta cambiar visibilidad de un proyecto ajeno → `404`.
- `GET /api/explore/projects` sin `Authorization` funciona (anónimo) y
  nunca incluye un proyecto `PRIVATE`.
- `owner_display_name` se graba al crear y viaja en la respuesta de
  Explorar y del detalle público.
- Suite completa del backend en verde.
- Verificación en vivo contra DEV: publicar un proyecto real, confirmar
  que aparece en `GET /api/explore/projects`, despublicarlo y confirmar
  que desaparece.

## Hecho
