# 117 — El preview 3D no muestra la textura, aunque el canvas 2D sí

## Objetivo
Encontrado al verificar el ticket 115 en vivo sobre `Carcomido v3` (`studio-dev`), ya con el 115 desplegado: el editor de textura pinta el atlas **correctamente** en el canvas 2D (47.968 píxeles opacos, idénticos al PNG persistido), pero el modelo 3D no muestra esa textura.

Dos comportamientos distintos, medidos en la misma sesión:
- **Preview 3D de la pestaña Textura** (HU-26, "textura 3D en vivo"): el mob se ve **negro**, sin las grietas violetas ni el tono verdoso que el atlas claramente tiene. No cambia tras una segunda carga, así que no es la carrera de pintado que arregló el 115.
- **Viewport de la pestaña Modelo**: el mob se ve **gris plano**, o sea sin textura aplicada en absoluto.

Que sean distintos entre sí es la pista: el preview de Textura parece estar aplicando algo (queda negro, no gris), mientras que el de Modelo no aplica nada.

## Alcance
**Incluye:**
- Determinar si el viewport de la pestaña **Modelo** debe mostrar la textura o si el gris plano es intencional (editor de geometría). Si es intencional, se documenta y se cierra esa mitad; si no, se arregla.
- Arreglar el preview 3D de la pestaña **Textura**, que sí debe mostrarla por HU-26.
- Diagnosticar por qué queda negro en vez de mostrar el contenido: `syncDataTexture` construye un `DataTexture` sobre `atlas.pixels` y llama a `applyPreviewModel()`; hay que verificar si el `threeViewportService` lo recibe, si el material lo usa, y si las UV del modelo muestrean donde hay contenido (el atlas mide 1024 de alto y el contenido vive solo en las ~360 filas superiores).

**No incluye:**
- El canvas 2D (arreglado y verificado en el 115).
- La calidad de la textura generada (caras chicas sin contenido: ver hallazgo 1 del `done/114`).

## Criterios de aceptación (TDD)
- Dado un mob con textura persistida, cuando se abre la pestaña Textura, entonces el preview 3D muestra esa textura y no un modelo negro.
- Dado ese mismo mob, cuando se pinta un trazo en el canvas 2D, entonces el preview 3D lo refleja en vivo (HU-26 no es solo la carga inicial).
- La decisión sobre el viewport de la pestaña Modelo queda documentada, se arregle o se declare intencional — no se deja ambigua.
- Verificación en vivo contra `studio-dev` sobre `Carcomido v3`, que es donde está reproducido.

## Hecho
