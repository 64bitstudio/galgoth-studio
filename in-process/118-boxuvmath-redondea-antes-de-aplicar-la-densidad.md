# 118 — `BoxUvMath` redondea a unidades enteras ANTES de aplicar la densidad, y aplasta las caras chicas

## Objetivo
Las piezas chicas del modelo (ojos, grietas, garras, jirones de ropa) salen sin contenido utilizable en la textura por más que se suba la densidad de téxel. La causa está en el orden de dos operaciones:

```java
int x = boxSizeAxis(cuboid.from().x(), cuboid.to().x()) * factor;   // redondea a unidades enteras...
public static int boxSizeAxis(double from, double to) {
    return (int) Math.round(Math.abs(to - from));                   // ...y recién después multiplica por la densidad
}
```

El redondeo ocurre **antes** de multiplicar por `texelsPerUnit()`, así que la densidad no puede recuperar lo que el redondeo ya destruyó:

| tamaño real del eje | hoy (X4) | correcto (X4) |
|---|---|---|
| 0,4 u | `round(0,4) × 4` = **0** texels | `round(0,4 × 4)` = 2 |
| 0,6 u | `round(0,6) × 4` = 4 | `round(0,6 × 4)` = 2 |
| 1,4 u | `round(1,4) × 4` = 4 | `round(1,4 × 4)` = 6 |

Un eje sub-unitario colapsa a **cero** y produce una cara degenerada, y eso pasa a cualquier densidad — subirla no ayuda, que es justo lo contrario de lo que el ticket 109 prometía.

Está en las dos rutas: `footprintOf(Cuboid, TexelDensity)` (lo que el packer reserva) y `boxUnwrapFaces(Cuboid, int, int, TexelDensity)` (dónde caen las caras). Tienen que corregirse juntas o el footprint reservado y las caras colocadas dejan de coincidir.

**Evidencia ya medida** (no es una sospecha teórica):
- `done/110`: 32 caras degeneradas en el reporte de calidad del benchmark.
- `done/114`, verificación en vivo sobre `Carcomido v3`: **22 caras salieron enteramente negras, 12 de ellas de 4×4 y ninguna mayor a 16×8** — todas caras chicas, sin un solo píxel utilizable. El relleno determinista de bordes correctamente se niega a taparlas porque no hay de dónde copiar.

**Depende de:** nada pendiente. Se apoya en el trabajo de densidad de `done/109`/`done/110`, cuya promesa este ticket termina de cumplir.

## Alcance
**Incluye:**
- Corregir el orden en las dos rutas: multiplicar por la densidad **antes** de redondear.
- Decidir y documentar qué pasa con un eje que aun así redondea a cero (un cuboide realmente degenerado): ¿mínimo de 1 texel, o se lo reporta como degenerado y no se lo pinta? La decisión queda escrita, no implícita.
- Volver a medir el reporte de calidad del benchmark (caras degeneradas y caras bajo el mínimo legible) contra los números del `done/110`.

**No incluye:**
- Cambiar la densidad por defecto (X4 ya fue decidido en el 109).
- El relleno de bordes negros (`done/114`, cerrado y medido).
- La fidelidad de color (fase de definición aparte).

## Criterios de aceptación (TDD)
- Dado un cuboide con un eje de 0,4 unidades a densidad X4, cuando se calcula su footprint, entonces ese eje mide 2 texels y no 0.
- Dado un cuboide con un eje de 1,4 unidades a densidad X4, entonces ese eje mide 6 texels (`round(1,4 × 4)`), no 4.
- Dado cualquier cuboide y densidad, el footprint reservado por `footprintOf` y las caras colocadas por `boxUnwrapFaces` siguen coincidiendo exactamente — es el invariante que no se puede romper al tocar las dos rutas.
- A densidad X1 el comportamiento no cambia respecto de hoy (`round(v × 1)` ≡ `round(v)`), así que las fixtures de los tickets 006/007 siguen en verde sin tocarlas.
- El reporte de calidad del benchmark baja su conteo de caras degeneradas respecto del número del `done/110`, y el número nuevo queda escrito en el `## Hecho`.
- Verificación en vivo: regenerar `Carcomido v3` y medir cuántas caras salen enteramente negras, contra las 22 medidas en el `done/114`.

## Hecho
