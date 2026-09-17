# 124 — Experimento: ¿dónde se pierde el croma?

## Objetivo
Referencia: `docs/definiciones/fidelidad-de-color-en-la-generacion-de-texturas.md` (Decisión 2 y segundo diagrama), aprobado con VoBo del PO.

Hay dos causas posibles para la pérdida de saturación, y llevan a soluciones **excluyentes**:

- **Si el croma se pierde en nuestro pipeline** (el reescalado bilineal de `TextureCompositorService.java:50`, que colapsa un slice inflado ~6× al rect del atlas promediando vecinos) → la solución es de **muestreo**: vecino más cercano, o pedirle al proveedor un tamaño más cercano al destino.
- **Si ya viene desaturado del proveedor** → la solución es de **prompt + cuantización a paleta**.

Implementar cualquiera de las dos antes de saber en cuál estamos es apostar. Este ticket es el que decide.

**Depende de:** 122 (conserva el sheet crudo) y 123 (aporta la métrica). Sin los dos, no se puede correr.
**Bloquea a:** 127 (la corrección), e informa el diseño del 128.

## Alcance
**Incluye:**
- Correr la comparación sobre al menos un mob real: saturación y croma del **sheet crudo** contra la porción correspondiente del **atlas final**.
- Repetirlo sobre **varias generaciones**, no una: la variación entre corridas del mismo prompt es conocida y grande (en el ticket 114 las caras enteramente negras saltaron de 1 a 22 entre dos corridas). Una sola medición no alcanza para decidir.
- Medir de paso si el PNG del proveedor trae perfil ICC embebido o chunks `gAMA`/`cHRM` — es el dato que decide el ticket 128 y sale gratis en el mismo experimento.
- **Documentar el resultado sea cual sea**, incluido "los dos contribuyen" o "ninguno explica la pérdida". Un resultado que no confirma la hipótesis es un resultado, no un fracaso.

**No incluye:**
- Implementar la solución (ticket 127 / 128).
- Cambiar el muestreo del compositor: si el experimento apunta ahí, eso es alcance del 127.

## Criterios de aceptación (TDD)
- Dado un job con el flag del 122 encendido, cuando se comparan el sheet crudo y el atlas con la métrica del 123, entonces se obtiene la diferencia de croma atribuible a **nuestro** pipeline, separada de la que ya traía el sheet.
- Dadas al menos 3 generaciones del mismo mob, entonces la conclusión se sostiene en las 3 o se declara que no es concluyente — no se decide con una.
- Dado el resultado, entonces queda escrito en el documento de definición (o en un anexo referenciado desde él) con los números, y se indica explícitamente qué rama del segundo diagrama quedó elegida.
- Queda registrado si el PNG del proveedor trae perfil ICC.
- **Este ticket no se cierra con "parece que sí"**: o hay evidencia numérica para elegir una rama, o se cierra declarando que no la hay y qué haría falta para conseguirla.

## Hecho
