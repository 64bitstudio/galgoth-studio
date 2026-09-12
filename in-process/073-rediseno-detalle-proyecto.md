# 073 — Rediseño del detalle de un proyecto (fidelidad visual estricta)

## Objetivo
Reconstruir la pantalla de detalle de proyecto (`ProjectDetail.vue`, en `/projects/{id}`) para que coincida visualmente con la referencia entregada por el Product Owner. Mismo flujo que los tickets 071/072: preview interactivo (Artifact) con VoBo del PO antes de implementar.

## Decisión del Product Owner (previa a implementar, VoBo con condiciones)
VoBo dado con condiciones explícitas y no negociables: *"Doy vobo solo NO uses componentes nativos, utiliza componentes personalizados que hemos venido trabajando, agrega transiciones y si se utilizan modales, no olvides agregar transiciones, todo debe llevar transiciones."*

Decisiones de producto previas (vía AskUserQuestion, cero-suposiciones):
- **Descripción del proyecto**: campo nuevo real y editable (no derivado), mismo lápiz que ya edita el nombre.
- **Dropdown "Todos"** del toolbar de la referencia: se quita -- cada `GSelect` de filtro ya muestra su propio label ("Tipo"/"Estado") como estado "sin filtro", no hace falta una cuarta opción.
- **Menú "•••" del proyecto**: Duplicar/Eliminar (Renombrar no entra -- ya tiene su propio lápiz junto al título).

## Alcance

### Incluye
- **Backend**: `description` (texto libre, opcional) como columna real nueva en `projects` (migración `V4__projects_description.sql`), no derivada -- a diferencia del `status` del ticket 072. Viaja en `ProjectSummary` y `ProjectDetail`. `RenameProjectRequest` pasa a requerir `name` + `description` SIEMPRE explícitos (contrato "always explicit": el caller reenvía el valor actual si no lo cambia -- omitir `description` la borraría en vez de dejarla intacta).
- **Frontend**:
  - `ProjectNameModal.vue`: en modo `rename` agrega un textarea de descripción opcional (mismo modal que ya editaba el nombre); `confirm` ahora emite siempre `(nombre, descripción)`. `ProjectsDashboard.vue`/`HomeView.vue` actualizados al nuevo contrato (pasan `initial-description` y reenvían el valor sin cambios si el usuario no toca el campo).
  - `MobCard.vue` rediseñada: el `GStatusPill` pasa del footer a superpuesto sobre la miniatura (esquina superior derecha); el footer ahora muestra tipo de base (ícono + etiqueta) + fecha relativa, en vez de repetir el estado.
  - 5 íconos nuevos (`design-system/icons/`): `IconHumanoid`, `IconArachnid`, `IconQuadruped`, `IconFlying`, `IconCustomBase` -- mismos 5 tipos de base que `AddMobModal.vue`.
  - `ProjectDetail.vue` reescrito: breadcrumb ("Galgoth Studio > Mis proyectos > {nombre}"), header con descripción bajo la meta-línea, menú ⋮ de acciones DEL PROYECTO (`GMenu`, Duplicar/Eliminar -- mismo componente que ya usan `ProjectCard`/`MobCard`, ninguna lógica nueva de dropdown), toolbar con filtros de Tipo/Estado/Ordenar vía `GSelect` (nunca `<select>` nativo), grid con la `MobCard` rediseñada y la CTA "Nuevo mob" restilizada al patrón de tarjeta punteada con ícono+título+descripción (mismo patrón que la card "Nuevo proyecto" del ticket 072).
  - TODOS los diálogos (los 4 ya existentes + el nuevo de eliminar proyecto) envueltos en `<Transition name="app-dialog">` -- condición explícita del VoBo.
- **Docs**: `docs/API.md` actualizado (`description` en los 3 endpoints de proyecto).

### No incluye
- Filtro Tipo/Estado/Ordenar del lado del backend (paginación/query params) -- sigue siendo client-side, mismo criterio que el buscador (sin ningún requisito de escala que lo justifique todavía).
- Cambiar qué acciones vive en qué menú (Renombrar de mob, Exportar) -- sin cambios frente a lo ya existente.
- Miniaturas/thumbnails reales por tipo de base -- las 5 íconos son solo para el chip de tipo en el footer de la card, no para el render 3D.

