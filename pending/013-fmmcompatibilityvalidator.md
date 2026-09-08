# 013 — `FmmCompatibilityValidator`

**Milestone:** M1 · **Depende de:** 010, 011, 012 · **HUs:** HU-20 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 15 del master prompt, HU-20). Implementar el validador de compatibilidad FMM con los checks aplicables a geometría/bones/UV/textura placeholder este ciclo (sin checks de animación, fuera de alcance).

## Criterios de aceptación (TDD)
- Dado un `.bbmodel` generado por 010+011, cuando se valida, entonces se verifican: JSON válido, unicidad de UUID, referencias de `groups`/`outliner` válidas, sin dimensiones de cuboid negativas/cero/inválidas, UV válida en todas las caras (ver 006/011), índices de textura existentes (placeholder incluido).
- Dado un bone con nombre especial (`hitbox`, `tag_name`, `mount_*`), cuando se valida, entonces se verifica que el nombre siga la convención esperada.
- Dado un modelo con un problema de validación, cuando se reporta, entonces el error es específico (qué elemento, qué regla) — no un mensaje genérico.
- Dado un modelo que cumple todos los checks aplicables a este ciclo, cuando se valida, entonces el resultado se marca explícitamente PASS/válido para exportar.
