# 010 — `BBModelExporterV5`

**Milestone:** M1 · **Depende de:** 004, 005, 006 · **HUs:** HU-19, HU-21 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §7, formato v5 como camino principal). Implementar el exportador `.bbmodel` versión 5 (separación `groups`/`outliner` de Blockbench 5), en backend, operando sobre un `MobProjectModel` (geometría + bones, sin textura/animación funcional este ciclo).

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` simple (un cuboid, un bone), cuando se exporta, entonces produce un `.bbmodel` con `meta.format_version` correspondiente a v5 y `groups`/`outliner` correctamente separados.
- Dado un `MobProjectModel` con jerarquía padre-hijo de bones, cuando se exporta, entonces la jerarquía se refleja fielmente en `outliner` usando las conversiones del `CoordinateSystemContract` (004).
- Dado cualquier export, cuando se inspecciona el JSON resultante, entonces todos los UUIDs son únicos y las referencias de `groups`/`outliner` son válidas.
- Dado un cuboid con `from`/`to`/`rotation` no triviales, cuando se exporta, entonces los valores numéricos en el `.bbmodel` corresponden exactamente a la conversión definida en 004 (sin pérdida ni desfase de ejes/grados).
