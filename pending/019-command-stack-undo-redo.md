# 019 — Command stack Undo/Redo

**Milestone:** M2 · **Depende de:** 018 · **HUs:** HU-08 · **Épica:** 015

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §4, HU-08). Implementar la pila de `Command` en Pinia sobre el draft en memoria — cada edición manual genera un Command; ninguno crea una `mob_revision` por sí solo.

## Criterios de aceptación (TDD)
- Dado una serie de cambios manuales, cuando se presiona Undo repetidamente, entonces el draft regresa paso a paso en el orden inverso exacto — ninguna `mob_revision` se crea ni se destruye en el proceso.
- Dado que se deshicieron cambios, cuando se presiona Redo, entonces el draft avanza hasta el estado más reciente.
- Dado un cambio nuevo aplicado después de deshacer, cuando ocurre, entonces se descarta la rama de redo pendiente (historial lineal estándar).
