# 101 — Texture V2: alineamiento de coordenadas prompt↔API + `TextureGenerationPlan` por cara

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-6, HU-7, sección "Texture Generation V2"). La auditoría de código confirmó la causa raíz de "colores en regiones incorrectas" y "zonas en blanco": el prompt describe coordenadas para el tamaño original del sheet, pero `OpenAiImageProvider` infla la imagen real pedida a la API hasta 25x más grande — el modelo nunca puede reconciliar ambos sistemas. Este ticket corrige ese desalineamiento y formaliza `TextureGenerationPlan` (extendiendo `TexturePlan` existente) con entradas por cara que unen `cuboidId + semanticPart + face + material + atlasRegion` en un solo lugar.

**Depende de:** 099 (necesita `semanticPart` persistido). **Bloquea:** 102 (continuidad se apoya en el plan estructurado y el sistema de coordenadas ya corregido).

## Alcance
**Incluye:**
- `TextureSheetPromptComposer`: describe el grid usando las mismas dimensiones infladas que `OpenAiImageProvider` va a solicitar realmente a la API (no el tamaño original del sheet).
- `TextureSheetSlicer`: recorta usando ese mismo sistema de coordenadas consistente de punta a punta, eliminando el escalado proporcional "a ojo" actual.
- `TextureGenerationPlan` (extiende `TexturePlan`/`BoneSemanticLabel` existente): ancla a `semanticPart` persistido (en vez de reinferir desde el nombre libre del bone), y mantiene entradas por `(cuboidId, face)` que agrupan `semanticPart + material + atlasRegion` en un solo lugar — cada línea de coordenadas del prompt lleva adjunta su propia nota de material, no un bloque separado al inicio.

**No incluye:**
- Continuidad entre bones vía atlas parcial (ticket 102).
- Validación de contenido compuesto (ticket 102).
- Segmentación real por máscara bitmap — explícitamente fuera de alcance de esta iteración (ver riesgos del documento de definición: si esta corrección no basta, la iteración futura sería máscara real).

## Criterios de aceptación (TDD)
- Dado un sheet cuyo tamaño original difiere del tamaño real inflado para la API, cuando se compone el prompt, entonces el grid describe coordenadas en el sistema de la imagen real solicitada.
- Dado el resultado de la API, cuando se recorta cada cara, entonces se usa el mismo sistema de coordenadas del prompt — sin doble conversión.
- Dado un modelo con `semanticPart` ya persistido, cuando se arma el plan de textura, entonces cada entrada por cara incluye `cuboidId`, `semanticPart`, `face`, nota de material y región de atlas juntos.
- Dado el plan, cuando se compone el prompt de una sheet, entonces cada línea de coordenadas lleva adjunta su propia nota de material.
- Suite existente de `TextureSheetSlicer`/`OpenAiImageProvider` sigue en verde — no se rompe compatibilidad de formato de sheet.

## Hecho
