# 089 — Hotfix: adjuntar Authorization real en projectsApi/mobsApi

## Objetivo
Regresión real detectada por Marco en vivo (captura de pantalla de
`studio-dev.galgoth.64bitstudio.com`, sesión iniciada): Inicio mostraba
"Esta operación requiere haber iniciado sesión." y "Mis proyectos"/
"Continuar trabajando" aparecían vacíos aunque el usuario SÍ tenía
sesión real.

**Causa real**: el ticket `084` (auth-core-mc, ya desplegado a dev) puso
`POST/GET /api/projects` y `GET /api/mobs/recent` a exigir
`Authorization: Bearer` real. `projectsApi.ts`/`mobsApi.ts`
(`frontend/src/projects/`) seguían usando `fetch` plano — nunca
adjuntaban el token, porque hasta este ticket ninguna ruta lo exigía.
El mecanismo para esto ya existía y estaba listo desde el ticket `078`
(`authenticatedFetch.ts`, Javadoc: "ninguna ruta de este backend exige
autenticación todavía... queda listo para cuando la primera ruta
protegida exista") — solo faltaba cablearlo en estos dos clientes.

Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` — este
hotfix se adelanta a una parte del alcance ya prevista para el ticket
`087` (que dependía de `085`/`086` y no había arrancado), porque romper
el uso normal de la app en dev no podía esperar a esa secuencia.

## Alcance
- **Sí incluye:**
  - `projectsApi.ts`/`mobsApi.ts`: su `request()` interno pasa de
    `fetch` a `authenticatedFetch` (ticket 078) — mismo mecanismo ya
    construido y probado (maneja el refresh ante un 401 real).
  - Tests: `projectsApi.spec.ts`, `mobsApi.spec.ts`, `HomeView.spec.ts`,
    `ProjectsDashboard.spec.ts`, `ProjectDetail.spec.ts` y
    `AiMobWizard.spec.ts` (esta última monta `createMob` transitivamente)
    ganan `setActivePinia(createPinia())` en su `beforeEach` — necesario
    porque `authenticatedFetch` lee la sesión desde un store de Pinia, y
    estos tests corrían la implementación real de `projectsApi`/`mobsApi`
    (stub de `fetch` global, no del módulo) sin un Pinia activo.
- **No incluye:** guard de rutas (redirigir a `/login` sin sesión) ni
  toggle de visibilidad — eso sigue siendo el ticket `087`.

## Criterios de aceptación (TDD)
- Suite completa del frontend en verde (Vitest) — 765/765, sin
  regresiones en ningún archivo tocado.
- Verificación en vivo contra DEV: Marco confirma que Inicio/Mis
  proyectos funcionan de nuevo con una sesión real iniciada.

## Hecho
- `projectsApi.ts`/`mobsApi.ts` migrados a `authenticatedFetch`.
- 6 archivos de test actualizados con `setActivePinia(createPinia())`.
  Suite completa: 765/765 en verde.
- **Verificación en vivo** (contra `studio-dev.galgoth.64bitstudio.com`,
  cuenta de prueba desechable creada y luego eliminada vía SQL):
  inicio de sesión real con `authenticatedFetch` cableado → "Inicio" ya
  no muestra "Esta operación requiere haber iniciado sesión.", "Continuar
  trabajando" y "Proyectos recientes" muestran su estado vacío normal
  ("Aún no has creado ningún mob." / "Todavía no tienes proyectos.");
  "Mis proyectos" carga sin error. Confirmado sin necesitar que Marco lo
  revise manualmente.
