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

### Qué se corrigió
`BoxUvMath` multiplicaba por la densidad **después** de redondear el eje a unidades enteras. Ahora escala primero y redondea después (`scaledAxis`), en las **dos** rutas que hacen el cálculo por separado: `footprintOf` (lo que el packer reserva) y `boxUnwrapFaces` (dónde caen las caras). Hay un test que fija que sigan coincidiendo para toda densidad y con ejes fraccionarios, porque es el invariante que se rompe si alguien toca una sola. Se eliminó `boxSizeAxis`, que quedó sin uso.

### Una decisión propia, revertida sobre la marcha
La primera versión forzaba un **piso de 1 téxel** para un eje que redondeaba a cero. Se revirtió al rastrear quién más usa esta matemática, por dos razones concretas:
- A X1 **cambiaba el layout** de los modelos existentes con ejes menores a 0,5. Mover un footprint desplaza el packing entero y desalinea la textura ya pintada — justo lo que `StableUvStrategy` existe para evitar.
- El frontend tiene su **propia copia** de esta matemática (`frontend/src/domain/autoUv.ts`, siempre a X1). Un piso solo del lado backend los hacía divergir en exactamente esos cuboides.

Sin piso, a X1 el resultado es idéntico al anterior y las fixtures de los tickets 006/007 siguen valiendo sin tocarlas (hay un test que lo fija).

### Verificación en vivo: `Carcomido v4`
Mob nuevo desde la **misma imagen de referencia** del v3 (copiada byte a byte), para conservar el v3 como línea de base. Geometría `MEDIUM`, densidad `max` (X4).

**El fix está activo y es el que manda**, comprobado contra los rects UV reales: en el v4, 27 caras coinciden **solo** con la matemática nueva y ninguna con la vieja; en el v3, 12 coinciden solo con la vieja.

Sobre la geometría del v4, contrafáctico calculado eje por eje:

| | v3 (matemática vieja) | v4 (con el 118) |
|---|---|---|
| Ejes sub-unitarios | 22 | 31 |
| Ejes que colapsan a 0 téxels | 8 | **10** (con la vieja habrían sido **14**) |
| Caras degeneradas | 32 | **40** (con la vieja, **56**) |

En el v3, los 8 ejes que colapsaban eran **todas las grietas** (`Face Crack`, `Skull Back Crack`, `Torso Front/Back Crack`, brazos y piernas): 0,2 unidades → 0 téxels. Con el fix dan 1.

### Lo que NO mejoró, y hay que decirlo
El criterio de aceptación pedía medir las caras enteramente negras contra las 22 del `done/114`. **No bajaron:**

| | v3 | v4 |
|---|---|---|
| Caras medidas | 202 | 230 |
| Caras enteramente negras | 22 (10,9 %) | **26 (11,3 %)** |
| Negro promedio por cara | 17,3 % | **23,7 %** |

Son otra vez las piezas diminutas: `Left/Right Eye Glow` (6 caras cada uno), `Head Crack`, `Side Crack` y las garras. La lección es concreta y vale más que el número: **hacer que una cara diminuta exista (1 téxel en vez de 0) no la hace legible**. El generador de imagen sigue sin poder poner contenido en 1-4 píxeles.

Dos salvedades honestas sobre esta comparación: v3 y v4 son **generaciones distintas** (39 vs 45 cuboides, la IA propuso más piezas de detalle en el v4), así que los porcentajes no son estrictamente comparables; y el área mínima de cara bajó de 16 px² a 4 px², lo que **no es una regresión** sino el fin de una mentira — la matemática vieja inflaba los ejes chicos (0,6 unidades daban 4 téxels en vez de los 2 que corresponden).

### Hallazgo que abrió el ticket siguiente
En el v4 la IA generó las grietas de **0,1 unidades**, que a X4 son 0,4 téxels y redondean a cero igual. El planner puede proponer geometría que **ninguna densidad disponible puede texturizar**. Decisión del PO: atacar la causa en el planner, no parchear el redondeo → `pending/121`, que además deja documentado por qué el piso de 1 téxel se descartó.

### Hallazgos laterales, ticketeados aparte
- `pending/119`: `geometryOperations.refreshUv` recalcula el UV de **todos** los cuboides a X1 en cada operación manual del editor — encontrado al rastrear la copia del frontend.
- `pending/120`: `MobGenerationServiceStreamingTest` falló en CI por la aserción de orden de etapas y pasó en el build siguiente.
- El contrato de `POST /api/mobs/{id}/generate` es **inconsistente**: `textureDensity` solo acepta el valor en minúscula (`"max"`) mientras `geometryDetail` acepta mayúscula (`"MEDIUM"`). Un `"MAX"` da 400 sin mensaje. Vale revisar si `docs/API.md` lo refleja.

### Tests
5 tests nuevos en `BoxUvMathTest` (3 verificados primero en rojo), más los dos que fijan la reversión del piso. Backend completo: **596/596 en verde**. CI verde en el PR #176, que además tuvo que corregir 7 issues de Sonar (1 propio, 6 del trabajo de login/2FA, con VoBo del PO).
