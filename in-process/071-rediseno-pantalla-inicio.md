# 071 — Rediseño de la pantalla Inicio (fidelidad visual estricta)

## Objetivo
Reconstruir la pantalla "Inicio" para que coincida visualmente con la referencia entregada por el Product Owner (`requerimientos/1_req_rediseno_inicio/GALGOTH_STUDIO_REQ_REDISENO_INICIO_PROMPT.md` + `assets/rediseno.png`), usando los assets reales provistos (`button_mob_ia.png`/`button_nuevo_proyecto.png`). Hasta este ticket, "Inicio" y "Mis proyectos" eran la misma pantalla (`ProjectsDashboard.vue`, simplificación consciente del ticket 021) -- este ticket le da a Inicio su propia pantalla real.

Flujo seguido: análisis previo (sin tocar código) → 3 decisiones de arquitectura/alcance resueltas explícitamente con el PO → preview interactivo (Artifact) con los assets reales → VoBo del PO sobre el preview (*"esta perfecto, doy vobo implementalo exactamente como está"*, tras un ajuste pedido sobre las cards de "Proyectos recientes") → implementación.

## Decisiones del Product Owner (previas a implementar)
1. **"Continuar trabajando" (mobs recientes cruzando TODOS los proyectos)**: nuevo endpoint backend dedicado (`GET /api/mobs/recent`), no agregación N+1 client-side, no fixtures.
2. **Labels de estado (`GStatusPill`)**: traducidos a español GLOBALMENTE (Ready→Listo, In progress→En progreso, Draft se mantiene) -- afecta también `ProjectDetail.vue`/`MobCard.vue`, no solo Inicio.
3. **"Ver todos"** (ambas secciones): navega a `/projects` (Mis proyectos, listado completo). No existe todavía una vista de "todos los mobs" separada.

## Alcance

