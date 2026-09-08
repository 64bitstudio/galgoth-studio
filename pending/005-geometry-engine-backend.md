# 005 — Geometry Engine (backend)

**Milestone:** M0 · **Depende de:** 004 · **HUs:** HU-14

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 9.2 del master prompt y HU-14). Implementar el motor determinista de aplicación de operaciones de geometría (whitelist cerrada), con validación de esquema atómica y resolución de `tempRef` — el único camino por el que cualquier escritura de geometría (manual server-side o IA) pasa.

## Criterios de aceptación (TDD)
- Dado una lista de operaciones que incluye `createBone`, `createCuboid`, `resizeCuboid`, `moveCuboid`, `rotateCuboid`, `setBonePivot`, `setBoneRotation`, `parentBone`, `removeCuboid`, cuando se aplican sobre un `MobProjectModel`, entonces cada una produce el efecto esperado usando el `CoordinateSystemContract` de 004.
- Dado un batch con una operación fuera de whitelist o con payload inválido, cuando se valida, entonces el batch completo se rechaza de forma atómica (ninguna operación se aplica).
- Dado un `createBone` seguido de un `createCuboid` que lo referencia por `tempRef` en el mismo batch, cuando se aplica, entonces el backend resuelve el `tempRef` a un UUID real generado por la aplicación — nunca un UUID provisto externamente.
- Dado un `resizeCuboid`/`moveCuboid` que produciría dimensiones negativas o cero, cuando se valida, entonces se rechaza antes de aplicarse.
- Dado un `removeCuboid` sobre un cuboid con referencias activas, cuando se aplica, entonces las referencias padre/hijo del modelo resultante siguen siendo válidas.
