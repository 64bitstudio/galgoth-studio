# 102 — Texture V2: continuidad entre bones vía atlas parcial + validación de contenido compuesto

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-8, HU-9). La auditoría confirmó que hoy no existe ningún mecanismo de continuidad entre caras (intra-sheet activamente evitado por diseño, inter-bone inexistente — cada llamada usa siempre la referencia original completa). Este ticket introduce el atlas parcial como contexto para llamadas posteriores a la primera, **validado empíricamente antes de fijarse como comportamiento por defecto**, y amplía la validación de contenido compuesto más allá de "vacío/uniforme".

**Depende de:** 101 (usa el `TextureGenerationPlan` y el sistema de coordenadas ya corregido). Es el ticket con mayor incertidumbre de la epic — su propio criterio de aceptación exige evidencia antes de cerrar.

## Alcance
**Incluye:**
- Si el proveedor de imagen soporta múltiples imágenes de entrada por llamada: enviar **ambas** (atlas parcial compuesto + referencia original) en llamadas posteriores a la primera dentro del mismo job.
- Si el proveedor solo admite una imagen: validar empíricamente ambas estrategias (atlas parcial vs. referencia original) contra **al menos dos fixtures reales** (Carcomido + otro personaje con geometría/textura distinta) antes de fijar el comportamiento por defecto. Documentar el resultado en código (comentario/ADR), siguiendo el patrón ya usado en el proyecto para hallazgos verificados en vivo (tickets 059-065).
- Si ninguna estrategia mejora el resultado actual: documentar como limitación conocida y mantener el comportamiento actual (referencia original) — no forzar un cambio sin evidencia de mejora.
- `TextureCompositorService`: validaciones ampliadas antes de componer — alpha coverage (proporción de píxeles no transparentes razonable para una cara que se esperaba pintada), contraste/varianza (más allá del caso 100% uniforme, relativo al `TextureDetailLevel` solicitado), y comparación opcional best-effort contra paleta/material esperado cuando el plan de textura (ticket 101) lo declare. Todos los chequeos son `generationWarning`, ninguno bloquea el job.

**No incluye:**
- Cambio de proveedor de imagen o técnica de generación (segmentación por máscara real) — fuera de alcance de esta iteración.

## Criterios de aceptación (TDD)
- Dado que el proveedor soporta múltiples imágenes, cuando se genera el segundo bone en adelante, entonces se envían atlas parcial + referencia original juntos.
- Dado que el proveedor solo admite una imagen, cuando se decide cuál usar, entonces la decisión está respaldada por evidencia empírica contra al menos 2 fixtures reales, documentada en código.
- Dado que ninguna estrategia mejora el resultado, cuando se concluye la validación, entonces se documenta como limitación conocida y se mantiene el comportamiento actual.
- Dado un slice recortado casi 100% transparente cuando se esperaba contenido, cuando se valida, entonces se marca sospechoso.
- Dado un slice con contraste anormalmente bajo para el nivel de detalle solicitado, cuando se valida, entonces se marca sospechoso (no solo el caso 100% plano).
- Dado que el plan de textura declara paleta/material esperado para una cara, cuando existe esa información, entonces se compara y se advierte si diverge fuertemente; si no hay esa información, el chequeo se omite sin fallar nada.
- Ninguno de estos chequeos genera falsos positivos contra fixtures reales ya existentes con contenido válido.

## Hecho
