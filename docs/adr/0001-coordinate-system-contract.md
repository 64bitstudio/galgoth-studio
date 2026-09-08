# ADR 0001 — CoordinateSystemContract

**Estado:** aceptado · **Ticket:** `004-mobprojectmodel-coordinate-system-contract` · **Fecha:** 2026-09-08

## Contexto

`MobProjectModel` (bones, cuboides, pivots, rotaciones) se lee y escribe desde tres capas distintas: el dominio interno (TS en frontend, Java en backend), el viewport Three.js, y el formato `.bbmodel` de Blockbench. Si cada capa decide por su cuenta cómo interpretar ejes, grados/radianes, orden de rotación o composición padre-hijo, cualquier conversión entre capas es una fuente de bugs sutiles (una IA pide "agranda la mano" y el hand queda en el lugar equivocado porque el exportador rotó en el orden Z-Y-X y el viewport en X-Y-Z).

Este documento es el contrato único. **Ningún módulo (Geometry Engine, AutoUv, viewport, exportador) reimplementa su propia conversión de ejes/grados/orden de rotación** — todos usan las funciones de `frontend/src/domain/coordinateSystem.ts` (TS) o `backend/.../domain/CoordinateSystem.java` (Java), según corresponda.

## Decisión

### Unidades

**Minecraft pixels** (16 px = 1 bloque de Minecraft). Confirmado contra `samples/model_spec_example.json` (`"units": "minecraft_pixels"`) y `samples/carcomido_minecraft_cuboids.bbmodel` (`"visible_box": [2,2,2]`, modelo ~32px de alto — coherente con una entidad de ~2 bloques). `MobProjectModel.units` es un campo `const` fijo, autodocumenta el archivo, no es una opción configurable.

### Ejes (mano derecha, convención Minecraft/Blockbench)

- **X**: este (+) / oeste (−)
- **Y**: arriba (+) / abajo (−)
- **Z**: sur (+) / norte (−)

Confirmado por los nombres de cara (`north`/`south`/`east`/`west`/`up`/`down`) en cada cuboid de `carcomido_minecraft_cuboids.bbmodel` — es la convención estándar de Minecraft/Blockbench, no una elección arbitraria de este proyecto.

### `from` / `to`

Esquinas opuestas del bounding box del cuboid. **`from` ≤ `to` componente a componente** (sin dimensiones negativas — master prompt §8). El motor de operaciones (ticket 005) normaliza cualquier operación que produciría lo contrario.

### `origin` / pivot

Punto de rotación, **independiente del bounding box** — no tiene que ser su centro. Confirmado en el sample real: el cuboid `raggedShoulder_R` tiene bbox `[-8.6,20.5,-2.7]`→`[-3.8,24.3,2.7]` (centro ≈ `[-6.2,22.4,0]`) pero su `origin` es `[-4,22,0]` — el mismo pivote de hombro que usan `armRight` y `handRight`, no el centro geométrico de esa pieza en particular. El pivote se elige por semántica de articulación, no por geometría.

### Rotación: grados, Euler extrínseco XYZ

- **Unidad: grados** en todas las capas del dominio (`MobProjectModel`, `.bbmodel`). La conversión a radianes es responsabilidad exclusiva de la capa que la necesite (Three.js, `Math.sin`/`cos` internos) — nunca se almacenan radianes en el dominio.
- **Orden de composición: extrínseco X → Y → Z**, matriz `R = Rz · Ry · Rx` aplicada a un vector columna (`Rx` se aplica primero). Fuente: especificación comunitaria del formato `.bbmodel` (el wiki oficial de Blockbench reconoce que "no hay especificación completa del formato" y remite al código fuente; la convención extrínseca XYZ / `R = Rz·Ry·Rx` está documentada en la referencia comunitaria del formato — ver `TECHNICAL_REFERENCES.md`, "tratar como línea base, no contrato congelado"). Si al implementar el exportador real (ticket 010+) se encuentra evidencia de que Blockbench compone distinto, se corrige ahí con un fixture real — este ADR se actualiza, no se asume en silencio.
- **Sentido**: regla de la mano derecha (ángulo positivo = antihorario visto desde el extremo positivo del eje hacia el origen) — convención estándar en gráficos 3D (Three.js, OpenGL); sin evidencia de que Blockbench difiera.
- **Aplicación sobre un punto**: `punto' = origin + R(rotationDeg) · (punto − origin)` — trasladar al pivote, rotar, trasladar de vuelta. Igual para bones y para cuboides.

