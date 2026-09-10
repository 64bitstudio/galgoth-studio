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

- [x] "Crear un mob con IA" habilitado, primera opción visual, acento mint, lleva al wizard real.
- [x] "Crear nuevo proyecto" como alternativa secundaria (reemplaza "Proyecto vacío").
- [x] Ya no aparece "Disponible en una fase futura" para IA.
- [x] Renombrar/Eliminar proyecto funcional (ya existía, se pule: AppDialog + loading + bloqueo doble submit).
- [x] Menú ⋮ de proyecto funcional (ya existía, se verifica).
- [x] Editar nombre desde el detalle del proyecto (icono junto al título).
- [x] CRUD de mobs: Renombrar + Eliminar reales (backend nuevo) + Exportar cableado; menú ⋮ en `MobCard`.
- [x] Todos los icon buttons del editor con tooltip real (no solo `title`).
- [x] Tooltips funcionan con mouse y teclado (`:focus-visible`).
- [x] Estados default/hover/active/focus-visible/disabled distinguibles sin depender solo de color.
- [x] Scrollbars custom aplicadas a jerarquía/inspector/panel IA/modales/paneles del editor.
- [x] Jerarquía: nombres truncados muestran el nombre completo en hover (tooltip).
- [x] CTA del detalle de proyecto: "Crear con IA" primario, "Agregar mob" secundario.
- [x] Identidad visual de IA consistente (spark/estrella + mint) en cada CTA de IA.
- [x] Sin `alert()`/`confirm()`/`prompt()` nativos en ningún flujo nuevo o tocado.
- [x] Textura/Animación siguen visibles como "Próximamente" (sin tocar).
- [x] Nada de lo existente se rompe (suite completa verde).

## Hecho

Cerrado en 2 PRs sobre `dev` (nunca `qa`/`prod`): **#50** (todo el
alcance del ticket) con un segundo commit corrigiendo un hallazgo real
de la verificación en vivo (ver abajo).

### Inicio y detalle de proyecto

