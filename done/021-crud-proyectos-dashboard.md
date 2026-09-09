# 021 — CRUD de proyectos + dashboard

**Milestone:** M3 · **Depende de:** 002, 003 · **HUs:** HU-01, HU-02

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Épica A). Crear/listar proyectos, con tarjetas mostrando nombre, hasta 3 miniaturas de mob y acciones Rename/Duplicate/Export/Delete.

## Criterios de aceptación (TDD)
- Dado un nombre válido en "Nuevo proyecto", cuando se confirma, entonces se crea el proyecto y se redirige a su detalle.
- Dado un intento de crear un proyecto sin nombre, cuando se confirma, entonces el sistema lo impide con un mensaje de validación claro.
- Dado un proyecto con más de 3 mobs, cuando se muestra su tarjeta, entonces aparece el indicador "+N".
- Dado el menú de acciones de una tarjeta, cuando se despliega, entonces ofrece Rename, Duplicate, Export, Delete.

## Hecho

**Decisiones del Product Owner (VoBo explícito vía `AskUserQuestion`, 2026-09-08):**
1. **"Export"** del menú de acciones: se muestra deshabilitado con su razón visible (no un simple tooltip -- mismo criterio de accesibilidad de `GTabs`), en vez de omitirse -- no existe ningún endpoint de exportación de proyecto expuesto todavía.
2. **"Duplicate"**: copia PROFUNDA -- el proyecto y TODOS sus mobs, con su historial completo de `mob_revisions` y su `mob_drafts` actual (si tiene), no una copia superficial vacía.

Backend (`backend/src/main/java/com/galgothstudio/backend/project/`):
- **`ProjectEntity`/`ProjectRepository`** (nuevo) + extensiones a `MobRepository`/`MobRevisionRepository` (ticket 020) para soportar list-by-project y duplicación.
- **`ProjectService`**: `create`/`list`/`get`/`rename`/`softDelete`/`duplicate`. Delete es soft-delete (`projects.deleted_at`, ya en el esquema desde el ticket 003) -- un proyecto eliminado se trata igual que "no existe" en cualquier consulta posterior. Duplicate reconstruye cada mob del original con nuevo id y copia TODA su cadena de `mob_revisions` (mismo `revision_number`/`model_jsonb`) más su `mob_drafts` si existe, en una única transacción. Limitación documentada, no un descuido: el campo `mobId` DENTRO del JSON de cada revisión/draft copiado no se reescribe al nuevo id -- es puramente informativo (ningún código de negocio lo cruza contra la fila real, ver ticket 020), así que reescribirlo no aporta nada funcional.
- **`ProjectController`** (`GET`/`POST /api/projects`, `GET`/`PATCH`/`DELETE /api/projects/{id}`, `POST /api/projects/{id}/duplicate`) + extensión de `ApiExceptionHandler` (`PROJECT_NOT_FOUND`, `INVALID_PROJECT_NAME`).
- **`WebConfig`** (nuevo): CORS para el origen local de Vite (`docs/definiciones/galgoth-studio-mvp.md` §9 confirma CORS como la estrategia elegida, no un proxy de Vite).
- **Tests**: 13 nuevos (`ProjectControllerTest`, Testcontainers + MockMvc + Postgres real, uno por AC + CORS + duplicación profunda verificada contra la BD real) -- 99 tests totales en backend, todos en verde.

