# 125 — La paleta se deriva de los píxeles reales, no solo de lo que la IA dice ver

## Objetivo
Referencia: `docs/definiciones/fidelidad-de-color-en-la-generacion-de-texturas.md` (HU-3 y Decisión 4), aprobado con VoBo del PO.

Hoy `TexturePalette` son **dos strings hex globales para todo el mob** (`domain/model/TexturePalette.java:13`), y los **inventa la IA de visión** (`TexturePlanService.java:66-69`): no existe una sola línea de código que muestree los píxeles de la referencia. Pedirle a un modelo "decime el color dominante" y confiar en la respuesta es innecesario cuando tenemos la imagen.

La jerarquía queda explícita: **los valores salen de los píxeles, los roles semánticos siguen saliendo de la IA** (qué color es piel, cuál es acento luminoso — eso un histograma no lo sabe).

## ⚠️ Cambio de contrato
Toca `TexturePalette`, `TexturePlan` y el JSON Schema `contracts/schemas/texture-plan.schema.json` (que hoy exige exactamente `dominantColorHex` + `accentColorHex` con `additionalProperties: false`). **Requiere VoBo dedicado del PO antes de implementarse**, y hay que decidir explícitamente qué pasa con los `TexturePlan` ya persistidos.

**Se puede adelantar** sin esperar al experimento del 124.

## Alcance
**Incluye:**
- Derivar los colores dominantes de la referencia por código (histograma cuantizado o equivalente), de forma determinista y reproducible.
- Ampliar la paleta más allá de 2 colores: dominantes + acentos, con la granularidad que se decida al implementar.
- Conservar el aporte de la IA de visión para los **roles semánticos**, no para los valores.
- Migración/compatibilidad de los planes ya persistidos, decidida y documentada.

**No incluye:**
- Usar la paleta con más fuerza en el prompt (ticket 126).
- Hacerla cumplir sobre el resultado (ticket 127).

## Criterios de aceptación (TDD)
- Dada una imagen de referencia, cuando se deriva su paleta, entonces los colores salen de sus píxeles y el resultado es **determinista**: la misma imagen da la misma paleta.
- Dada una referencia con violetas saturados dominantes (el caso real del Carcomido), entonces esos violetas aparecen en la paleta derivada — hoy la paleta puede no tenerlos porque depende de lo que la IA reporte.
- Dada una paleta derivada, entonces cada color tiene su rol semántico cuando la IA lo aporta, y la ausencia de rol no rompe nada.
- Dado un `TexturePlan` persistido con el formato viejo de 2 colores, entonces sigue leyéndose sin error.
- El cambio de contrato queda reflejado en `docs/API.md` y en el JSON Schema.

## Hecho
