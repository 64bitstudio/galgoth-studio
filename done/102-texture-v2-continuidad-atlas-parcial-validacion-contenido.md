# 102 — Texture V2: continuidad entre bones vía atlas parcial + validación de contenido compuesto

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-8, HU-9). La auditoría confirmó que hoy no existe ningún mecanismo de continuidad entre caras (intra-sheet activamente evitado por diseño, inter-bone inexistente — cada llamada usa siempre la referencia original completa). Este ticket introduce el atlas parcial como contexto para llamadas posteriores a la primera, **validado empíricamente antes de fijarse como comportamiento por defecto**, y amplía la validación de contenido compuesto más allá de "vacío/uniforme".

**Depende de:** 101 (usa el `TextureGenerationPlan` y el sistema de coordenadas ya corregido). Es el ticket con mayor incertidumbre de la epic — su propio criterio de aceptación exige evidencia antes de cerrar.

## Alcance
**Incluye:**
- Si el proveedor de imagen soporta múltiples imágenes de entrada por llamada: enviar **ambas** (atlas parcial compuesto + referencia original) en llamadas posteriores a la primera dentro del mismo job.
- Si el proveedor solo admite una imagen: validar empíricamente ambas estrategias (atlas parcial vs. referencia original) contra **al menos dos fixtures reales** (Carcomido + otro personaje con geometría/textura distinta) antes de fijar el comportamiento por defecto. Documentar el resultado en código (comentario/ADR), siguiendo el patrón ya usado en el proyecto para hallazgos verificados en vivo (tickets 059-065).
- Si ninguna estrategia mejora el resultado actual: documentar como limitación conocida y mantener el comportamiento actual (referencia original) — no forzar un cambio sin evidencia de mejora.
- `TextureCompositorService`: validaciones ampliadas antes de componer — alpha coverage (proporción de píxeles no transparentes razonable para una cara que se esperaba pintada), contraste/varianza (más allá del caso 100% uniforme, relativo al `TextureDetailLevel` solicitado), y comparación opcional best-effort contra paleta/material esperado cuando el plan de textura (ticket 101) lo declare. Todos los chequeos son `generationWarning`, ninguno bloquea el job.

**No incluye:**
- Cambio de proveedor de imagen o técnica de generación (segmentación por máscara real) — fuera de alcance de esta iteración.

## Criterios de aceptación (TDD)
- Dado que el proveedor soporta múltiples imágenes, cuando se genera el segundo bone en adelante, entonces se envían atlas parcial + referencia original juntos.
- Dado que el proveedor solo admite una imagen, cuando se decide cuál usar, entonces la decisión está respaldada por evidencia empírica contra al menos 2 fixtures reales, documentada en código.
- Dado que ninguna estrategia mejora el resultado, cuando se concluye la validación, entonces se documenta como limitación conocida y se mantiene el comportamiento actual.
- Dado un slice recortado casi 100% transparente cuando se esperaba contenido, cuando se valida, entonces se marca sospechoso.
- Dado un slice con contraste anormalmente bajo para el nivel de detalle solicitado, cuando se valida, entonces se marca sospechoso (no solo el caso 100% plano).
- Dado que el plan de textura declara paleta/material esperado para una cara, cuando existe esa información, entonces se compara y se advierte si diverge fuertemente; si no hay esa información, el chequeo se omite sin fallar nada.
- Ninguno de estos chequeos genera falsos positivos contra fixtures reales ya existentes con contenido válido.

## Hecho
### Continuidad entre bones (HU-8)
- **El proveedor real SÍ soporta múltiples imágenes por llamada**, así que aplica la primera rama del alcance (enviar ambas), no la de "solo admite una imagen": la referencia oficial de la API de OpenAI para `/v1/images/edits` (consultada 2026-09) documenta hasta 16 imágenes de entrada por llamada para la familia `gpt-image-*` — que es exactamente la configurada en este proyecto (`OPENAI_IMAGE_MODEL`, default `gpt-image-2.5-sunburst-2026-09-08`) — enviadas como partes multipart REPETIDAS bajo el nombre `image[]`.
- `TextureGenerationSheetRequest` gana el campo aditivo `partialAtlasBytes` (constructor de compatibilidad de 5 args para los call sites previos, mismo patrón que `Cuboid`/`CreateCuboid` del 099) — cero call sites existentes rotos.
- `OpenAiImageProvider.callEdits` ahora manda N imágenes bajo `image[]` (mismo formato para 1 o N, un solo camino de código): **la referencia original siempre primero** (nunca se la reemplaza ni se la descarta) y el atlas parcial después.
- `TextureGenerationService.runPipeline` pasa el atlas ya compuesto como `partialAtlasBytes` **de la segunda sheet del job en adelante** (contador `sheetsGenerated`, no índice de bone: lo que importa es que exista contenido real ya compuesto). En la primera llamada va `null` — todavía no hay nada generado por este job que sirva de contexto.
- **Verificación en vivo: HECHA y EXITOSA** (16-sep-2026, `studio-dev`, mob real `Carcomido v2`: 46 cuboides, 15 bones). Se corrió una generación de textura de **modelo completo** desde la UI, con estilo "Fiel a la referencia" y detalle Medio. El job recorrió todos los bones (`Analizando paleta` → `Mapeando caras` → `Generando textura: <bone>` por cada uno → `Componiendo atlas`) y terminó en la propuesta Antes/Después, sin error.

  **Por qué esto prueba el formato multi-imagen**: la PRIMERA sheet del job viaja con una sola imagen (todavía no hay atlas parcial); de la SEGUNDA en adelante viajan DOS partes `image[]`. Un rechazo del formato por parte de la API habría fallado el job exactamente en el segundo bone (`AiProviderException` → `status=failed`), no antes. El job pasó por los 15 bones, así que la API real aceptó las llamadas multi-imagen. Queda así cerrado el precedente de 059/060/061/063: esta vez la documentación oficial coincidió con el comportamiento real, pero recién se afirma después de comprobarlo.

  El Javadoc de `OpenAiImageProvider.callEdits` se actualiza en este mismo commit para reflejar que la verificación ya ocurrió, con su fecha.

