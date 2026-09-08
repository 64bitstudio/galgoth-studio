# 031 — Edición por IA con diff + Apply/Reject + 409

**Milestone:** M5 · **Depende de:** 030, 020 · **HUs:** HU-17, HU-18

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (mockup 06, Diseño técnico §5). Implementar el panel de edición conversacional por IA sobre un mob ya existente (post "Usar este modelo"): prompt en lenguaje natural → plan de cambio → Before/After → Apply/Reject, verificando `base_revision_number` **y** `base_draft_version`.

## Criterios de aceptación (TDD)
- Dado una instrucción como "haz las manos más grandes y los hombros más irregulares", cuando se envía, entonces se recibe un plan con resumen y operaciones, generado contra el draft/revisión actuales (`base_revision_number`+`base_draft_version` registrados en el job).
- Dado un plan recibido, cuando se revisa, entonces se ve Before/After y los cuboides/bones afectados, sin que el draft ni la revisión hayan cambiado todavía.
- Dado Apply, cuando se confirma, entonces se ejecuta en una transacción que actualiza `mob_drafts` (mismo mecanismo de 020) y crea una nueva `mob_revisions`.
- Dado Reject, cuando se confirma, entonces ni el draft ni las revisiones cambian.
- Dado que el draft o la revisión base avanzaron desde que se generó la propuesta, cuando se intenta Apply, entonces se rechaza con 409, no se aplica ninguna operación, y se ofrece regenerar la propuesta contra el estado actual.
- Dado que la llamada a la IA falla, cuando ocurre, entonces el modelo (draft y revisiones) queda sin cambios y se informa el error.
