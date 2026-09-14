# 087 — Guard de rutas autenticadas + toggle de visibilidad (frontend)

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — cierre de HU-1/HU-2/HU-4 del lado del frontend. Con el
backend ya exigiendo login para "Mis proyectos" (ticket `085`), el
frontend hoy no redirige a nadie a `/login` — un visitante sin sesión
llega a `/projects` y solo vería errores `401` sin explicación.

**Depende de:** `085` (backend ya debe exigir `authenticated()` en las
rutas propias) y `086` (para el toggle de visibilidad).

## Alcance
- **Sí incluye:**
  - `router.beforeEach` nuevo: rutas `home`, `projects-dashboard`,
    `project-detail` (cuando el proyecto es propio), `mob-editor`,
    `export-screen`, `ai-mob-wizard` exigen
    `sessionStore.isAuthenticated` — si no, redirige a `/login`
    (guardando la ruta destino para volver tras loguearse, mismo patrón
    ya usado en tickets `080`-`083`).
  - `ProjectDetail.vue`: control de visibilidad (toggle o menú "Hacer
    público"/"Hacer privado") sobre `PATCH /api/projects/{id}/visibility`
    del ticket `086`.
- **No incluye:** la sección Explorar en sí (ticket `088`); que
  `projectsApi.ts`/`mobsApi.ts` adjunten `Authorization` — se adelantó
  como hotfix (ticket `089`) al descubrirse como regresión visible en
  vivo apenas se desplegó el ticket `084`, antes de que este ticket
  arrancara.

## Criterios de aceptación (TDD)
- Visitante sin sesión que navega a `/projects` es redirigido a
  `/login`.
- Usuario logueado puede togglear la visibilidad de un proyecto propio y
  ve el cambio reflejado sin recargar.
- Suite de tests del frontend en verde (Vitest).
- Verificación en vivo: navegación real contra DEV sin sesión iniciada
  confirmando el redirect, y con sesión confirmando el toggle.

## Hecho
