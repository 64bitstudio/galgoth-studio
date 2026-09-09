# 032 — Pantalla de exportación (estado FMM + draft sin guardar)

**Milestone:** M6 · **Depende de:** 013, 020 · **HUs:** HU-19

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (mockup 11, HU-19 y Addendum de implementación). Implementar la pantalla de exportación mostrando el estado de compatibilidad FMM (013) y, cuando el draft tiene cambios sin guardar, ofreciendo explícitamente **[Guardar y exportar]** (primaria), **[Exportar última versión guardada]**, **[Cancelar]**.

## Criterios de aceptación (TDD)
- Dado que exporto un mob, cuando se genera el archivo, entonces el exportador usa `mob_revisions` (última guardada) — nunca el draft en curso.
- Dado cambios en el draft sin guardar (`draft_version` más nuevo que la última revisión), cuando se abre la pantalla de exportación, entonces se muestran las tres acciones: Guardar y exportar / Exportar última versión guardada / Cancelar.
- Dado "Guardar y exportar", cuando se confirma, entonces primero se crea la revisión (mismo mecanismo de 020) y después se exporta esa revisión recién creada.
- Dado "Exportar última versión guardada", cuando se confirma, entonces se exporta sin tocar el draft actual.
- Dado el resultado de 013, cuando se muestra en pantalla, entonces cada error de compatibilidad es específico y accionable.

## Hecho

Implementado end-to-end, backend + frontend, verificado en vivo contra los 4 estados reales posibles de la pantalla.

**Backend** (`backend/.../project/export/`, nuevo paquete): `MobExportService.getStatus` calcula `hasUnsavedChanges` comparando el draft actual contra la última revisión guardada por igualdad de valor de `MobProjectModel` (mismo mecanismo que el dirty-check de autosave, 020) y `fmmCompatible`/`fmmIssues` (013) SIEMPRE contra esa última revisión guardada -- decisión confirmada explícitamente con el PO vía `AskUserQuestion` (el AC deja ambiguo contra qué modelo calcular el panel de compatibilidad cuando "Guardar y exportar" y "Exportar última versión guardada" pueden producir archivos distintos). `MobExportService.exportBbmodel` (AC #1) lee exclusivamente `mob_revisions`, nunca `mob_drafts`. `MobExportController`: `GET /api/mobs/{mobId}/export/status` + `GET /api/mobs/{mobId}/export/bbmodel` -- "Guardar y exportar" NO es un endpoint compuesto: el frontend orquesta `POST /api/mobs/{mobId}/revisions` (020, "Guardar", ya existente) seguido de `GET .../export/bbmodel`, reutilizando el mecanismo de Guardar tal cual en vez de duplicarlo (AC #3). 201 tests backend (+7).

**Bug real encontrado por testing (no por Sonar)**: `ResponseEntity<byte[]>` con `Content-Type: application/json` hace que Spring prefiera `MappingJackson2HttpMessageConverter` sobre `ByteArrayHttpMessageConverter` -- el `.bbmodel` llegaba codificado como string base64 dentro de un JSON, no como el archivo crudo. Fix: `application/octet-stream` (también más correcto semánticamente: es una descarga de archivo, no una respuesta JSON para consumir programáticamente).

**Frontend** (`frontend/src/editor/`): `mobExportApi.ts` (nuevo) + `ExportScreen.vue` (nuevo, ruta propia `/projects/:projectId/mobs/:mobId/export`, enlazada desde un botón "Exportar" en `MobEditor.vue`). **Decisión de diseño (no en el AC original)**: reutiliza el THUMBNAIL ya generado por 023 (imagen estática) en vez de un viewport 3D interactivo -- más simple, fiel al mockup 11 (que muestra una imagen sin controles de cámara), y evita por completo el problema del singleton de `ThreeViewportService` que 031 tuvo que resolver explícitamente con un toggle. 4 estados reales según `ExportStatus` (sin contenido / solo "Guardar y exportar" / un solo botón "Exportar .bbmodel" / las 3 acciones del AC), cada uno verificado en vivo por separado. "Guardar y exportar" pide confirmación en dos pasos (mismo patrón que `ResultStep.vue`/`HierarchyBoneNode.vue`) antes de crear una revisión real; las acciones de solo lectura son de un solo clic. 309 tests frontend (+12: 4 `mobExportApi.spec.ts` + 7 `ExportScreen.spec.ts` + 1 nuevo en `MobEditor.spec.ts`). `npm run lint`/`vue-tsc -b`/`npm run build` en verde.

**Decisión de alcance, no un gap silencioso**: "Validar modelo" del mockup no tiene un botón propio -- el panel de compatibilidad ya se calcula en vivo y muestra cada error específico (AC #4) apenas se carga la pantalla; un botón separado solo repetiría la misma llamada sin agregar nada real, y el AC no pide una acción de validación independiente de exportar.

**Verificado en vivo, los 4 estados reales** (Claude in Chrome, backend+Postgres+MinIO reales, providers mock -- ningún AI involucrado en este ticket): (1) mob sin draft ni revisión ("mob1") → mensaje explícito "todavía no tiene ningún contenido", sin botón de exportar. (2) mob con draft pero sin ninguna revisión ("Serpentario", autosave real vía API) → solo "Guardar y exportar" + "Cancelar", sin panel FMM. (3) mob con revisión real y sin cambios sin guardar ("Carcomido Resultado Real", el rig de 14 cuboides de 030) → un solo botón "Exportar .bbmodel" + "Modelo listo para usar en tu servidor" real; confirmado vía `curl` que el endpoint devuelve un `.bbmodel` real (`format_version:"5.0"`, 14 elementos con los nombres reales del rig, `Content-Disposition` con el filename sanitizado correcto). (4) mob con revisión Y draft divergente ("m1", autosave real con contenido distinto al guardado) → las 3 acciones reales del AC; "Cancelar" navegó de vuelta al editor real, mostrando el draft sin ningún cambio perdido. `mobId` inexistente → "Este mob no existe. Volver al proyecto" explícito. Sin errores de consola.

**Docs**: `docs/API.md` (nueva sección, corrige la ruta "prevista" de `POST` a `GET` con el porqué), `docs/ARQUITECTURA.md`, `docs/COMPONENTES.md`. Colección Postman actualizada con los 2 endpoints nuevos.
