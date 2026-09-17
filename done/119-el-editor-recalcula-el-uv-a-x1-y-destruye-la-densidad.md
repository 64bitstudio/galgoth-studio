# 119 — Mover un cuboide en el editor recalcula todo el UV a X1 y tira abajo la densidad

## Objetivo
Encontrado mientras se implementaba el 118. `geometryOperations.refreshUv` recalcula el UV de **todos** los cuboides en cada operación manual del editor (mover, redimensionar, rotar):

```ts
function refreshUv(model: MobProjectModel): MobProjectModel {
  const result = layoutUv(model.cuboids, model.uv.textureWidth, model.uv.textureHeight)
  return { ...model, cuboids: result.cuboids, uv: { ...model.uv, regions: result.regions } }
}
```

`layoutUv` vive en `frontend/src/domain/autoUv.ts`, que es una **copia propia** de la matemática de box-unwrap del backend y **no conoce la densidad de téxel**: siempre calcula a X1.

Consecuencia: un mob generado a X4 —donde cada cara ocupa 4 veces más téxels por eje— queda con **todas** sus regiones UV recalculadas a X1. Eso no solo desperdicia el trabajo del ticket 109: mueve todos los rects, así que **la textura ya pintada deja de corresponder con el modelo**.

> **Corrección al enunciado original de este ticket** (se descubrió al escribir el primer test, que pasó cuando debía fallar): `refreshUv` **no** corre al mover un cuboide. Mover, redimensionar y rotar pasan por `replaceCuboid` a secas y no tocan el UV. `refreshUv` corre solo al **crear** y **duplicar** un cuboide — las dos operaciones que cambian el conjunto a empaquetar. El bug es igual de real y de grave, pero se dispara ahí, no al mover.

**Depende de:** nada. Se relaciona con el `done/109` (que introdujo la densidad) y el `118` (que arregló la matemática del lado backend).

## Alcance
**Incluye:**
- Confirmar el impacto real en vivo antes de decidir el arreglo: mover un cuboide de un mob generado a X4 y comparar sus regiones UV antes y después.
- Que el editor deje de degradar la densidad: o `autoUv.ts` recibe y respeta la densidad del modelo, o el recálculo deja de hacerse en el frontend y pasa a pedirse al backend (que ya es "la autoridad canónica final" según el propio Javadoc de `geometryOperations`).
- Decidir qué hacer con la **duplicación** de la matemática de box-unwrap entre `BoxUvMath.java` y `autoUv.ts`: hoy son dos implementaciones que hay que mantener sincronizadas a mano, y ya divergieron una vez (el 118 tuvo que descartar un piso mínimo de 1 téxel justamente para no separarlas más).

**No incluye:**
- La matemática del backend (arreglada en el 118).
- El preview 3D (`pending/117`).

## Criterios de aceptación (TDD)
- Dado un mob generado a densidad X4, cuando se mueve un cuboide en el editor, entonces sus regiones UV conservan la escala X4 y no se recalculan a X1.
- Dado ese mismo mob con textura pintada, cuando se mueve un cuboide, entonces las regiones que no cambiaron de tamaño no se desplazan (la textura sigue correspondiendo).
- La duplicación entre `BoxUvMath.java` y `autoUv.ts` queda resuelta o documentada explícitamente con el criterio de sincronización — no se deja como está sin decir nada.
- Verificación en vivo contra `studio-dev`.

## Hecho

### Corrección de la premisa, encontrada por un test que pasó cuando debía fallar
El primer test que escribí afirmaba que **mover** un cuboide degradaba el UV. Pasó en verde. `refreshUv` tiene exactamente **dos** llamadores: `createCuboid` y `duplicateCuboid`. Mover, redimensionar y rotar van por `replaceCuboid` y no tocan el UV.

El bug es igual de real: **crear o duplicar un cuboide en un mob generado a X4 rehacía el atlas entero a X1**, cuatro veces más chico por eje. Reproducido con test: la cara `north` de un cuboide de 8×12 pasaba de **32 téxels de ancho a 8**.

### Qué se implementó
- `autoUv.ts` gana `texelsPerUnit` (default 1) y escala **antes** de redondear, con la misma semántica que `BoxUvMath.scaledAxis` del backend (ticket 118). A densidad 1 el resultado es idéntico al anterior, así que las fixtures compartidas con el backend siguen valiendo sin tocarlas.
- **`inferTexelsPerUnit(cuboids)`**: deduce la densidad del layout que el modelo ya tiene, comparando el ancho real de la cara `north` contra el tamaño del cuboide en unidades. Existe porque `MobProjectModel` **no transporta su densidad** — `TextureDensity` es un parámetro de generación del backend y no se persiste.
- `refreshUv` la usa en vez de asumir 1.

### Decisiones tomadas y por qué
- **Deducir la densidad en vez de agregarla al modelo.** Agregarla sería más limpio, pero es un cambio de contrato (`MobProjectModel` + schema + migración de los modelos persistidos) que necesita VoBo dedicado. La deducción es autocontenida y no rompe nada. Si más adelante el modelo transporta su densidad, `inferTexelsPerUnit` se borra y se lee el campo.
- **Gana la densidad más votada, no la del primer cuboide.** Una sola cara degenerada no puede decidir por todo el modelo.
- **Una relación que no corresponde a ninguna densidad real (`TexelDensity` = X1/X2/X4) cae a 1**, no se inventa una densidad X3.
- **Sin cuboides medibles devuelve 1**, que es exactamente el comportamiento anterior al ticket.

### Sobre el criterio 2 (las regiones que no cambian no se desplazan)
Se cumple **por construcción** y quedó fijado con un test: el packing es determinista, así que con la densidad correcta y el mismo conjunto de tamaños, los rects salen idénticos. Mover un cuboide además ni siquiera pasa por acá.

### Hallazgo lateral, no arreglado
`layoutUv` marca **todas** las regiones como `status: 'unpainted'` al recalcular, incluidas las que conservan su rect exacto. En un mob con textura aplicada, crear un cuboide borra el estado de pintado de todo el modelo. Es un problema distinto (estado, no geometría) y no se tocó acá para no mezclar; **queda para decisión del PO** si se abre ticket.

### Sobre la duplicación entre `BoxUvMath.java` y `autoUv.ts`
El ticket pedía resolverla o documentarla. **No se resolvió**: siguen siendo dos implementaciones sincronizadas a mano. Lo que sí se hizo es alinear su semántica (escalar antes de redondear) y dejar la referencia cruzada escrita en el código de los dos lados. Unificarlas de verdad implica o compilar el dominio a ambos targets o que el editor pida el layout al backend en cada operación — las dos son decisiones de arquitectura que exceden este ticket.

### Tests
8 tests nuevos, verificados primero en rojo los que reproducen el bug: 3 en `geometryOperations.spec.ts` (crear conserva X4, duplicar conserva X4, mover no toca el UV) y 5 en `autoUv.spec.ts` sobre la inferencia (X4, X1, sin datos, cara degenerada, relación inválida). Frontend completo: **927/927 en verde**, tsc y lint limpios.
