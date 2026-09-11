# 065 — Fix crítico: el atlas compuesto salía casi 100% negro (el slicer no escalaba al tamaño real inflado)

**Milestone:** post-M8 (hallazgo en producción/dev) · **Relacionado:** 053, 059-064 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
El PO pidió revisar el "preview oscuro" observado tras el primer job completado con éxito (`head`, ticket 063). Decodificado el PNG real del atlas compuesto (`ai_jobs.proposal_jsonb->>'composedAtlasPngBase64'` del job `659f436b-...`): el atlas de 128x128 salió **casi 100% negro/transparente** (16 000 px transparentes, 331 negro puro, apenas un puñado de píxeles con algo de color) -- no un problema de renderizado del frontend, el dato real generado y persistido ya venía mal.

**Root cause**: `TextureGenerationService.runPipeline` pasa el `sheet` (con `sheetWidth()/sheetHeight()` = dimensiones ORIGINALES calculadas por `ShelfBinPacker`, ANTES de que `OpenAiImageProvider.sizeParam` -- tickets 060/061/063 -- las infle para cumplir los requisitos reales de la API) directo a `TextureSheetSlicer.slice(sheetBytes, sheet)`. Esa inflación puede ser de 10-25x (ej. un sheet de 64x32 termina pidiéndose como 816x816 para cumplir el pixel budget mínimo de 063). La API genera contenido real proporcional a TODO el canvas pedido, no confinado a una esquina -- así que recortar con las coordenadas ORIGINALES (pequeñas) contra la imagen REAL (mucho más grande) sin escalar extraía una esquina mayormente vacía/negra, nunca el contenido generado real.

## Criterios de aceptación (TDD)
- `TextureSheetSlicer` escala cada `sheetRect` proporcionalmente (`imagenReal.ancho/alto` ÷ `sheet.sheetWidth()/sheetHeight()`) antes de recortar.
- El slice resultante puede quedar más grande que el `atlasUvRect` de destino -- eso es SEGURO por diseño: `TextureCompositorService` YA soporta ese mismatch (lo reescala automáticamente al tamaño de destino, decisión ya vigente del PO, ver su propio Javadoc) -- este ticket no le cambia nada a esa clase.
- Test de regresión que reproduce el escenario real (imagen real más grande que el sheet planeado) y confirma que se extrae el contenido correcto, no una esquina en blanco.
- El caso "el rect no cabe ni después de escalar" (dato genuinamente inconsistente) sigue fallando explícito -- se ajustó el test existente para que siga probando ESE caso real (antes probaba "placement excede la imagen real sin escalar", que con el fix ya no es un caso de fallo por sí solo).
- Suite completa de backend en verde.

## Hecho
Implementado:
- `backend/src/main/java/.../aiorchestrator/texture/TextureSheetSlicer.java`: `slice()` calcula `scaleX/scaleY` a partir de la imagen real decodificada vs. `sheet.sheetWidth()/sheetHeight()`; `cropClamped` aplica ese escalado a cada `sheetRect` antes de recortar. Javadoc documenta el hallazgo completo (atlas casi 100% negro, root cause, por qué el fix es seguro dado el mismatch ya soportado por `TextureCompositorService`).
- `backend/src/test/java/.../aiorchestrator/texture/TextureSheetSlicerTest.java`: +1 test que reproduce el escenario real (imagen real 2x más grande que el sheet planeado, con dos mitades de color distinto) y confirma que se extrae la mitad correcta tras escalar, no la que caería sin escalar. Se ajustó `unSheetRectQueExcedeLosLimitesRealesDeLaImagenGenerada...` -- su fixture original (placement que ocupa el sheet completo, sheet más grande que la imagen real) dejó de ser un caso de fallo con el fix (escalar el sheet COMPLETO siempre mapea exactamente a los límites reales, por construcción) -- se cambió a un placement que excede las dimensiones DECLARADAS de su propio sheet (dato inconsistente que ni el escalado puede arreglar), preservando el espíritu original del test.

**TDD real**: confirmado que el test nuevo falla contra el código sin este fix (`git stash`, re-corrida, `AssertionError` real) antes de aplicar la corrección.

**Tests**: backend 423/423 (+3 desde 063: 2 de 064 + 1 de 065 -- el test de fixture ajustado no suma, solo cambia su premisa), sin regresiones. `./gradlew clean test` corrido localmente antes de push.

**Verificación en vivo pendiente**: repetir "Generar con IA" contra `studio-dev` una vez mergeado y desplegado, confirmando que el atlas resultante tiene contenido real (no negro/vacío) y que el preview Antes/Después de la UI ya no se ve oscuro.

**Relacionado**: ver también `done/064-planner-omite-caras-area-cero.md` -- descubierto y corregido en la misma investigación, mismo PR.

**Hallazgo real del gate de Sonar (PR #84, corregido antes de mergear)**: 3 falsos positivos de S1135 ("Complete the task associated to this TODO comment") -- Sonar detecta la palabra "todo"/"TODO" (español, "el canvas completo") dentro de comentarios como si fuera un marcador `TODO:` real, sin entender el idioma (mismo tipo de gotcha ya encontrado antes en `ui-accessibility-guard.sh`). Reformulados los 3 comentarios para evitar la palabra en vez de suprimir la regla. Además, 1 hallazgo REAL (S5853, "Join these multiple assertions subject to one assertion chain") en el test nuevo de 064 -- corregido encadenando las aserciones.

**Mejora continua, importante**: este bug es consecuencia DIRECTA de un supuesto que hice en 060 ("la imagen resultante puede llegar más grande que lo pedido, y eso es seguro por diseño -- `TextureSheetSlicer` ya recorta cada placement por su `sheetRect` exacto... cualquier margen extra queda simplemente descartado") -- ese razonamiento era válido para un crecimiento PEQUEÑO (redondeo a múltiplo de 16, unos pocos px), pero se rompía silenciosamente para el crecimiento GRANDE que 063 (pixel budget de área) introdujo después (10-25x). La lección: una garantía de seguridad razonada para un caso pequeño no se extiende automáticamente a una escala de magnitud distinta introducida por un cambio posterior -- vale la pena, al escalar un ajuste ya "cerrado", revisar explícitamente si sus garantías originales siguen sosteniéndose bajo la nueva magnitud.