- `ProjectsDashboard.vue`: "Crear un mob con IA" pasa de deshabilitada a
  ser la CTA PRIMARIA (acento mint, `IconSparkle`, primera opción
  visual). "Crear nuevo proyecto" (antes "Proyecto vacío") queda
  secundaria. Como el wizard IA necesita un `projectId` real y desde
  Inicio no hay ninguno todavía, `AiMobProjectPickerDialog.vue` (nuevo)
  pide elegir un proyecto existente o crear uno nuevo antes de abrir el
  wizard -- decisión explícita del PO (arranca directo en "nuevo
  proyecto" si todavía no hay ninguno).
- `ProjectDetail.vue`: CTAs "Crear con IA"/"Agregar mob" invierten su
  prominencia (antes "Agregar mob" era la `GButton` primaria). Ícono de
  editar junto al título abre `ProjectNameModal` en modo `rename`.

### Diálogos del design system (nunca `alert`/`confirm`/`prompt` nativos)

- `AppDialog.vue` (nuevo): shell genérico extraído del patrón ya probado
  en `ProjectNameModal.vue` (`<dialog>` nativo, backdrop/Escape gratis).
- `ConfirmDialog.vue` (nuevo): confirmaciones destructivas con `busy`
  (deshabilita ambos botones, bloquea doble submit real) y `error` (se
  muestra sin cerrar el diálogo) -- usado por Eliminar proyecto/mob.
- `MobRenameDialog.vue`, `AiMobProjectPickerDialog.vue` (nuevos).
- `ProjectNameModal.vue` reconstruido sobre `AppDialog` -- mismo
  comportamiento, ahora con `busy`/`error` reales.

### CRUD de mobs (backend nuevo -- ticket 022 solo pedía crear/listar)

- `PATCH`/`DELETE /api/mobs/{mobId}` (`MobDetailController`/`MobService`).
- Soft-delete real: `mobs.deleted_at` (`V2__mobs_soft_delete.sql`) --
  mismo criterio que `projects.deleted_at`, justificado porque ningún FK
  hacia `mobs` (`mob_revisions`/`mob_drafts`/`reference_images`/`ai_jobs`)
  tiene `ON DELETE CASCADE`.
- `MobCard.vue` gana el menú ⋮ real (Renombrar/Exportar/Eliminar),
  restructurado igual que `ProjectCard.vue` (el botón de abrir y el menú
  son hermanos, nunca anidados).
- Duplicate de mob individual queda fuera de alcance (no existía, no era
  mínimo requerido).

### Tooltips + estados de toolbar

- `IconButton.vue`: tooltip visual propio (burbuja del design system,
  `aria-hidden`) reemplaza `title=""` nativo -- visible en `:hover` Y
  `:focus-visible`, delay de aparición corto (350ms) y ocultamiento
  instantáneo. El nombre accesible real sigue siendo `aria-label` (nunca
  depende del tooltip). Prop `shortcut` opcional, solo en Deshacer/Rehacer
  (únicos con atajo real cableado en `EditorToolbar.vue`, detectado
  Mac/no-Mac para mostrar Cmd/Ctrl correcto).
- Labels de la toolbar traducidos a español (Mover/Escalar/Rotar/Agregar
  cuboide/Agregar bone/Duplicar/Eliminar/Deshacer/Rehacer), consistentes
  con el resto de la UI.
- `HierarchyBoneNode.vue` migra su botón de eliminar bone a `IconButton`
  por el mismo motivo.
- "Editar pivote" ya no es un botón de toolbar (se movió al Inspector en
  el ticket 036) -- no aplica un tooltip a un ícono inexistente.
- Estados default/hover/active/focus-visible/disabled ya estaban
  cubiertos por `IconButton`/el reset global (`:focus-visible` con
  `box-shadow`, nunca solo color) -- verificado, no hizo falta CSS nuevo.

### Scrollbars y jerarquía

- `.app-scroll` (nuevo, `tokens.css`/`reset.css`) aplicada a la
  jerarquía, el inspector, el panel de IA del editor (`AiEditPanel.vue`)
  y los `<main>` de página completa (Inicio/Detalle de
  proyecto/Wizard IA/Exportación).
- Nombres truncados en la jerarquía muestran el nombre completo en hover
  (`title` nativo sobre el `<span>` de texto -- caso apropiado, a
  diferencia de un icon button: el texto completo ya está en el DOM).

### Hallazgo real de la verificación en vivo (corregido antes de cerrar)

Verificación manual con el stack local completo (Postgres+MinIO+backend
con providers mock+frontend real) vía Claude in Chrome, siguiendo el
flujo obligatorio del ticket: Inicio→IA→picker→wizard abre correctamente;
crear/renombrar/eliminar proyecto con diálogos reales; menú ⋮ de mob
funcional (Renombrar/Exportar/Eliminar); tooltips confirmados con mouse
Y con foco por teclado (`document.activeElement`+`:focus-visible` real,
no solo visual); scrollbar custom confirmada (`getComputedStyle` real:
`scrollbar-color: rgb(51, 64, 77) transparent`, `scrollbar-width: thin`).

Esta misma verificación expuso un bug real: al agregar soft-delete de
mobs, `ProjectService` seguía usando `countByProjectId`/
`findByProjectIdOrderByUpdatedAtDesc` SIN filtrar `deletedAt` en sus 4
usos reales (conteo de "criaturas" en detalle/rename, Duplicate,
dashboard) -- nunca hizo falta antes porque `mobs` no tenía soft-delete.
Efecto real observado en el navegador: el conteo de "criaturas" no
bajaba al eliminar un mob. Corregido con `countByProjectIdAndDeletedAtIsNull`
(nuevo) + reusar la versión ya filtrada existente en los 4 usos; se
retiraron las versiones sin filtrar del repositorio (no queda una
alternativa "sin filtro" para usar por error más adelante). 3 tests de
regresión nuevos.

### Tests y verificación

Suite completa verde: backend (`gradle build`, incluye 3 tests de
regresión del hallazgo + tests nuevos de `MobControllerTest`/
`MobDetailControllerTest` para PATCH/DELETE) y frontend (lint,
`vue-tsc -b`, build, tests nuevos en `AppDialog`/`ConfirmDialog`/
`IconButton`/`AiMobProjectPickerDialog` + actualizados en `MobCard`/
`ProjectCard`/`ProjectDetail`/`ProjectsDashboard`/`ProjectNameModal`/
`EditorToolbar`/`HierarchyBoneNode`). Gate de Sonar `OK`/0 violaciones
nuevas (frontend y backend) antes de mergear.

### Pendiente / decisión del PO

- El copy exacto de algunos textos (ej. subtítulo de "Crear nuevo
  proyecto") se redactó según lo pedido literalmente en el ticket -- sin
  ambigüedad, no requirió confirmación adicional.
- `Duplicate` de mob individual queda fuera de alcance (no existía antes,
  el ticket no lo pedía como mínimo) -- se puede pedir como ticket futuro
  si hace falta.
