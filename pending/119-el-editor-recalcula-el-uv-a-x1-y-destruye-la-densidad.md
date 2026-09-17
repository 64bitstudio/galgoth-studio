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

Consecuencia esperada (falta confirmarla en vivo, ver criterios): un mob generado a X4 —donde cada cara ocupa 4 veces más téxels por eje— al que el usuario le mueve **un solo cuboide** queda con todas sus regiones UV recalculadas a X1. Eso no solo desperdicia el trabajo del ticket 109: mueve todos los rects, así que **la textura ya pintada deja de corresponder con el modelo**.

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