### Incluye
- Backend: `GET /api/mobs/recent?limit=N` (`MobRecentController`/`MobService.listRecentAcrossProjects`/`MobRepository.findRecentAcrossProjects`, nuevo DTO `RecentMobSummary` con `projectId`). Excluye mobs soft-deleted y mobs de proyectos soft-deleted. `limit` inválido/ausente cae a un default (3, tope 20), nunca 400.
- Frontend: `HomeView.vue` (nueva pantalla en `/`), `RecentMobCard.vue`/`RecentProjectCard.vue` (variantes horizontales de `MobCard`/`ProjectCard`, sin tocar los originales), `HomeSectionHeader.vue`.
- Router: `/` deja de apuntar a `ProjectsDashboard.vue` -- ahora `HomeView.vue`. `/projects` sin cambios (`ProjectsDashboard.vue`, listado completo).
- `GSidebar`: "Inicio" deja de ser sinónimo de "Mis proyectos" en TODAS las pantallas que lo manejaban así (`ProjectsDashboard`, `ProjectDetail`, `AiMobWizard`, `MobEditor`, `ExportScreen`) -- ahora navega a `/`.
- `GStatusPill.vue`: labels traducidos (decisión #2).
- Assets reales copiados a `frontend/src/assets/home/` (PNG original, sin recomprimir ni alterar).
- Menús ⋮ de ambas secciones de Inicio 100% funcionales (Renombrar/Exportar/Eliminar para mobs; Renombrar/Duplicar/Exportar(disabled)/Eliminar para proyectos), reusando exactamente los mismos diálogos/endpoints que `ProjectDetail.vue`/`ProjectsDashboard.vue`.
- Estados vacíos (sin mobs / sin proyectos) con CTA, según el requerimiento.
- Postman: nueva carpeta "Mobs recientes cruzando proyectos (ticket 071)".
- Docs: `docs/API.md` actualizado.

### No incluye
- Rediseño de "Mis proyectos" (`ProjectsDashboard.vue`) -- sigue exactamente igual, solo cambia de ruta implícita (ya no es alias de "/").
- Responsive/mobile más allá de un breakpoint básico ya existente en el patrón de la pantalla (desktop-first, según la regla 11 del requerimiento).
- Cambiar el criterio de "Ver todos" a una vista de "todos los mobs" nueva (decisión #3: pospuesto, no pedido).

## Criterios de aceptación (TDD)
- `GET /api/mobs/recent` devuelve los N mobs más recientes CRUZANDO proyectos, ordenados por `updatedAt` descendente, excluyendo soft-deleted (mob y proyecto). `limit` opcional respeta el valor pedido; inválido/ausente usa el default.
- Inicio (`/`) muestra: saludo, las 2 CTA (IA con fondo `button_mob_ia.png`, ~58%; Proyecto con fondo `button_nuevo_proyecto.png`, ~42%), "Continuar trabajando" (mobs reales del endpoint nuevo) y "Proyectos recientes" (top 2 de `listProjects()`, ya ordenado por el backend).
- Clic en "Empezar ahora" abre el flujo IA existente (selector de proyecto → wizard). Clic en "Crear proyecto" abre el modal de creación existente.
- Clic en un mob/proyecto reciente navega correctamente usando su `projectId` real (un mob de otro proyecto no navega mal).
- Menús ⋮ de ambas secciones son funcionales de punta a punta (no decorativos): Renombrar/Eliminar (mobs), Renombrar/Duplicar/Eliminar (proyectos), con los mismos diálogos y bloqueo de doble submit que las pantallas ya existentes.
- "Ver todos" de ambas secciones navega a `/projects`.
- Sidebar: "Inicio" navega a `/` (ya no a `/projects`) desde CUALQUIER pantalla que lo maneje.
- `GStatusPill` muestra "Listo"/"En progreso"/"Draft" en todas las pantallas que lo usan.
- Estados vacíos con CTA cuando no hay mobs/proyectos.
- Suite completa (backend + frontend) en verde, sin hallazgos nuevos de lint/type-check, build sin errores.
- Verificación visual en vivo contra `rediseno.png` en un viewport equivalente.

## Hecho

Implementado exactamente sobre el preview interactivo con VoBo del PO (`https://claude.ai/code/artifact/8d533823-566f-4da5-91de-6247ac58b7a4`, *"esta perfecto, doy vobo implementalo exactamente como está"* -- tras un primer ajuste pedido sobre la superposición de texto en las cards de "Proyectos recientes" del propio preview).

**Backend:**
- `MobRepository.findRecentAcrossProjects(Pageable)` -- JPQL con subquery para excluir mobs de proyectos soft-deleted (sin relación JPA entre `MobEntity`/`ProjectEntity`, solo FK crudo).
- `MobService.listRecentAcrossProjects(limit)`, DTO nuevo `RecentMobSummary` (`MobSummary` + `projectId`).
- `MobRecentController` -- `GET /api/mobs/recent?limit=N`, ruta literal que no colisiona con `/api/mobs/{mobId}` (Spring resuelve literales antes que variables). `limit` inválido/ausente cae a un default (3, tope 20) en vez de 400.
- `MobRecentControllerTest` (Testcontainers + Postgres real + MockMvc, 5 tests): orden cross-proyecto, `limit`, default, `limit` inválido, exclusión de soft-deleted (mob y proyecto).
- `docs/API.md` y colección Postman actualizados.

**Frontend:**
- `HomeView.vue` (nueva pantalla en `/`), `RecentMobCard.vue`/`RecentProjectCard.vue` (variantes horizontales, `ProjectCard.vue`/`MobCard.vue` sin tocar), `HomeSectionHeader.vue`.
- Router: `/` → `HomeView.vue`; `/projects` (`ProjectsDashboard.vue`, "Mis proyectos") sin cambios de contenido.
- `GSidebar`: "Inicio" deja de ser sinónimo de "Mis proyectos" en las 5 pantallas que lo manejaban así (`ProjectsDashboard`, `ProjectDetail`, `AiMobWizard`, `MobEditor`, `ExportScreen`) -- ahora navega a `/`.
- `GStatusPill.vue`: labels traducidos a español (Ready→Listo, In progress→En progreso, Draft sin cambio) -- global, afecta también `ProjectDetail`/`MobCard`.
- Assets reales (`button_mob_ia.png`/`button_nuevo_proyecto.png`) copiados sin alterar a `frontend/src/assets/home/`.
- Menús ⋮ de ambas secciones 100% funcionales (mismos diálogos/endpoints que las pantallas ya existentes) -- confirmado en vivo exportando un mob real desde "Continuar trabajando".
- Tests nuevos: `HomeView.spec.ts`, `RecentMobCard.spec.ts`, `RecentProjectCard.spec.ts`, `HomeSectionHeader.spec.ts`. Tests actualizados: `ProjectsDashboard.spec.ts` (sidebar), `GStatusPill.spec.ts`/`MobCard.spec.ts`/`ProjectDetail.spec.ts` (labels ES).

**Hallazgo real durante el propio desarrollo (test-first evitó que llegara a producción)**: `RecentProjectCard.vue` invertía el orden de los parámetros de `handleAction(projectId, actionKey)` contra el call site `handleAction(project.id, key)`, copiado mal desde `ProjectCard.vue` -- el menú ⋮ de "Proyectos recientes" emitía `action` con la key y el id INVERTIDOS, rompiendo silenciosamente Renombrar/Duplicar/Eliminar. Lo destapó `RecentProjectCard.spec.ts` (test de "Renombrar" con el orden esperado del evento) antes de tocar el navegador.

**Verificación en vivo** (`scripts/e2e-up.sh`, stack real -- Postgres+MinIO+backend+frontend): comparado contra `rediseno.png` en un viewport de 1672×941 (mismo tamaño de la referencia). Layout, proporciones de las 2 cards hero (~58/42), badges, cards horizontales, sidebar (selected state) y tipografía coinciden. Con datos reales (un proyecto/mob `TEST` preexistente en la BD local): "Continuar trabajando"/"Proyectos recientes" cargan del backend real, el menú ⋮ de un mob real navegó a "Exportar modelo" end-to-end, "Ver todos" navegó a `/projects` (Mis proyectos, sin tocar), y "Inicio" en el sidebar volvió a `/` correctamente desde ahí.

**Feedback del PO en vivo (post-verificación)**: el menú ⋮ se quedaba abierto para siempre al hacer click afuera. Causa raíz: `GMenu.vue` (componente COMPARTIDO por toda la app, no específico de Inicio) nunca tuvo un listener de "click afuera" ni Escape -- gap real desde su creación (ticket 021), simplemente más visible en Inicio por el espacio vacío alrededor de las cards. Corregido con el mismo patrón `rootRef` + `handleDocumentClick` ya establecido por `GSelect.vue` (ticket 058), más cierre con Escape (devuelve el foco al trigger). 2 tests nuevos en `GMenu.spec.ts`. Confirmado en vivo: abrir el menú y clickear afuera ahora sí lo cierra.

**Feedback del PO en vivo (ajustes de layout de `RecentMobCard.vue`)**:
1. El `⋮` debía quedar apilado DEBAJO del pill de estado (no al lado) -- `.recent-mob-card__side` pasa de fila a columna (pill arriba, menú abajo), reemplazando el patrón de menú superpuesto absoluto que usan `MobCard`/`ProjectCard` (acá no aplica: el layout horizontal necesita que ambos convivan como grupo, no superpuestos).
2. Pill y `⋮` se veían "muy juntos" y el "contenedor" del `⋮` se veía "muy grande": se agregó `gap` explícito entre ambos. Verificado en vivo que el "contenedor grande" era en realidad el hover de 40×40px del botón -- EL MISMO hit target mínimo que usa cualquier botón de ícono en toda la app (`IconButton`, toggle del sidebar, cualquier otro `GMenu`), no algo nuevo de esta card. El PO confirmó explícitamente dejarlo así (no reducir el estándar de accesibilidad compartido, ni acá ni globalmente).

**Feedback del PO en vivo (transición + anti-desborde de `GMenu.vue`)**: pidió (1) transición de entrada/salida del menú ⋮ y (2) que nunca desborde el viewport -- si no hay espacio abajo, que se abra hacia arriba. Implementado en `GMenu.vue` (compartido por toda la app):
- `rendered` (existe en el DOM, incluye mientras anima la salida) separado de `isOpen` (estado visual + intención lógica) -- mismo patrón "animar la salida antes de desmontar" que `GDrawer.vue` (ticket 069), con `CLOSE_ANIMATION_MS=140` sincronizado a la transición CSS, y respeto a `prefers-reduced-motion` (cierre instantáneo).
- `computePlacement()` mide `getBoundingClientRect()` del trigger y de la lista contra `window.innerHeight` justo antes de mostrarse, y decide `--above`/`--below`. Verificado en vivo en una ventana angosta: el menú de la última card ("Proyectos recientes", sin espacio debajo) se abrió hacia arriba en vez de desbordar.
- 2 tests nuevos en `GMenu.spec.ts` (apertura hacia arriba con espacio insuficiente simulado; comportamiento de cierre con `vi.useFakeTimers()`, mismo precedente que `GDrawer.spec.ts`).
- Efecto secundario esperado: el ítem "Eliminar" del menú queda montado ~140ms animando su salida después de seleccionarse, coincidiendo en texto con el botón "Eliminar" del `ConfirmDialog` que se abre a la vez -- 3 tests existentes (`ProjectDetail`/`ProjectsDashboard`/`HomeView`) escopados a `.app-dialog` para no ambigüedad.

**Feedback del PO en vivo (transiciones más suaves + ancho de cards)**:
- Cards/botones nuevos de Inicio (hero cards, CTA, `RecentMobCard`/`RecentProjectCard`, "Ver todos") pasan de `var(--transition-fast)` (120ms, la curva por defecto de controles pequeños del resto de la app) a 220ms con `cubic-bezier(0.16, 1, 0.3, 1)` (misma curva "ease-out" ya usada por `GDrawer.vue`) -- solo en estos componentes nuevos, sin tocar el timing compartido del resto de la app. `RecentMobCard`/`RecentProjectCard` además ganan el mismo hover con lift + sombra que ya tenían las hero cards (antes solo cambiaban `border-color`, sin movimiento).
- `.home__mob-row`/`.home__project-row`: el `minmax(..., 1fr)` de la grilla hacía que una sola card (caso real actual, un solo proyecto/mob de prueba) se estirara a ocupar TODO el ancho disponible, viéndose desproporcionada. Cambiado a un tope fijo (`minmax(260px, 360px)` / `minmax(320px, 420px)`) -- las cards crecen hasta ese tope y el resto de la fila queda vacío, en vez de estirarse; con más cards, la grilla sigue envolviendo (wrap) normalmente.

**Feedback del PO en vivo (transición de diálogos + `<select>` nativo)**:
1. Los diálogos (`AppDialog.vue`, compartido por `ConfirmDialog`/`ProjectNameModal`/`MobRenameDialog`/`AiMobProjectPickerDialog`/`AddMobModal`) abrían/cerraban sin transición. Como el `v-if` real que monta/desmonta cada diálogo vive en cada PANTALLA (no en `AppDialog.vue`), la animación de salida no puede resolverse dentro del propio componente sin acoplarlo a la lógica de éxito/error de cada caller -- se resuelve con `<Transition name="app-dialog">` envolviendo cada `v-if` (Vue anima la salida sin importar POR QUÉ se desmontó: cancelar, Escape, o un guardado exitoso). Las clases de la transición viven en un `<style>` SIN `scoped` dentro de `AppDialog.vue` (un `scoped` normal no las alcanzaría de forma confiable, al aplicarse desde fuera del árbol del componente) -- contrato del design system para cualquier pantalla que use estos diálogos. Aplicado en los 5 diálogos que monta `HomeView.vue`; el resto de pantallas (`ProjectsDashboard`/`ProjectDetail`/`AiMobWizard`) queda sin animar por ahora (mismo criterio ya aplicado a las transiciones de hover: alcance de este ticket es Inicio, no una pasada global -- extensible después si se pide).
2. `AiMobProjectPickerDialog.vue` (el selector "¿En qué proyecto?") usaba un `<select>` nativo con un glitch visual real (chrome inconsistente del navegador/SO sobre el tema oscuro) -- reemplazado por `GSelect.vue`, el componente que el design system ya tiene para esto exactamente (ticket 058, que "prohíbe explícitamente `<select>` nativo"). Memoria nueva guardada (`no-native-form-controls`) para no repetir este gap en el resto de la app -- queda pendiente, señalado explícitamente y no en silencio, el mismo `<select>` nativo de `ConfigurationStep.vue` (fuera del alcance de Inicio).
3. Tests de `AiMobProjectPickerDialog.spec.ts` actualizados a la interacción real de `GSelect` (abrir + click en opción, en vez de `.setValue()` sobre un `<select>`).

**Feedback del PO en vivo (`GSelect.vue` recortado dentro del diálogo)**: dentro de `.app-dialog__body` (que tiene `max-height:60vh; overflow-y:auto`, necesario para diálogos con contenido genuinamente largo), la lista desplegada de `GSelect` se recortaba y generaba su PROPIO scroll interno en vez de desbordar por encima del diálogo como corresponde a un popover real. Causa: `.g-select__list` era `position: absolute` -- su "contenedor de recorte" para `overflow` era el ancestro scrolleable más cercano (`.app-dialog__body`), no el viewport. Corregido con el mismo criterio ya aplicado a `GMenu.vue`: `position: fixed` + coordenadas (`top`/`left`/`width`) medidas en vivo contra el trigger real (`getBoundingClientRect`), que escapa CUALQUIER ancestro con `overflow` sin necesidad de teleport (un `<dialog>` con `showModal()` vive en el "top layer" del navegador -- un teleport a `body` quedaría VISUALMENTE DETRÁS del propio diálogo, hallazgo real evaluado antes de implementar). De paso, mismo cálculo de espacio disponible que `GMenu.vue` (arriba/abajo según el viewport). 2 tests nuevos en `GSelect.spec.ts`.

**Feedback del PO en vivo ("Nuevo proyecto" tiene el mismo problema" -- el input se ve "roto")**: primer diagnóstico (transición con `scale()`, ver más abajo) resultó ser una mejora defensiva real pero NO la causa -- el PO insistió correctamente ("creo que no me di a entender") en que el problema persistía en estado asentado, con captura de otro diálogo más (`AiMobProjectPickerDialog.vue`) mostrando la misma costura. Diagnóstico real esta vez, confirmado con `getComputedStyle`/`:matches(':focus-visible')` en vivo (`javascript_tool`) en vez de intuir desde capturas: `--focus-ring` (`reset.css`/`tokens.css`) usa `0 0 0 2px var(--bg), 0 0 0 4px var(--accent)` -- el anillo interno de 2px asume que el control enfocado SIEMPRE está parado sobre `--bg` (fondo de página, `#0b0f14`). Dentro de un diálogo, el fondo real es `--panel` (`#111820`, un tono distinto) -- ese anillo de 2px no se mezcla con el fondo real, dejando una costura visible entre el borde del input y el anillo mint. Es un bug del TOKEN global, no de ningún input en particular -- afecta cualquier control enfocado que viva sobre `--panel`/`--surface` en vez de `--bg` directo (la mayoría de los diálogos/cards de la app), no solo Inicio.

Corregido en `tokens.css`: el anillo interno pasa de `var(--bg)` (color adivinado) a `transparent` (dejar ver lo que sea que haya detrás, correcto en cualquier fondo sin necesidad de adivinarlo). Verificado en vivo en AMBOS diálogos reportados (`ProjectNameModal`/`AiMobProjectPickerDialog`), comparando el mismo recorte de esquina antes/después del cambio: costura visible antes, arco limpio después.

**Segundo round del mismo hallazgo -- "se ve un borde... como si excediera el tamaño y se recortara"**: el fix de arriba resolvió la costura de color pero NO el recorte real que el PO seguía viendo. Diagnóstico confirmado con `getBoundingClientRect()` en vivo: `.app-dialog__body` (`overflow-y:auto`, que el navegador computa como `overflow-x:auto` también, no `visible`) no tenía padding propio -- el input llegaba EXACTAMENTE al borde de recorte de `body` (`inputLeft === bodyLeft`, `inputRight === bodyRight`, 0px de margen). El `--focus-ring` (`box-shadow`) se extiende 6px hacia afuera del borde -- sin ningún margen donde existir, ese `box-shadow` se recortaba en los costados exactos donde toca el borde de `body`. Corregido con `padding:6px` + `margin:-6px` en `.app-dialog__body` (el padding le da a esos 6px un lugar donde existir dentro del área de recorte; el margin negativo a juego cancela el desplazamiento visual, mismo layout percibido que antes). Verificado en vivo con `getBoundingClientRect()` (gap de 6px a cada lado entre el input y el borde de recorte de `body`, antes 0px) y visualmente en ambos diálogos: anillo completo en los 4 lados, sin recorte.

**Estado final**: backend 100% de tests en verde (Testcontainers); frontend 658/658 tests en verde, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.

**Gate de autorización del requerimiento** (`requerimientos/1_req_rediseno_inicio/...md`): implementación LOCAL únicamente -- sin commits, sin push, sin ramas, sin PR, según lo pedido explícitamente. Ticket queda en `in-process/` (no se mueve a `done/`) hasta que el PO revise el resultado en vivo y autorice el flujo git normal (rama → PR → CI verde → self-merge).
