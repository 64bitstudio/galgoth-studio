# 115 — El editor de textura abre con el atlas vacío aunque el mob tiene textura persistida

## Objetivo
Al entrar a la pestaña **Textura** de un mob que ya tiene textura aplicada, el editor muestra el atlas completamente vacío (solo el damero de transparencia y la grilla de regiones UV). La textura no se perdió: el modelo sigue referenciando su PNG. Es el editor el que no la carga.

Reproducido dos veces sobre `Carcomido v3` en `studio-dev` (`mobId 282e74d7-4f54-4e8f-8617-f332086e821f`), incluyendo después de un reload limpio de la página. En ese estado, `GET /api/mobs/{mobId}/draft` devuelve `model.texture.storageKey = "textures/6237734c…png"` — o sea el dato está, el lienzo no.

El impacto es de confianza, no solo cosmético: el usuario abre el editor y ve su trabajo perdido. Además, cualquier pincelada o import sobre ese lienzo vacío corre el riesgo de escribir encima de una textura que el usuario cree que ya no está.

## Alcance
**Incluye:**
- Diagnosticar en qué eslabón se corta: si el frontend no pide el PNG del `storageKey`, si lo pide y falla, o si lo recibe y no lo pinta en el canvas del editor.
- Que el editor cargue y muestre el atlas persistido al abrir la pestaña.
- Determinar si al guardar desde ese estado vacío se pisa la textura buena (si se pisa, es lo más urgente del ticket).

**No incluye:**
- Rediseñar el editor de textura.
- El problema de las caras chicas que salen sin contenido (otro subsistema, ver el hallazgo 1 del `done/114`).

## Criterios de aceptación (TDD)
- Dado un mob cuyo `model.texture.storageKey` apunta a un PNG con contenido, cuando se abre la pestaña Textura, entonces el lienzo muestra ese PNG y no un atlas vacío.
- Dado ese mismo mob, cuando se recarga la página estando en la pestaña Textura, entonces el lienzo sigue mostrando el PNG (el bug se reproduce sobre todo después del reload).
- Dado un mob que efectivamente NO tiene textura (sin `storageKey`), cuando se abre la pestaña, entonces el lienzo vacío es el resultado correcto y no se rompe nada.
- Verificación en vivo contra `studio-dev` sobre `Carcomido v3`, que es donde está reproducido.

## Hecho
