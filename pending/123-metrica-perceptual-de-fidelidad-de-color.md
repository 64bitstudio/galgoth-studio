# 123 — Métrica perceptual de fidelidad de color, separando croma de luminancia

## Objetivo
Referencia: `docs/definiciones/fidelidad-de-color-en-la-generacion-de-texturas.md` (HU-1 y HU-5), aprobado con VoBo del PO.

Hoy no hay forma objetiva de decir si una textura respeta el color de la referencia. Lo único parecido es `TextureContentValidator`, que compara el **promedio** de los píxeles contra 2 colores con distancia euclídea en sRGB y umbral 160 sobre 441 — tan laxo que casi cualquier cosa pasa, y encima no distingue un violeta de un gris de la misma luminancia.

La medición que originó la definición mostró por qué hay que separar las dos dimensiones: la luminancia era fiel (60,4 contra 59,7) y el croma no (saturación 0,31 contra 0,513). Una métrica única habría dado un número mediocre sin decir qué arreglar.

**Bloquea a:** 124.

## Alcance
**Incluye:**
- Calcular, para un atlas generado y su referencia, una **distancia de croma** y una **distancia de luminancia**, reportadas por separado.
- Usar un espacio **perceptual** (CIELAB, distancia CIEDE2000 o equivalente justificado), no euclídea en sRGB.
- Dejar la métrica consultable fuera del log, con valor incluso cuando el color es fiel (la ausencia de advertencia no puede confundirse con "no se midió").
- Resolver y documentar la pregunta abierta del documento: contra qué se compara exactamente — la referencia completa, una región, o la paleta derivada de ella. Afecta el umbral y hay que decidirlo, no dejarlo implícito.

**No incluye:**
- Corregir el color (ticket 127).
- Reemplazar `TextureContentValidator`: este ticket puede convivir con él; si lo absorbe, eso se decide acá y se documenta.

## Criterios de aceptación (TDD)
- Dado un atlas idéntico a su referencia, cuando se calcula la métrica, entonces ambas distancias son cero.
- Dado un atlas igual a la referencia pero **desaturado**, entonces la distancia de croma sube y la de luminancia se mantiene baja — es el caso real que motivó el ticket y tiene que distinguirse.
- Dado un atlas igual a la referencia pero **oscurecido**, entonces ocurre lo inverso.
- Dado un violeta y un gris de la misma luminancia, entonces la métrica los separa — cosa que la distancia euclídea en sRGB del validador actual no hace de forma confiable.
- **Validación contra el caso real ya medido**: corriendo la métrica sobre el atlas del `Carcomido v4` contra su referencia, el resultado reproduce la conclusión conocida (luminancia fiel, croma no). Si no la reproduce, la métrica está mal, no la conclusión.
- Dado cualquier job de textura, entonces su métrica queda registrada y consultable.

## Hecho
