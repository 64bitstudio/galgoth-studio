# 127 — Corrección de color por estilo, determinista

## Objetivo
Referencia: `docs/definiciones/fidelidad-de-color-en-la-generacion-de-texturas.md` (HU-4), aprobado con VoBo del PO.

Pedirle al generador que respete la paleta depende de que obedezca, y el ticket 113 ya midió que obedece a medias. Este ticket hace que el sistema **corrija** la desviación de color cuando es fuerte, sin depender del proveedor.

Hoy no existe ningún post-proceso de color: el stage SSE `LIMPIANDO_PIXELES` es un **no-op deliberado y documentado** (`TextureGenerationService.java:276-285`) que emite el evento y no toca un píxel. Este ticket es el que le da contenido real a esa etapa.

**Depende de 124**: su resultado decide el diseño. Si el croma se pierde en el reescalado, la corrección correcta es de **muestreo** (cambiar la interpolación del compositor o el tamaño pedido), no de color. Si ya viene desaturado del proveedor, es de **cuantización a paleta**. **No empezar este ticket antes de cerrar el 124.**

## Alcance
**Incluye:**
- La corrección que dicte el 124, aplicada según el estilo:
  - **Fiel a la referencia**: la fidelidad de color es requisito duro; se corrige cuando supera el umbral.
  - **Pixel Art** y **Minecraft Vanilla**: cuantización a paleta, que es lo que esos estilos piden de todos modos.
  - **Realista**: laxo o sin corrección — la decisión se toma y se documenta, no se deja implícita.
- Que toda corrección aplicada quede **en el reporte con su magnitud**. Nunca en silencio.
- Reemplazar el no-op de `LIMPIANDO_PIXELES` o renombrar/eliminar ese stage si la corrección termina viviendo en otro lado — lo que no puede quedar es un stage que miente sobre lo que hace.

**No incluye:**
- La métrica (ticket 123) ni el experimento (124).
- La gestión de espacio de color (ticket 128).

## Criterios de aceptación (TDD)
- Dado un atlas cuya distancia de croma supera el umbral y estilo Fiel a la referencia, cuando se procesa, entonces se corrige y queda registrado que se corrigió, con cuánto.
- Dado un atlas ya fiel, entonces **no se toca un solo píxel** y queda byte a byte idéntico.
- Dado estilo Pixel Art o Minecraft Vanilla, entonces el resultado usa solo colores de la paleta.
- Dado estilo Realista, entonces se comporta según lo decidido y documentado.
- Dada cualquier corrección, entonces nunca se aplica en silencio: la magnitud es consultable fuera del log.
- **Verificación en vivo**: regenerar un mob y comparar la distancia de croma del 123 contra la corrida previa, con el preview 3D del `pending/117` ya arreglado — hasta entonces, ninguna evaluación visual del resultado es confiable.

## Hecho
