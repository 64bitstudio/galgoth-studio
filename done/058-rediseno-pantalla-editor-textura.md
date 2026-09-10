# 058 — Rediseño de la pantalla del editor de textura (mockup 07 v2)

**Milestone:** M11 (addendum post-Fase-3) · **Depende de:** 047, 048, 049, 050 · **HUs:** HU-27, HU-41 · **Épica:** N (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de una revisión directa del PO sobre la pantalla ya construida en el ticket 050: la distribución (herramientas en columna lateral izquierda) no respeta la línea visual del producto y comprime el lienzo, que debería ser el foco principal. El PO validó un preview interactivo (Artifact) del rediseño y dio **VoBo explícito** antes de tocar código, siguiendo el flujo acordado para cambios visuales de este proyecto (ver memoria `ui-changes-preview-first-workflow`): construir un Artifact primero, iterar sobre feedback real, implementar solo después del VoBo.

El preview validado (dos rondas: layout inicial + 3 correcciones de usabilidad) está en `docs/definiciones/mockups/058-texture-editor-redesign-reference.html` (copia exacta del Artifact aprobado, para referencia de comportamiento/markup/estilos — NO es el componente final, es la especificación viva de interacción).

## Cambios obligatorios (del VoBo del PO, sin reinterpretar)

### 1. Layout en tres zonas
- **Toolbar horizontal arriba** (no columna lateral izquierda): selector de región, Pincel/Borrador/Cubeta/Cuentagotas, toggle de grid, tamaño de pincel, color activo, controles de zoom, separador, Importar PNG, Generar con IA, estado de guardado, Guardar.
- **Lienzo UV/atlas** ocupa el área central principal (foco visual dominante).
- **Preview 3D** a la derecha, secundario (más angosto que el lienzo).
- Barra de estado inferior opcional: región seleccionada | zoom | herramienta activa | estado de guardado.

### 2. Zoom y pan reales en el lienzo
- Zoom in/out/reset + porcentaje visible + dropdown de presets (50/100/200/400/800/1600%).
- `Ctrl`+rueda (zoom centrado en el cursor), atajos `+`/`-`/`0`.
- Pixel-perfect en todos los niveles de zoom (`image-rendering: pixelated`, sin ningún suavizado) — no negociable, ya es un principio establecido del proyecto (047).
- Pan: barra espaciadora + arrastre, y/o scroll nativo con scrollbars ya estilizadas del proyecto.

### 3. Todos los selectores son componentes propios
- Ningún `<select>` nativo. Aplica a región, tamaño de pincel, presets de zoom, estilo/parte del panel de IA.
- Estados hover/focus/open, opción activa en verde menta (`--accent`), navegación por teclado (flechas/Enter/Escape), ícono de flecha consistente.
- **Selector de color**: la paleta fija de 8 colores NO es suficiente — debe permitir elegir *cualquier* color. Única excepción explícita a "sin componentes nativos" (ya aceptada por el PO en la ronda de ajustes): un `<input type="color">` real pero oculto, disparado por un swatch "personalizado", más un campo de texto para hex directo.

### 4. Integración real con IA visible en la toolbar
- Botón "Generar con IA" visible en la toolbar (no escondido) que abre el flujo existente del ticket 054/055 (reutilizar el pipeline y los endpoints ya construidos — este ticket es de shell/UX, no reimplementa el pipeline de IA).

### 5. Guardado visible con estado
- Botón "Guardar" + indicador de 4 estados: guardado / cambios sin guardar / guardando… / error al guardar (reutiliza el flush ya construido en el ticket 056 — `textureFlush.ts`/`textureAtlasEncode.ts`/`textureUploadApi.ts`).

### 6. Preview 3D orbitable
- El PO señaló explícitamente en la segunda ronda de ajustes que el preview 3D debía poder rotarse con el puntero (arrastrar), no solo girar automáticamente sin control. El viewport real ya usa Three.js + `ThreeViewportService` — verificar si ya expone orbit controls (es probable que sí, mismo mecanismo que la tab "Modelo") y sencillamente NO deshabilitarlos en este contexto; si están deshabilitados aquí a propósito, habilitarlos.

## Criterios de aceptación (TDD)
- Dado que entro al tab "Textura", cuando la pantalla carga, entonces veo las 3 zonas del layout (toolbar horizontal arriba, lienzo dominante, preview 3D secundario) — nunca la columna lateral izquierda del diseño anterior.
- Dado el lienzo, cuando hago zoom (botones, `Ctrl`+rueda, o atajos de teclado), entonces el nivel de zoom afecta SOLO al lienzo (nunca al resto de la pantalla), permite llegar a niveles altos de ampliación, y el resultado sigue siendo pixel-perfect sin ningún suavizado.
- Dado cualquier selector de esta pantalla (región, tamaño de pincel, zoom), cuando lo uso, entonces es un componente propio del design system — ningún `<select>` nativo en el DOM de esta pantalla (verificable con un test que falle si aparece uno).
- Dado el selector de color, cuando quiero un color que no está en la paleta fija, entonces puedo elegirlo (color picker nativo oculto disparado por un control propio, o hex directo) y ese color queda activo para pintar.
- Dado que uso el cuentagotas, cuando tomo un color del lienzo, entonces hay una confirmación visible de qué color se capturó (no un cambio silencioso imperceptible).
- Dado el botón "Generar con IA" en la toolbar, cuando hago clic, entonces se dispara el flujo real ya existente (054/055) — reutilizado, no reimplementado.
- Dado el estado de guardado, cuando el atlas tiene cambios sin persistir, entonces el indicador lo refleja (guardado/sin guardar/guardando/error), reutilizando el flush ya construido en 056.
- Dado el preview 3D, cuando arrastro sobre él con el puntero, entonces la cámara orbita según el arrastre — el giro automático (si existe) no bloquea el control manual.
- Ningún test existente de `TextureCanvas.spec.ts`/`MobEditor.spec.ts`/`EditorHeader.spec.ts` se debilita ni se ignora para forzar verde — se actualizan donde el cambio de estructura lo requiera legítimamente.
- **Revisión visual en vivo obligatoria antes de cerrar** (skill `cerrar-ticket`): contrastar la pantalla corriendo contra el Artifact validado con Claude in Chrome.

## Hecho
