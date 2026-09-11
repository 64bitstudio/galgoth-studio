# 063 — Fix crítico: corrige el pixel budget de OpenAI (es de ÁREA, no de lado) -- corrige el error de 061

**Milestone:** post-M8 (hallazgo en producción/dev) · **Relacionado:** 051, 053, 059, 060, 061, 062 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Quinto hallazgo consecutivo de la misma cadena de verificación en vivo (059→060→061→062→este), y **corrige un error real del propio fix de 061**: el piso de `256px por lado` adoptado en 061 como estimación conservadora resultó INCORRECTO -- la siguiente verificación en vivo contra `studio-dev` volvió a fallar con el MISMO mensaje:
```
400 Bad Request: "Invalid size '256x256'. Requested resolution is below the current minimum pixel budget."
```
`256x256` = 65 536px de área -- claramente insuficiente. Una búsqueda más específica (`"gpt-image-2.5" "pixel budget" minimum resolution 512 OR 1024`) sí encontró el número exacto documentado para esta familia de modelos: **el "pixel budget" es una restricción de ÁREA TOTAL (`width * height`), entre 655 360 y 8 294 400 píxeles** -- nunca fue una restricción de lado individual, por lo que NINGÚN piso por lado (256, o cualquier otro valor razonable elegido a mano) podía funcionar de forma consistente.

## Criterios de aceptación (TDD)
- `OpenAiImageProvider` reemplaza el piso por lado (`MIN_SIDE_PX`, 061) por un mínimo de ÁREA (`MIN_PIXEL_BUDGET` = 655 360px): si `width * height` (tras redondeo a 16 y clamp de ratio) sigue bajo ese mínimo, AMBOS lados se agrandan proporcionalmente (preservando el ratio) hasta alcanzarlo -- nunca se encoge nada.
- Se re-aplica el clamp de aspect ratio después de escalar (el redondeo a 16 de cada lado por separado, tras escalar, puede introducir una discrepancia mínima de ratio).
- El caso real que rompió 061 (`256x256`) queda cubierto explícitamente como caso de test.
- Los tests verifican las 3 invariantes documentadas por la API real (múltiplo de 16, ratio ≤3:1, área ≥ pixel budget) en vez de un valor `size` exacto por caso -- fijar literales exactos a mano (como hizo 061, incorrectamente) es frágil para un cálculo con escalado proporcional y redondeos encadenados.
- Suite completa de backend en verde.

## Hecho
Implementado:
- `backend/src/main/java/.../aiorchestrator/provider/OpenAiImageProvider.java`: eliminado `MIN_SIDE_PX`; nuevo `MIN_PIXEL_BUDGET = 655_360` y `growToMeetPixelBudget(width, height)` -- escala proporcionalmente (`Math.sqrt`) ambos lados hasta que el área cumpla el mínimo, con un ajuste fino final (agranda el lado más chico de a 16px) por si el redondeo a 16 de cada lado por separado deja el área apenas por debajo del objetivo. `clampAspectRatio` extraído como método reutilizable, aplicado antes Y después del escalado por pixel budget. Javadoc documenta honestamente el error de 061 y cómo se corrigió.
- `backend/src/test/java/.../aiorchestrator/provider/OpenAiImageProviderTest.java`: el `@ParameterizedTest` de ajuste de `size` ahora verifica INVARIANTES (vía un `RequestMatcher` que parsea el body real y valida múltiplo de 16 / ratio / área) en vez de un `String` esperado exacto -- más robusto contra errores aritméticos manuales como el de 061. Casos actualizados/agregados: el caso real que rompió 061 (`256x256`), el caso real original (`64x32`), y el caso límite de preservación de ratio tras escalar (`1536x8`). El primer test de la clase (wiring genérico de model/prompt/n/size) se corrigió de `512x256` (todavía insuficiente en área real) a `1024x1024` (tamaño estándar real, ya sobre el mínimo).

**TDD real**: confirmado que 7 casos fallan contra el código de 061 (`git stash` del fix, re-corrida, 7 `AssertionError` reales) antes de aplicar la corrección.

**Tests**: backend 420/420, sin regresiones. `./gradlew clean test` corrido localmente antes de push.

**Verificación en vivo pendiente**: repetir "Generar con IA" (parte `head`) contra `studio-dev` una vez mergeado y desplegado -- quinta verificación de esta cadena.

**Mejora continua, reforzada por partida doble**: (1) mismo patrón de las 4 cadenas anteriores -- solo la verificación EN VIVO contra la API real encontró esto, ninguna cantidad de mocks lo hubiera detectado; (2) **lección nueva específica de este ticket**: cuando la documentación NO da un número exacto (a diferencia de 059/060, donde el mensaje de error sí lo daba), adoptar una estimación sin re-verificarla en vivo de inmediato es arriesgado -- 061 documentó honestamente que su piso "no estaba confirmado en vivo", lo cual permitió detectar y corregir el error rápido en 063 en vez de que quedara oculto como un supuesto "fix cerrado". Vale la pena, para futuras estimaciones sin fuente exacta, marcarlas explícitamente como pendientes de una verificación en vivo de seguimiento en el mismo ciclo, no darlas por buenas solo porque el código compila y los tests (con la MISMA estimación adentro) pasan.
