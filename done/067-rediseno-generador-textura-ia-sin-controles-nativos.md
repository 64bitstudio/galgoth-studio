# 067 — Rediseño del Generador de textura (IA): sin controles nativos + reemplazo de referencia

## Objetivo

Feedback directo del PO sobre la pantalla `TextureAiGeneratorScreen.vue` (055/059-066), con 2 capturas de pantalla: "hay varios detalles, no se aplican los cambios generados por IA, segundo quisiera poder agregar otra imagen en la referencia cuando se va a generar la imagen, respecto a los estilos no uses componentes nativos de HTML, usa radio buttons personalizados con ICONOS, lo mismo para el selector y el input de detalle, el botón de generar textura no debería estar hasta abajo, corrige esto". El reporte agrupaba 3 pedidos distintos:

1. **Bug**: "los cambios generados por IA no se aplican" -- aclarado con el PO, es el bug de persistencia al recargar, ya corregido y cerrado por separado en `done/066-fix-carga-textura-persistida-al-recargar.md`. Este ticket NO lo repite.
2. **Feature**: poder reemplazar la imagen de referencia al momento de generar textura por IA -- aclarado con el PO: reemplaza la única referencia activa (no una galería acumulable), específicamente desde esta pantalla.
3. **Rediseño visual**: eliminar TODO control nativo de HTML (`<input type="radio">`, `<select>`, `<input type="range">`) de esta pantalla, reemplazándolos por widgets personalizados con íconos, y reubicar el botón "Generar con IA" fuera del pie de página.

**Workflow seguido** (memoria `ui-changes-preview-first-workflow`): antes de tocar código de producción, se publicó un mockup interactivo completo como Artifact (`https://claude.ai/code/artifact/9efe4c8d-2776-4f94-be58-6caa4fb78ebc`, favicon 🎨) usando los tokens reales del design system. El PO dio VoBo explícito: *"doy vobo del preview publicado, integralo exactamente como esta"* -- incluyendo la sustitución del slider nativo de "Detalle" por un control segmentado de 3 opciones, señalada explícitamente como una desviación de diseño a confirmar antes de asumirla.

## Criterios de aceptación (TDD)

- "Estilo" se renderiza como un `role="radiogroup"` de 4 tarjetas `role="radio"` con ícono + etiqueta (sin ningún `<input type="radio">` nativo), navegable con click y con flechas (con wrap-around).
- "Detalle" se renderiza como un control segmentado de 3 opciones (`role="radiogroup"`/`role="radio"`, sin `<input type="range">` nativo), mismo patrón de navegación que Estilo.
- "Parte a generar" usa el componente `GSelect` del design system (058) en vez de un `<select>` nativo.
- El botón "Generar con IA" vive en la columna izquierda, debajo del radiogroup de Estilo -- no al final de la pantalla.
- La imagen de referencia se puede reemplazar desde esta misma pantalla (botón "Cambiar imagen" + input de archivo), reutilizando el endpoint ya existente `POST /api/mobs/{mobId}/references` (024/027) -- sin necesitar ningún endpoint nuevo, porque `mostRecentReference` (backend) ya toma siempre la última imagen subida.
- El reemplazo de referencia valida tipo (`PNG`/`JPEG`) y tamaño (≤10MB) client-side, con los MISMOS mensajes de error que `ReferenceStep.vue` (027/037).
- El diseño resultante coincide exactamente con el mockup aprobado por el PO.

## Hecho