## Criterios de aceptación (TDD)
- `GET /api/projects`, `GET /api/projects/{id}` incluyen `description` (`null` si no se ha editado). `PATCH /api/projects/{id}` requiere `name` + `description` explícitos; enviar solo `name` no borra la descripción existente si el caller reenvía el valor actual (probado).
- El detalle de proyecto muestra breadcrumb, descripción (si existe, oculta si no), y permite editarla desde el mismo lápiz que el nombre.
- El menú ⋮ del proyecto permite Duplicar (navega al proyecto duplicado) y Eliminar (confirmación explícita, navega a `/projects` tras eliminar el proyecto actualmente abierto).
- Los 3 `GSelect` de Tipo/Estado/Ordenar filtran/ordenan el grid client-side; el toolbar no usa ningún `<select>` nativo.
- `MobCard` muestra el pill de estado superpuesto a la miniatura y el tipo de base + fecha relativa en el footer.
- TODOS los diálogos de la pantalla (existentes y nuevos) animan entrada/salida con `<Transition name="app-dialog">`.
- Suite completa (backend + frontend) en verde, sin hallazgos nuevos de lint/type-check, build sin errores.
- Verificación visual en vivo contra la referencia en un viewport equivalente.

## Hecho

Implementado sobre el preview interactivo con VoBo del PO (`https://claude.ai/code/artifact/bab000df-7d27-43a9-9ab6-503afefb361f`), con las tres condiciones explícitas cumplidas: sin componentes nativos (todo `GSelect`/`GMenu`/`AppDialog`), transiciones en todos los diálogos, transiciones de hover consistentes con el resto del rediseño (`cubic-bezier(0.16, 1, 0.3, 1)`, 220ms).

**Backend:**
- `V4__projects_description.sql`: columna `description` (nullable) en `projects`.
- `ProjectEntity`/`ProjectDetail`/`ProjectSummary`: campo `description` agregado. `RenameProjectRequest(name, description)` -- contrato "always explicit" (evita borrado silencioso de la descripción al renombrar solo el nombre).
- `ProjectService`: `rename()` normaliza y persiste ambos campos; `duplicate()` copia la descripción; `normalizeDescription()` (blank/null -> `null`, si no `.strip()`).
- Tests nuevos en `ProjectControllerTest` (editar descripción persiste, proyecto recién creado no tiene descripción). `SchemaMigrationReversibilityTest` actualizado (versión esperada `3` -> `4`).
- `docs/API.md` actualizado.

**Frontend:**
- `ProjectNameModal.vue`: textarea de descripción opcional en modo `rename`; título pasa a "Editar proyecto" (antes "Renombrar proyecto", ya no describe bien lo que hace el modal). `confirm` emite siempre `(nombre, descripción | null)`.
- `ProjectsDashboard.vue`/`HomeView.vue`: actualizados al nuevo contrato de `renameProject`/`ProjectNameModal` -- precargan `initialDescription` del proyecto y la reenvían sin cambios si el usuario no la toca (mismo criterio "always explicit" que el backend).
- 5 íconos nuevos (`IconHumanoid`/`IconArachnid`/`IconQuadruped`/`IconFlying`/`IconCustomBase`), mismo patrón `IconBase.vue` que el resto del design system.
- `MobCard.vue` reescrita: `GStatusPill` superpuesto a la miniatura (`aspect-ratio: 16/11`, como la referencia), footer con tipo de base (ícono + etiqueta) + `formatRelativeDate`.
- `ProjectDetail.vue` reescrito: breadcrumb, header con descripción, menú ⋮ del proyecto (`GMenu`, Duplicar/Eliminar -- mismo criterio de responsabilidad que `ProjectsDashboard.vue`: duplicar navega al nuevo proyecto, eliminar navega a `/projects`), toolbar con 3 `GSelect` (Tipo/Estado/Ordenar, sin dropdown "Todos" -- cada uno ya muestra su label como estado "sin filtro"), grid con la nueva `MobCard`, CTA "Nuevo mob" restilizada. Todos los diálogos (existentes + el nuevo `ConfirmDialog` de eliminar proyecto) envueltos en `<Transition name="app-dialog">`.
- Tests actualizados/nuevos: `ProjectNameModal.spec.ts` (textarea, emit de 2 argumentos), `ProjectsDashboard.spec.ts` (precarga y reenvío de descripción), `MobCard.spec.ts` (footer con tipo de base, pill superpuesto), `ProjectDetail.spec.ts` (breadcrumb, descripción, menú del proyecto, filtros/orden), `projectsApi.spec.ts` (contrato explícito de `description`). Fixtures de `ProjectSummary` en `AiMobProjectPickerDialog.spec.ts`/`HomeView.spec.ts`/`ProjectCard.spec.ts`/`RecentProjectCard.spec.ts` actualizados con el campo `description` nuevo (requerido por el tipo).