### Composición padre-hijo (bone → bone hijo → cuboid)

La transformación de un cuboid en espacio de mundo es la composición de la transformación de **cada bone en la cadena hacia la raíz**, aplicada en orden desde la raíz hacia la hoja, más la transformación propia del cuboid:

```text
worldTransform(cuboid) =
    worldTransform(bone_raíz)
    ∘ pivotRotation(bone_hijo_1, origin, rotation)
    ∘ ...
    ∘ pivotRotation(bone_padre_directo, origin, rotation)
    ∘ pivotRotation(cuboid, origin, rotation)
```

Cada `pivotRotation` usa la fórmula de arriba (`origin + R·(punto−origin)`) sobre el resultado acumulado de la capa anterior — nunca sobre las coordenadas originales sin acumular. `bones[].parentId = null` es la raíz (sin transformación adicional por encima).

### Mapeo Three.js ↔ `MobProjectModel` ↔ `.bbmodel`

| Concepto | `MobProjectModel` | Three.js | `.bbmodel` |
|---|---|---|---|
| Unidad de longitud | Minecraft pixels | unidades de escena (1:1 con px — sin reescalado) | Minecraft pixels |
| Unidad de rotación | grados | radianes (`THREE.Matrix4`/`THREE.Quaternion` los exige) | grados |
| Representación de rotación | `[x,y,z]` en grados (`vec3`) | Matriz explícita `Rz·Ry·Rx` construida a mano (`makeRotationZ().multiply(makeRotationY()).multiply(makeRotationX())`) — **no** `THREE.Euler('XYZ')` directo, para no heredar la semántica intrínseca/orden propio de Three.js y así garantizar exactamente la composición de este contrato | `rotation: [x,y,z]` (array) en los `.bbmodel` que emite nuestro exportador. Algunos `.bbmodel` reales (ej. `carcomido_minecraft_cuboids.bbmodel`) usan en cambio `rotation: <número>` + `axis: "x"\|"y"\|"z"` (forma de un solo eje) — variante real observada, no algo que nuestro exportador produzca; se documenta para el trabajo de fixtures/importer futuro (ticket 012). |
| Pivote | `origin: vec3` | `Object3D.position` del nodo pivote + geometría offset, o aplicado manualmente vía la fórmula de arriba según el contexto (viewport vs. cómputo puro) | `origin: [x,y,z]` |
| Jerarquía | `bones[].parentId` | `Object3D.parent`/`.add()` | `outliner` anidado (grupos) |

### `tempRef` y `CoordinateSystemContract`

Sin relación directa — `tempRef` (sección 9.2 del master prompt) resuelve referencias de ID dentro de un batch de operaciones de IA; este contrato resuelve coordenadas. Se mencionan juntos solo porque ambos son "reglas que todo el sistema debe respetar sin reinventar", mismo espíritu.

## Consecuencias

- Cada implementación (TS, Java) de las funciones de conversión se prueba contra el **mismo fixture** (`contracts/fixtures/coordinate-system-fixture.json`) — igual que el patrón ya usado para AutoUv (ticket 006/007): dos implementaciones, un fixture compartido, paridad verificada en CI de ambos lados.
- El orden de composición extrínseco XYZ es una **decisión documentada con la evidencia disponible, no una certeza absoluta** — queda marcado como riesgo abierto de bajo impacto (ver ticket 004, Riesgos) hasta que el exportador real (ticket 010+) lo valide contra Blockbench de verdad, abriendo o cerrando Blockbench para comparar si hace falta.
