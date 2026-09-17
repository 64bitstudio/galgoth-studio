# 122 — Conservar el sheet crudo del proveedor, para poder diagnosticar el color

## Objetivo
Referencia: `docs/definiciones/fidelidad-de-color-en-la-generacion-de-texturas.md` (HU-2), aprobado con VoBo del PO.

Hoy la imagen que devuelve el proveedor se recorta y se descarta en el acto (`TextureSheetSlicer`), así que **no existe forma de saber si el croma se pierde en la generación o en nuestro pipeline**. Sin esto, cualquier arreglo de color es una apuesta.

Este ticket no arregla nada de color: habilita medirlo.

**Bloquea a:** 124 (el experimento que decide el diseño del resto).

## Alcance
**Incluye:**
- Conservar el sheet crudo de cada llamada al proveedor, con su `jobId` y el bone/parte al que corresponde, de forma que se pueda recuperar después.
- Que esté **detrás de un flag** y apagado por defecto: el flujo normal no debe pagar almacenamiento ni latencia por esto.
- Registrar junto al sheet el tamaño que se pidió (`inflatedSheetSize`) y el rect de destino, que es lo que permite calcular el factor de reducción real.

**No incluye:**
- Medir el color (ticket 123) ni sacar conclusiones (ticket 124).
- Exponer los sheets crudos en la UI.
- Conservarlos en el flujo normal de producción.

## Criterios de aceptación (TDD)
- Dado el flag apagado, cuando corre un job de textura, entonces no se guarda ningún sheet crudo y el comportamiento es byte a byte el de hoy.
- Dado el flag encendido, cuando corre un job, entonces por cada llamada al proveedor queda recuperable el PNG crudo tal como llegó, **sin pasar por `TYPE_INT_ARGB` ni por ningún `drawImage`** — si lo normalizamos al guardarlo, destruimos justo la evidencia que buscamos.
- Dado un sheet crudo guardado, entonces se puede saber a qué job, bone y rect de atlas corresponde, y con qué tamaño se pidió.
- Dado un job con el flag encendido, entonces el tiempo de generación no cambia de forma perceptible (el guardado no bloquea el pipeline).

## Hecho
