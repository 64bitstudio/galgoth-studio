# 021 — CRUD de proyectos + dashboard

**Milestone:** M3 · **Depende de:** 002, 003 · **HUs:** HU-01, HU-02

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Épica A). Crear/listar proyectos, con tarjetas mostrando nombre, hasta 3 miniaturas de mob y acciones Rename/Duplicate/Export/Delete.

## Criterios de aceptación (TDD)
- Dado un nombre válido en "Nuevo proyecto", cuando se confirma, entonces se crea el proyecto y se redirige a su detalle.
- Dado un intento de crear un proyecto sin nombre, cuando se confirma, entonces el sistema lo impide con un mensaje de validación claro.
- Dado un proyecto con más de 3 mobs, cuando se muestra su tarjeta, entonces aparece el indicador "+N".
- Dado el menú de acciones de una tarjeta, cuando se despliega, entonces ofrece Rename, Duplicate, Export, Delete.
