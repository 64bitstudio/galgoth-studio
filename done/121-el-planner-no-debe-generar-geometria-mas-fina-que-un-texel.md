# 121 — El planner no debe generar geometría más fina que un téxel

## Objetivo
Decisión del Product Owner tomada sobre la medición en vivo del ticket 118 (ver su `## Hecho`): atacar la causa en vez de parchear el redondeo.

El 118 arregló que la densidad de téxel se aplicara antes de redondear, y eso rescató las piezas finas de 0,2 unidades (a X4: `round(0,2 × 4)` = 1 téxel, antes 0). Pero al regenerar el benchmark quedó a la vista el límite real: en `Carcomido v4` la IA generó las grietas de **0,1 unidades**, que a X4 son 0,4 téxels y redondean a **cero** igual. 10 ejes colapsados, 40 caras degeneradas.

El problema de fondo no es el redondeo: es que **el planner puede proponer geometría que ninguna densidad disponible puede texturizar**. Una pieza de 0,1 unidades no tiene representación en un atlas de 4 téxels por unidad, y taparla con un téxel inventado sería maquillaje.

**Depende de:** `done/118` (sin su corrección, el umbral correcto ni siquiera se puede expresar).

## Alcance
**Incluye:**
- Que el planner (`SecondaryGeometryPlanner` / `SecondaryGeometryConstraints`) no acepte ejes por debajo de lo representable a la densidad elegida: a X4, el mínimo es 1/4 = 0,25 unidades; a X1, 1 unidad.
- Decidir y documentar el mecanismo: ¿se **rechaza** la operación (como ya hacen otros constraints deterministas) o se **ajusta** el eje al mínimo representable? Ajustar conserva la pieza y cambia levemente su forma; rechazar la pierde. La decisión queda escrita con su tradeoff.
- Que el mínimo se derive de la densidad real del job, no de una constante hardcodeada — si mañana existe X8, el umbral baja solo.
- Que el rechazo/ajuste quede visible en el reporte de calidad o en los warnings, no en silencio.

**No incluye:**
- La matemática de footprint (`done/118`).
- Subir la densidad por defecto (X4 fue decisión del `done/109`).
- El piso de 1 téxel en `BoxUvMath`: fue evaluado y descartado en el 118 (ver su Javadoc), y este ticket es la alternativa que el PO eligió en su lugar.

## Criterios de aceptación (TDD)
- Dado un plan con un cuboide de 0,1 unidades de espesor a densidad X4, cuando se aplican los constraints, entonces ese eje queda en al menos 0,25 unidades (o la operación se rechaza, según lo que se decida) y nunca produce una cara degenerada.
- Dado el mismo plan a densidad X1, entonces el mínimo aplicado es 1 unidad, no 0,25 — el umbral sale de la densidad, no de una constante.
- Dado un plan sin ejes por debajo del mínimo, entonces no se modifica ni se rechaza nada (el constraint no toca lo que ya está bien).
- El ajuste o rechazo queda registrado de forma consultable, no solo en el log.
- Verificación en vivo: regenerar el benchmark y medir las caras degeneradas contra las **40** del `Carcomido v4` (y las 56 que habría habido sin el 118).

## Hecho

### Qué se implementó
`SecondaryGeometryConstraints` recibe `texelsPerUnit` y lleva al mínimo representable (`1/texelsPerUnit`) los ejes más finos que un téxel. El umbral sale de la **densidad real del job** (`context.textureDensity()` viaja hasta los constraints), no de una constante: a X4 el mínimo es 0,25 unidades; a X1, 1 unidad entera.

**Se ajusta, no se rechaza** (decisión del PO). El tradeoff —la pieza queda algo más gruesa de lo propuesto— se compensa con tres cosas:
- El ajuste **conserva el centro** del eje: una grieta pegada a una superficie no salta de lugar al engrosarse.
- **Nunca es silencioso**: queda en `ValidationResult.adjustments()` con el eje y los dos tamaños, se loguea, y desde el `done/116` se **persiste y es consultable** vía el campo `warnings` de la API.
- Un eje de tamaño **cero o negativo se sigue rechazando**: ahí no hay intención que preservar.

Lo que ya es representable no se toca: se devuelve la misma instancia, sin copiar.

### Verificación en vivo: qué probó y qué NO
Mob nuevo `Carcomido v5`, misma imagen de referencia, geometría `MEDIUM` y densidad `max`:

| | v4 (antes) | v5 (con el 121) |
|---|---|---|
| Caras degeneradas | 40 | **0** |
| Ejes sub-unitarios | 31 | 25 |
| Ejes por debajo del mínimo (0,25) | 10 | **0** |
| Ejes ajustados a exactamente 0,25 | — | **0** |

**Las 0 caras degeneradas NO son atribuibles a este ticket.** Como no hubo ningún eje por debajo de 0,25, el código de ajuste **no llegó a ejecutarse**: la IA no propuso grietas finísimas en esta corrida. Es una buena noticia sobre el estado del modelo, no una prueba del fix.

Lo que la corrida **sí** probó: que el cableado de la densidad hasta los constraints no rompe el pipeline (44 cuboides, 15 bones, FMM compatible, 0 caras degeneradas) y que el canal de advertencias del `done/116` funciona de punta a punta (`"warnings": []` en la respuesta real de la API).

Lo que **queda sin probar en vivo**: el camino de ajuste en sí. Está cubierto por 7 tests unitarios, uno por criterio de aceptación, incluido el caso real medido (grieta de 0,1 unidades a X4 → 0,25). Forzar una corrida que lo ejercite requeriría que la IA volviera a proponer geometría sub-téxel, que no es controlable desde acá. **Se cierra así, dicho explícitamente, en vez de presentar un 0 afortunado como verificación.**

### Tests
7 tests nuevos en `SecondaryGeometryConstraintsTest`, uno por criterio de aceptación: el ajuste al mínimo, la conservación del centro, que el umbral salga de la densidad (X1 vs X4), que lo representable no se modifique, que el ajuste quede registrado, que un eje en cero se siga rechazando, y que la sobrecarga sin densidad no cambie de comportamiento. Backend completo: **607/607 en verde**.