- **Hallazgo real de esa misma corrida, que NO es de este ticket** (reportado al PO y origen de una definición nueva): el atlas resultante del mob es de 32×256 px para 276 caras, con 178 caras (64%) por debajo de 16 px² y mediana de 8 px² — los ojos (2×1.2 u) y colmillos (1×1 u) reciben 1-2 téxeles. La continuidad y el alineamiento de coordenadas funcionan (los mosaicos caen limpios en su celda UV), pero no hay píxeles suficientes para que la IA pinte contenido reconocible. Es un problema de asignación de téxeles, no de este ticket ni de la calidad del proveedor. Ver `docs/definiciones/densidad-de-texel-y-resolucion-de-textura.md`.

### Validación de contenido compuesto (HU-9)
- `TextureContentValidator` (nuevo, determinista, sin IA): un solo barrido de píxeles por slice que produce `Finding`s tipados (enum cerrado `FindingType`, nunca string libre — listo para que el 104 los mapee a `generationWarnings` estructurados):
  - `LOW_ALPHA_COVERAGE`: < 5% de píxeles pintados en una cara que se pidió con contenido. Corta ahí: sin píxeles pintados, varianza y color dominante no son medidas significativas, así que no se acumulan hallazgos derivados del mismo problema.
  - `UNIFORM_CONTENT`: desviación de luminancia (Rec. 601) < 1.0 — el caso "plano/vacío".
  - `LOW_CONTRAST_FOR_DETAIL_LEVEL`: desviación < 4.0 **solo cuando se pidió `TextureDetailLevel.HIGH`** — con LOW/MEDIUM un resultado casi plano es una respuesta legítima, marcarlo sería un falso positivo.
  - `PALETTE_DIVERGENCE`: distancia euclídea RGB del color dominante al color más cercano de la paleta declarada > 160 (umbral laxo a propósito: la paleta describe la referencia completa, no esa cara). Best-effort real: sin paleta declarada, o con hex mal formado (el plan viene de un LLM, nunca se asume bien formado), el chequeo se omite sin fallar nada.
- Ningún hallazgo bloquea ni altera el job: el slice se compone igual y el hallazgo se loguea con sus números concretos (`logContentFindings`), mismo criterio ya establecido por `MobGenerationService.logRejections` (099).
- Umbrales elegidos deliberadamente conservadores por el AC explícito de "sin falsos positivos contra contenido válido" — hay un test dedicado a eso (un slice con ruido real alrededor de la paleta declarada no genera NINGÚN hallazgo, ni siquiera pidiendo detalle HIGH).

### Tests
- `TextureContentValidatorTest` (9, nuevo): cada tipo de hallazgo, el caso "sin falsos positivos", paleta ausente/mal formada, y que cada hallazgo identifique cuboid+cara concretos.
- `OpenAiImageProviderTest` (+2): dos imágenes viajan como dos partes `image[]` repetidas con la referencia primero; atlas parcial vacío manda una sola imagen. Test existente de `edits` actualizado al nuevo nombre de campo.
- `TextureGenerationServiceTest` (+1, integración real con Testcontainers): la primera llamada del job va sin atlas parcial y la segunda con él, con la referencia original presente en ambas. `MockImageProvider` gana registro de requests recibidas (con tope de 50 y reset obligatorio en `@BeforeEach`, por el hallazgo de contexto Spring cacheado del 099).
- Suite completa del backend: 560/560 en verde (0 failures, 0 errors, daemon fresco).