**Widgets personalizados (sin componentes nativos)**:
- **Estilo**: `role="radiogroup"` de 4 `role="radio"` (`texture-ai-generator__style-card`) con ícono (`IconTarget`/`IconCuboid`/`IconMosaic`/`IconEye`) + etiqueta + checkmark (`IconCheck`) en el estado activo. Navegación por teclado con un helper genérico (`handleRadioGroupKeydown`) con wrap-around, mismo patrón WAI-ARIA "Radio Group" (APG).
- **Detalle**: el slider nativo (`<input type="range">`, 3 valores discretos: Bajo/Medio/Alto) pasa a un control segmentado (`texture-ai-generator__segmented`) con el MISMO helper de teclado que Estilo -- decisión explícita: un slider ARIA accesible hecho a mano es un widget de alto riesgo para solo 3 valores discretos, cuando un radiogroup ya cubre exactamente lo mismo sin reinventar nada. Señalado al PO en el preview y aprobado.
- **Parte a generar**: `<select>` nativo reemplazado por `<GSelect>` (058) -- requirió agregarle a `GSelect.vue` un prop `disabled` nuevo (ningún consumidor anterior lo necesitaba) para poder bloquearlo fuera de la fase `form`, igual que el resto de los controles de esta pantalla.
- **Reemplazo de imagen de referencia**: botón "Cambiar imagen" (icono `IconUpload`) + `<input type="file">` oculto bajo la imagen de referencia, reutilizando `uploadReferenceImage`/`MAX_REFERENCE_IMAGE_BYTES`/`SUPPORTED_REFERENCE_IMAGE_TYPES` de `referenceImagesApi.ts` (mismos límites/mensajes que `ReferenceStep.vue`) -- sin backend nuevo: `ReferenceImageService` es append-only y `mostRecentReference` siempre toma la última, así que subir una imagen la vuelve automáticamente "la" referencia activa.
- **Reposición del botón principal**: "Generar con IA" se movió de un `__footer` al final de la pantalla a la columna izquierda, justo debajo del radiogroup de Estilo (fase `form` solamente). El pie de página ahora solo existe (y solo se renderiza) en la fase `result`, con las acciones Rechazar/Aplicar/advertencias que ya tenía.

**Extensión al design system**: `GSelect.vue` gana un prop `disabled?: boolean` (default `false`) que deshabilita nativamente el botón trigger -- primer consumidor real de esta necesidad desde que el componente nació en 058.

**Gotcha real del hook `ui-accessibility-guard.sh` encontrado en el camino**: escribir un `<input>` nuevo con un atributo por línea (7 líneas) disparó 7 violaciones fantasma "UNLABELED INPUT" aunque el tag tuviera (o no) `aria-label` -- el mismo bug de line-splitting ya documentado para `<button>`/`<a>` icon-only (memoria `ui-accessibility-guard-gotchas`, gotcha #4), aplicado por primera vez a `<input>`. Documentado como nueva memoria (`ui-accessibility-guard-input-multiline-gotcha`) enlazada a la existente. Workaround aplicado: el `<input>` completo en una sola línea, con `aria-label` presente en esa misma línea.

**TDD real**: `TextureAiGeneratorScreen.spec.ts` reescrito -- las 12 pruebas preexistentes se adaptaron a los nuevos selectores (tarjetas de estilo, opciones segmentadas, `GSelect`) sin debilitar ningún criterio ya cubierto, más 7 pruebas nuevas: navegación por teclado con wrap-around (Estilo y Detalle), reemplazo de referencia exitoso, formato no soportado, archivo demasiado pesado, y error del servidor al subir. Frontend completo: 583/583 tests, `vue-tsc -b` y `eslint --max-warnings 0` sin hallazgos.

**Sonar Quality Gate del frontend, PR #87**: bloqueó en `ERROR` (`new_violations`) con 2 hallazgos S6819 ("Use `<input>` instead of the radio role") sobre los `role="radio"` nuevos de Estilo/Detalle -- exactamente el mismo caso ya documentado en `sonar-project.properties` para `GSelect.vue` (058: el VoBo del PO exige explícitamente eliminar el control nativo, y el rol ARIA correspondiente es el reemplazo recomendado para ese caso, no un defecto real). Extendida la misma excepción documentada, ahora también para `TextureAiGeneratorScreen.vue` -- ver commit `a712baa`.

**Revisión visual en vivo contra `studio-dev` (checklist de `cerrar-ticket`)**: confirmado que el rediseño coincide con el mockup aprobado -- Estilo/Detalle/GSelect/reposición del botón funcionan correctamente por click y teclado. Encontró 1 hallazgo real que ningún test detectó (`jsdom` no renderiza layout real): el botón "Cambiar imagen" quedaba recortado a "Cambi..." -- `.texture-ai-generator__reference-replace` era un simple hijo flex de `.texture-ai-generator__reference`, apretado al lado de la imagen y recortado por el `overflow: hidden` del contenedor, en vez del overlay completo del mockup. Corregido con `position: absolute` anclado al fondo del contenedor (PR #88, commit `d2623a4`) -- reconfirmado en vivo tras el redeploy: el botón ya se ve completo.

**Sin hallazgos pendientes**: los 2 PRs de este ticket (#87 rediseño, #88 fix del overlay) están mergeados a `dev`, ambos con Jenkins verde y ambos Sonar Quality Gates OK por revisión exacta, y `studio-dev` ya redesplegado y verificado en vivo con el fix aplicado.