**Hallazgo real durante los propios tests**: con el nuevo menú ⋮ del proyecto conviviendo con el menú ⋮ de cada mob en la misma pantalla, varios tests existentes que hacían `wrapper.find('.g-menu__trigger')` a secas (sin escopar) empezaban a encontrar el trigger del menú del PROYECTO en vez del de un mob (el del proyecto aparece antes en el DOM, en el header). Detectado antes de que rompiera nada gracias al escaneo de tests existentes al tocar la pantalla; corregido escopando por `aria-label` específico (`Acciones de {nombre del mob}` vs. `Más acciones del proyecto`) en vez de por clase genérica -- mismo patrón recurrente ya documentado en tickets anteriores (selectores ambiguos cuando aparece un segundo elemento del mismo tipo en la pantalla).

**Estado final**: backend 437/437 tests en verde (Testcontainers); frontend 692/692 tests en verde, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.

**Verificación en vivo** (stack real: Postgres+MinIO+backend con `bootRun`+frontend con `npm run dev`, sobre un proyecto real con datos): comparado contra la referencia en `/projects/{id}` -- breadcrumb, header con lápiz de edición (título + descripción, textarea precargado y persistido correctamente al guardar), menú ⋮ del proyecto (Duplicar/Eliminar, confirmación con el nombre real del proyecto, Cancelar verificado que NO borra nada), los 3 `GSelect` de Tipo/Estado/Ordenar funcionando (filtro por tipo deja el grid vacío con el mensaje correcto cuando no hay coincidencias, orden "Nombre A-Z" reordena el grid en vivo), cero `<select>` nativos en el DOM (`document.querySelectorAll('select').length === 0`), `MobCard` con el pill de estado superpuesto a la miniatura y el tipo de base + fecha en el footer, hover con elevación/borde-accent en cards y CTA "Nuevo mob", y la transición de entrada del modal "Agregar mob" confirmada visualmente en cámara lenta (CSS inyectada a 2000ms vía `javascript_tool`, captura mid-fade). Hallazgo operativo (no de código): el backend/frontend que ya estaban corriendo en el entorno eran de ANTES de este ticket (sin `description` en la respuesta de `/api/projects`) -- se detectó comparando la respuesta real de la API antes de confiar en la UI, se mataron esos procesos viejos y se relanzaron con el código actual antes de verificar.

**Gate de autorización del requerimiento** (mismo criterio que tickets 071/072): implementación LOCAL únicamente -- sin commits, sin push, sin ramas, sin PR. Ticket queda en `in-process/` hasta que el PO revise el resultado en vivo y autorice el flujo git normal.

**Post-073 (pedido explícito del PO tras revisar el resultado en vivo)**: el mismo breadcrumb (diseño Y funcionalidad) se extiende a las pantallas que todavía no lo tenían:
- `ProjectsDashboard.vue` ("Mis proyectos"): agrega "Galgoth Studio > Mis proyectos" (2 niveles -- esta pantalla ya ES el segundo nivel de la jerarquía, sin nivel intermedio).
- `EditorHeader.vue` (compartido por las tabs Modelo/Textura del editor de mob, `MobEditor.vue`): reemplaza el breadcrumb estático anterior ("Galgoth Studio > {mob}", separador "›" a mano, sin links) por el mismo patrón con `IconChevron` y links reales, completando la jerarquía real que antes saltaba directo de la marca al mob sin pasar por el proyecto: "Galgoth Studio > Mis proyectos > {proyecto} > {mob}". `MobEditor.vue` ahora trae el proyecto (`getProject`, en paralelo con `getMob` vía `Promise.all`) solo para tener el nombre real que mostrar en ese nivel.
- Tests actualizados: `ProjectsDashboard.spec.ts` (nuevo test del breadcrumb), `EditorHeader.spec.ts` (reescrito para montar con un router de prueba real, ahora que usa `<router-link>` -- antes no lo necesitaba; nuevos tests de jerarquía completa y de qué niveles son links vs. texto), `MobEditor.spec.ts` (los `fetch` mockeados de cada test/describe ahora responden también a `GET /api/projects/p1`, llamada nueva de `onMounted`).
- Verificado en vivo en las 3 pantallas: `/projects` (2 niveles), `/projects/{id}/mobs/{mobId}/edit` en ambas tabs (4 niveles, el mismo breadcrumb persiste al cambiar de tab), y navegación real click a click desde el editor hasta el detalle del proyecto real.
- Estado final: 695/695 tests frontend en verde, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.
