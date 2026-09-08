# 022 — CRUD de mobs + detalle de proyecto + modal "Agregar mob"

**Milestone:** M3 · **Depende de:** 021 · **HUs:** HU-03, HU-04

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Épica A). Detalle de proyecto con grid de mobs, búsqueda, y el flujo de "Agregar mob" (preservando la idea útil de `add_mob_modal_legacy.png`).

## Criterios de aceptación (TDD)
- Dado el detalle de un proyecto, cuando se hace clic en "Agregar mob", entonces se abre el flujo de creación de mob.
- Dado que se completa la creación, cuando se confirma, entonces el mob aparece con estado "Draft", `current_revision_number=0` y sin fila en `mob_drafts`.
- Dado el detalle de un proyecto con varios mobs, cuando se usa el buscador, entonces la lista se filtra por nombre.
- Dado el grid de mobs, cuando se renderiza, entonces muestra estado (Ready/In progress/Draft) por cada mob.