Frontend (`frontend/src/projects/`, primera pantalla productiva real -- reemplaza `HomePlaceholder.vue`):
- **`ProjectsDashboard.vue`** (rutas `/` y `/projects`): saludo + 2 CTAs (mockup 01) + grid de proyectos + modal de creación + confirmación de borrado explícita (sin `confirm()` nativo, mismo patrón que el ticket 018).
- **`ProjectCard.vue`**: hasta 3 miniaturas + indicador "+N" (AC #3) + `GMenu` de acciones.
- **`GMenu.vue`** (design-system, nuevo): primer menú desplegable del proyecto -- ítems deshabilitados muestran su razón como texto visible.
- **`ProjectNameModal.vue`**: un solo componente para Nuevo proyecto/Rename.
- **`ProjectDetailPlaceholder.vue`** (ruta `/projects/:id`): mínimo a propósito -- solo nombre + conteo de mobs, para que la redirección de AC #1 tenga un recurso real; HU-04/ticket 022 lo reemplaza con el grid completo.
- **`projectsApi.ts`**: primer cliente HTTP real del frontend (`fetch` directo, sin proxy de Vite -- CORS es la estrategia elegida).
- **Tests**: 45 nuevos (`projectsApi`, `GMenu`, `ProjectNameModal`, `ProjectCard`, `ProjectsDashboard`, `ProjectDetailPlaceholder`) -- 179 tests totales en frontend, todos en verde. `vue-tsc -b`, `npm run lint`, `npm run build` en verde. Cobertura del proyecto: 95.31%.

### Verificación en vivo (Claude in Chrome, backend real vía `./gradlew bootRun` + Postgres real vía `docker compose`, no solo tests)

Crear proyecto → redirección real a `/projects/{uuid}` (AC #1) confirmada; Rename, Duplicate (copia profunda verificada contra la API real -- nueva fila con `" (copia)"`, mismo mob, mismo historial completo de revisiones) y Delete (soft-delete, desaparece del listado) confirmados desde el menú de una tarjeta; indicador "+2" confirmado sembrando 5 mobs directo en Postgres para un proyecto (AC #3); menú de acciones con las 4 opciones, "Export" deshabilitado con su razón visible (AC #4); navegación del sidebar confirmada; sin errores de consola.

### Hallazgos reales encontrados en el camino (ambos en la verificación en vivo, no en tests -- corregidos en el mismo ticket)

- **Fechas serializando como epoch numérico, no ISO-8601**: el `ObjectMapper` de `JacksonConfig` (construido a mano desde el ticket 020, para sortear el cambio de Spring Boot 4 a Jackson 3 como default) tenía `WRITE_DATES_AS_TIMESTAMPS` habilitado -- el default "puro" de Jackson, que la autoconfiguración de Spring Boot normalmente desactiva, perdida al construir el `ObjectMapper` a mano. Un `Instant` serializaba como `1788924939.428339` en vez de `"2026-09-09T03:35:39.428339Z"`. **Esto también afectaba, retroactivamente, a los endpoints ya mergeados del ticket 020** (`AutosaveResponse.updatedAt`, `DraftView.updatedAt`) -- nunca detectado porque esos tests solo verificaban las fechas vía SQL crudo (`updated_at::text`), nunca inspeccionando el tipo real del JSON de respuesta. Corregido con `.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` + un test de regresión explícito (`createdAt_y_updatedAt_serializan_como_texto_ISO8601...`).
- **El `@select` del `GSidebar` no navegaba pese a que el comentario de cabecera ya lo afirmaba**: tanto `ProjectsDashboard.vue` como `ProjectDetailPlaceholder.vue` originalmente pasaban `@select="() => {}"` (no-op) a `GSidebar`, aunque el comentario de `ProjectsDashboard.vue` decía explícitamente "tanto Inicio como Mis proyectos en el sidebar apuntan aquí". Clic en "Mis proyectos" desde el detalle de un proyecto no hacía nada -- detectado al navegar de verdad en el navegador, no por ningún test (los tests montaban los componentes sin ejercitar el sidebar). Corregido cableando ambas claves (`home`/`projects`) a `router.push('/projects')`, con tests nuevos que lo cubren.

### Hallazgos reales de Sonar (verificados manualmente contra `sonarqube-db`, gap conocido desde el ticket 008 -- Jenkins reportó verde en el primer intento pese a que AMBOS gates estaban en `ERROR`)

- **Backend, `S107`**: el constructor de `MobEntity` tenía 9 parámetros (máximo 7). Corregido migrando la entidad al estilo JavaBean (constructor solo con `id` + setters para el resto) -- más idiomático para una entidad JPA de todos modos, y deja la entidad lista para que el ticket 022 la use al crear mobs reales.
- **Frontend, `S6821`**: `role="none"` en `GMenu.vue` -- "none" no es un rol ARIA válido (el token correcto es `"presentation"`). Corregido.
- **Frontend, `S6819`** (x2): `role="dialog"` sobre un `<div>` en `ProjectNameModal.vue` y `role="button"` sobre un `<div>` en `ProjectCard.vue` -- Sonar exige el elemento nativo en vez de emular el rol con ARIA. Ambos se reescribieron de verdad (no se suprimió la regla):
  - `ProjectNameModal.vue` ahora usa `<dialog>` nativo (`showModal()`/`::backdrop`) -- gana gratis manejo de foco y cierre con Escape, que la versión con `<div>` no tenía (verificado en vivo: el input recibe foco automático al abrir, Escape cierra). **Hallazgo de entorno**: jsdom (v30, la más reciente a la fecha) todavía no implementa `HTMLDialogElement.showModal()/close()` -- se agregó un polyfill mínimo en los tests afectados (togglear el atributo `open`), documentado como limitación de jsdom, no de producción.
  - `ProjectCard.vue` se reestructuró: el `<button>` que abre el detalle ya no envuelve el menú de `GMenu` (contenido interactivo dentro de un `<button>` es HTML inválido) -- el menú ahora es un hermano superpuesto visualmente en la esquina, sin necesitar `@click.stop`.

### Alcance no cubierto, documentado explícitamente (no un descuido)

- El detalle de proyecto es un placeholder mínimo -- el grid completo de mobs, búsqueda y estado por mob es HU-04 (ticket 022).
- La tarjeta CTA "Crear un mob con IA" del mockup se muestra pero deshabilitada -- sin pipeline de IA todavía (épica futura).
- El mockup separa "Inicio" (saludo + "Proyectos recientes" + link "Ver todos") de "Mis proyectos" (listado completo) -- este ticket implementa un único dashboard con el listado COMPLETO para ambas entradas del sidebar, ya que el AC de este ticket solo describe la segunda vista.
