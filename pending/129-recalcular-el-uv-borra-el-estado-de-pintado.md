# 129 — Recalcular el UV borra el estado de pintado de todo el modelo

## Objetivo
Encontrado al cerrar el `done/119`. `layoutUv` (`frontend/src/domain/autoUv.ts`) marca **todas** las regiones como `status: 'unpainted'` al recalcular, incluidas las que conservan su rect **exacto**:

```ts
regions.push({ cuboidId: cuboid.id, face: faceName, rect: faces[faceName].uv, status: 'unpainted' })
```

Como `refreshUv` corre al crear y al duplicar un cuboide, **crear una sola pieza en un mob con textura aplicada borra el estado de pintado del modelo entero**. Los píxeles no se pierden (el atlas es otro archivo), pero el modelo deja de saber qué caras estaban pintadas.

El comentario del código dice que es intencional ("AutoUv siempre recomputa desde cero... nunca preserva contenido pintado"), criterio heredado de Fase 1+2, cuando no existía textura generada por IA ni pintado manual persistido. Hoy sí existen, así que la decisión merece revisarse — no darse por válida por antigüedad.

**Depende de:** `done/119`, que dejó el packing determinista y con densidad correcta. Gracias a eso, ahora se puede saber qué regiones conservaron su rect y cuáles no.

## Alcance
**Incluye:**
- Decidir si el estado de pintado debe preservarse cuando el rect de una región no cambia, y documentar la decisión con su tradeoff.
- Si se preserva: que `layoutUv` conserve el `status` anterior de las regiones cuyo rect quede idéntico, y marque `unpainted` solo las que se movieron o son nuevas.
- Revisar qué consume `status` hoy, para no romper a nadie al cambiarlo.

**No incluye:**
- La densidad de téxel (`done/119`).
- El editor de textura ni el atlas en sí: esto es estado del modelo, no píxeles.

## Criterios de aceptación (TDD)
- Dado un modelo con regiones `painted` y una operación que no cambia ningún rect, cuando se recalcula el UV, entonces esas regiones siguen `painted`.
- Dada una región cuyo rect **sí** cambió, entonces pasa a `unpainted` — su contenido ya no corresponde.
- Dada una región nueva (cuboide recién creado), entonces nace `unpainted`.
- Si la decisión es **no** preservar, entonces queda escrita en el código con el porqué actualizado a hoy, no heredado de Fase 1+2.

## Hecho
