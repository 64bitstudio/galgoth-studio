# 002 — Sistema de diseño base (Visual Contract)

**Milestone:** M0 · **Depende de:** 001 · **HUs:** —

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección "Visual Contract"). Implementar los tokens de diseño (dark graphite/charcoal, verde menta como acento funcional) y los componentes Vue base, fieles a los 12 mockups — precede a la implementación de cualquier pantalla productiva.

## Criterios de aceptación (TDD)
- Dado los tokens definidos (`--bg #0B0F14`, `--panel #111820`, `--surface #171F29`, `--accent #48E5A0`, etc.), cuando se renderiza cualquier componente base, entonces usa exclusivamente esos tokens (sin colores hardcodeados fuera del sistema).
- Dado un componente que representa un estado activo/seleccionado, cuando se muestra, entonces comunica el estado en verde menta **y** con una señal no-color (forma/ícono) — no solo color.
- Dado el sidebar global, cuando se renderiza, entonces contiene únicamente Nuevo proyecto / Mis proyectos / Recientes / Configuración — sin ítem "Volver".
- Dado un botón o control interactivo, cuando se inspecciona, entonces tiene un hit target ≥40px y un estado de foco visible por teclado.
- Dado un revisor comparando el showcase de componentes contra `mockups/00_all_views.png`, cuando lo hace, entonces no hay reinterpretación de estructura/layout sin aprobación (Visual Contract, punto 11 del documento de definición).

## Hecho

Completado 2026-09-08. PR [`#2`](https://github.com/64bitstudio/galgoth-studio/pull/2) (rama `feature/002-sistema-diseno-base`), CI de Jenkins en verde (build #3: lint + 12 tests Vitest + build + análisis SonarQube con Quality Gate `OK`, proyecto `galgoth-studio-frontend`).

**Implementado:**
- Scaffold de `frontend/` (Vue 3 + TS strict + Vite + Pinia + Vue Router + Three.js + Vitest + Vue Test Utils + ESLint), fuentes self-hosted (`@fontsource` Inter Variable + JetBrains Mono).
- `frontend/src/design-system/`: tokens (`tokens/tokens.css`, `reset.css`) fieles al master prompt §2; componentes `GButton`, `GSidebar`, `GStatusPill`, `GPanel`, `GTabs`; set propio de íconos de línea (`icons/`); vitrina de QA en `/dev/design-system`.
- `Jenkinsfile`: primer `buildAndTest` real (antes placeholder vacío) — lint + test + build + análisis SonarQube.
- `frontend/sonar-project.properties` nuevo.
- `docs/COMPONENTES.md` actualizado.

**Verificado en vivo** (Claude in Chrome, `npm run dev` + navegación real, no solo el check estático de accesibilidad de cada edición): anillo de foco visible al tabular, estado activo del sidebar con color + borde + peso de fuente (nunca solo color), tabs deshabilitadas con motivo visible, showcase renderiza correctamente.

**Hallazgo real encontrado en el camino, corregido antes de cerrar (no en el plan original):** al verificar de verdad el criterio de aceptación #5 (comparar contra el mockup real, algo que no se había hecho hasta ese punto), se encontró que `GSidebar` estaba construido fiel al *texto* del master prompt §3 ("Nuevo proyecto, Mis proyectos, Recientes, Configuración") — pero el *mockup real* (`01_inicio_mis_proyectos.png`, la fuente de verdad visual declarada) muestra algo distinto: "Inicio, Mis proyectos, Explorar, Plantillas" arriba y "Configuración, Usuario" abajo, con "Nuevo proyecto" como tarjeta CTA del dashboard, no ítem de sidebar. Consultado explícitamente con el Product Owner (no resuelto en silencio) — confirmó el mockup como fuente de verdad. Corregido: `GSidebar` reescrito, íconos nuevos (`IconHome`, `IconExplore`, `IconTemplates`, `IconUser`; `IconProjects` pasa de cubo a carpeta), tests actualizados, y el texto de `docs/definiciones/galgoth-studio-mvp.md` (Visual Contract §6) corregido con nota en el Addendum — no cambia alcance ni arquitectura, corrige un error de transcripción del master prompt detectado en implementación.

**Gap conocido, documentado a propósito (no silencioso):** el ítem "Usuario" del sidebar se renderiza como un nav-item simple, sin el tratamiento de tarjeta de perfil (avatar + subtítulo "Creador de mundos") que muestra el mockup — depende de datos reales de usuario/proyecto que no existen todavía. Queda para cuando se implemente esa pantalla real.

**Segundo hallazgo real, ya durante el cierre:** el primer intento de `Jenkinsfile` (`buildAndTest` con lint+test+build pero sin Sonar) rompió el pipeline — `corePipeline` exige que `buildAndTest` llame `withSonarQubeEnv('sonarqube-vm')` siempre que esté definido, o la etapa `Quality Gate de SonarQube` falla con `IllegalStateException`. Corregido agregando el análisis real; documentado como gotcha en el skill `bootstrap-proyecto` para no repetirlo en el próximo proyecto.
