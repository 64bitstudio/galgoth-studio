# 018 — Herramientas de transformación + Add/Delete/Duplicate

**Milestone:** M2 · **Depende de:** 016, 007, 005 · **HUs:** HU-06, HU-07 · **Épica:** 015

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Épica B). Implementar Select/Move/Scale/Rotate/Pivot y Add cuboid/Add bone/Delete/Duplicate en el editor, aplicando `AutoUv` frontend (007) en cada creación/redimensión y respetando las reglas de geometría de 005.

## Criterios de aceptación (TDD)
- Dado un cuboid seleccionado, cuando se usa Move/Scale/Rotate, entonces cambia `from`/`to`/`rotation` en tiempo real, sin permitir dimensiones negativas o cero.
- Dado "Add cuboid"/"Add bone", cuando se crea, entonces el ID lo genera la aplicación (nunca hardcodeado en el cliente) y AutoUv frontend (007) le asigna UV válida en sus 6 caras sin intervención manual.
- Dado un bone con hijos, cuando se elimina, entonces se muestra una advertencia explícita del impacto en cascada antes de confirmar.
- Dado un cuboid duplicado, cuando se completa la acción, entonces aparece una copia independiente con nuevo ID y su propia UV.
- Dado la edición del pivote de un bone, cuando se confirma, entonces las rotaciones futuras de ese bone y sus hijos usan el nuevo pivote (según el `CoordinateSystemContract` de 004).
