# 088 — Sección Explorar (frontend)

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — HU-5 y HU-6. El ítem "Explorar" ya existe como stub en
`GSidebar.vue` (`{ key: 'explore', label: 'Explorar', icon: IconExplore }`)
sin ruta detrás. Este ticket construye la pantalla real.

**Depende de:** `086` (endpoint `GET /api/explore/projects` y detalle
público ya deben existir).

## Alcance
- **Sí incluye:**
  - Nueva ruta `/explore` → `ExploreView.vue`: galería de tarjetas
    (miniatura, nombre, descripción, `ownerDisplayName`) consumiendo
    `GET /api/explore/projects`, **sin** exigir sesión (guard del ticket
    `087` no aplica aquí).
  - Nueva ruta `/explore/:id` → `ExploreProjectDetail.vue`: ficha del
    proyecto público + lista de sus mobs (nombre, miniatura, estado) en
    modo estrictamente lectura — sin ningún botón de editar, borrar,
    exportar o duplicar (fuera de alcance, ver documento de definición).
  - `GSidebar.vue`: `handleSidebarSelect` en las vistas que la usan
    (`HomeView.vue`, `ProjectsDashboard.vue`, etc.) navega de verdad a
    `/explore` cuando `key === 'explore'` — deja de ser un no-op.
  - Estado vacío ("Todavía no hay proyectos públicos") si Explorar no
    tiene resultados — nunca una pantalla en blanco sin explicación
    (mismo criterio de "nunca fingir funcionalidad" del resto del
    proyecto).
- **No incluye:** visor 3D en modo lectura (el detalle de mobs se queda
  en nombre/miniatura/estado, sin abrir `MobEditor.vue`), duplicar desde
  Explorar, búsqueda/filtros.

## Criterios de aceptación (TDD)
- Visitante sin sesión navega a `/explore` y ve los proyectos públicos
  reales.
- Abrir un proyecto público desde Explorar muestra su ficha y mobs, sin
  ninguna acción de edición visible.
- Un proyecto privado nunca es alcanzable vía `/explore/:id` (backend ya
  responde `404`, el frontend muestra el estado "no encontrado", no un
  error genérico).
- Suite de tests del frontend en verde (Vitest).
- Verificación en vivo contra DEV: publicar un proyecto real desde una
  cuenta, confirmar que aparece en `/explore` navegando SIN sesión
  iniciada (ventana privada o sesión cerrada), y que su detalle se ve
  correctamente en modo lectura.

## Hecho
