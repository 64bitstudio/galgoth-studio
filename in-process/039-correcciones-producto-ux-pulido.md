# 039 — Correcciones funcionales y de pulido visual

## Objetivo

Pasada de producto/UX y acabado visual sobre Inicio, CRUD de proyectos/mobs,
toolbar del editor y scrollbars, pedida explícitamente por el PO (spec
completa de 21 puntos + "NO HACER" + criterios de Done, 2026-09-10). Sin
rediseño de arquitectura, sin cambiar el alcance del Technical Alpha,
Textura/Animación se mantienen como "Próximamente".

## Auditoría del estado real ANTES de escribir código

- **CRUD de proyectos YA existe funcional de punta a punta**: backend
  (`PATCH/DELETE/POST .../duplicate /api/projects/{id}`, ticket 021) +
  frontend (`ProjectCard.vue` con `GMenu` real, `ProjectNameModal.vue` con
  `<dialog>` nativo real -- no `window.prompt`). Lo que falta no es CRUD
  nuevo sino: (a) el confirm de "Eliminar" usa un `GPanel`+backdrop ad-hoc
  en vez de un `AppDialog` reutilizable, sin loading/bloqueo de doble
  submit; (b) no hay forma de renombrar desde el propio detalle del
  proyecto (punto 4).
- **CRUD de mobs NO existe**: `MobController` solo tiene `POST`/`GET` --
  sin `PATCH` (rename) ni `DELETE`. Hace falta agregar ambos (el ticket
  permite explícitamente tocar backend "lo necesario para habilitar las
  acciones descritas"). `mobs` no tiene columna de soft-delete -- se agrega
  `deleted_at` (migración `V*__mobs_soft_delete.sql`), mismo patrón EXACTO
  ya usado en `projects.deleted_at` (ticket 021) -- consistente, no
  inventa un mecanismo nuevo. Export de mob YA existe (`GET
  /api/mobs/{mobId}/export/bbmodel`) -- se cablea al menú. Duplicate de
  mob NO existe y no es mínimo requerido ("si ya existen") -- queda fuera
  de este ticket.
- **`GMenu`/`GTabs` YA implementan gran parte de los puntos 2/15**: `GTabs`
  ya muestra Textura/Animación deshabilitadas con motivo visible (no solo
  tooltip); `GMenu` ya es el componente compartido de menú contextual
  (surface/borde/shadow/hover/foco/separación visual para `danger`). No
  hace falta crear ninguno de los dos desde cero.
- **`IconButton` usa SOLO `title=""` nativo como tooltip** -- exactamente
  lo que el punto 6 prohíbe como solución final. Sin estado
  `:focus-visible` propio tampoco (punto 7).
- **Sin componente de scrollbar custom** -- ninguna clase `.app-scroll` ni
  tokens de scrollbar en `tokens.css`/`reset.css` todavía.
- **Inicio (`ProjectsDashboard.vue`) tiene las CTAs invertidas**: "Proyecto
  vacío" es la card primaria (acento verde), "Crear un mob con IA" está
  deshabilitada con "Disponible en una fase futura" -- exactamente al
  revés de lo que pide el punto 1.
- **Detalle de proyecto (`ProjectDetail.vue`) tiene las CTAs invertidas**:
  "Agregar mob" es `GButton variant="primary"`, "Crear con IA" es un link
  con estilo secundario -- al revés de lo que pide el punto 12.

## Decisiones técnicas (sin preguntar de nuevo lo ya evidente en el código)

- **Tooltip real**: se extiende `IconButton.vue` (no un wrapper nuevo por
  cada call-site) con una burbuja propia del design system, mostrada en
  `:hover`/`:focus-visible` con un delay de aparición corto y consistente
  (occultamiento inmediato). `aria-label` (ya existente) sigue siendo la
  fuente accesible del nombre -- la burbuja es visual, `aria-hidden`. Se
  quita el `title=""` nativo (dejaría un tooltip del navegador duplicado
  encima del custom). Prop opcional `shortcut` -- solo se pasa en
  Undo/Redo, que son los únicos con atajo de teclado real ya cableado.
- **`AppDialog`**: se extrae un componente genérico a partir del patrón ya
  probado de `ProjectNameModal.vue` (`<dialog>` nativo, backdrop nativo,
  Escape ya gratis del navegador) -- `ProjectNameModal` pasa a construirse
  sobre `AppDialog`, y el confirm de "Eliminar proyecto"/"Eliminar mob"
  también, con loading y bloqueo de doble submit reales.
- **Soft-delete de mobs**: mismo mecanismo que proyectos (`deleted_at`),
  filtrado en `listMobs`/`getMob`. Justificación: los FKs de
  `mob_revisions`/`mob_drafts`/`reference_images`/`ai_jobs` hacia `mobs`
  NO tienen `ON DELETE CASCADE` (`V1__init_schema.sql`) -- un hard-delete
  fallaría por violación de FK en cualquier mob con historial real.
- **Editar pivote**: ya no es un botón de la toolbar (se movió al panel
  Inspector en el ticket 036, decisión ya documentada) -- el tooltip de
  "editar pivote" no aplica a un ícono de toolbar inexistente; se agregan
  tooltips a los campos del Inspector que lo controlan en su lugar.

## Criterios de aceptación (checklist del PO, ticket original)

- [ ] "Crear un mob con IA" habilitado, primera opción visual, acento mint, lleva al wizard real.
- [ ] "Crear nuevo proyecto" como alternativa secundaria (reemplaza "Proyecto vacío").
- [ ] Ya no aparece "Disponible en una fase futura" para IA.
- [ ] Renombrar/Eliminar proyecto funcional (ya existía, se pule: AppDialog + loading + bloqueo doble submit).
- [ ] Menú ⋮ de proyecto funcional (ya existía, se verifica).
- [ ] Editar nombre desde el detalle del proyecto (icono junto al título).
- [ ] CRUD de mobs: Renombrar + Eliminar reales (backend nuevo) + Exportar cableado; menú ⋮ en `MobCard`.
- [ ] Todos los icon buttons del editor con tooltip real (no solo `title`).
- [ ] Tooltips funcionan con mouse y teclado (`:focus-visible`).
- [ ] Estados default/hover/active/focus-visible/disabled distinguibles sin depender solo de color.
- [ ] Scrollbars custom aplicadas a jerarquía/inspector/panel IA/modales/paneles del editor.
- [ ] Jerarquía: nombres truncados muestran el nombre completo en hover (tooltip).
- [ ] CTA del detalle de proyecto: "Crear con IA" primario, "Agregar mob" secundario.
- [ ] Identidad visual de IA consistente (spark/estrella + mint) en cada CTA de IA.
- [ ] Sin `alert()`/`confirm()`/`prompt()` nativos en ningún flujo nuevo o tocado.
- [ ] Textura/Animación siguen visibles como "Próximamente" (sin tocar).
- [ ] Nada de lo existente se rompe (suite completa verde).

## Hecho
(se completa al cerrar el ticket)
