# 014 — `BBModelExporterV4` (compatibilidad)

**Milestone:** no bloquea M1 · requerido antes de M6 · **Depende de:** 010 · **HUs:** HU-19, HU-21 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 15 del master prompt, adaptador de compatibilidad v4). Implementar el exportador `.bbmodel` versión 4 (sin la separación `groups`/`outliner` de v5) como subtarea de compatibilidad de la misma épica de export — **no bloquea el Gate M1** (que solo exige v5), pero **debe estar completo antes del Gate M6** (Technical Alpha final).

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` simple, cuando se exporta en formato v4, entonces produce un `.bbmodel` con `meta.format_version` correspondiente a v4 y estructura de outliner de esa versión.
- Dado el mismo `MobProjectModel` exportado en v4 y v5, cuando se comparan, entonces ambos representan la misma geometría/jerarquía, solo con la estructura de formato distinta.
- Dado los archivos `.bbmodel` reales v4 mínimos (ver 012), cuando se agregan como fixtures de conformidad para v4, entonces `BBModelExporterV4` pasa la comparación estructural.
- Dado el flujo E2E de aceptación (033), cuando se ejecuta contra el Gate M6, entonces V4 está disponible y probado — su ausencia bloquea el cierre del Technical Alpha aunque no haya bloqueado M1.

## Hecho (parcial — ticket NO cerrado, mismo bloqueo que 012)

**AC #1 (formato v4 real) — cumplido:**
- `BBModelExporterV4.export(model[, uvLayoutStrategy])`: misma API que `BBModelExporterV5`. `format_version: "4.10"` y ausencia total de la clave `groups` (verificado que no existe como campo top-level) — cada bone se serializa embebido completo (`name`, `origin`, `rotation` opcional -- omitido si es `[0,0,0]`, igual convención observada en el `.bbmodel` real del proyecto --, `color:0`, `uuid`, `export`, `isOpen`, `children`) dentro del propio árbol `outliner` (`BBV4OutlinerGroup`/`BBV4OutlinerLeaf`, interfaz sellada + `JsonSerializer` dedicado, mismo patrón que v5).
- **Refactor al agregar V4** (sin cambiar comportamiento de v5, sus 8 tests de tickets 010/011 siguen pasando sin tocarlos): se extrajo `BBModelExportSupport` (paquete-visible) con la lógica 100% compartida entre V4 y V5 — `toElement(Cuboid)`, `isZero(Vec3)`, `buildPlaceholderTexture`, `withFreshUv` (recomputa UV vía `UvLayoutStrategy`), `serialize`. Evita duplicar esa lógica palabra por palabra en el nuevo exportador (y el `new_duplicated_lines_density` del Quality Gate).

**AC #2 (misma geometría/jerarquía que v5) — cumplido:**
- Test dedicado que exporta el MISMO `MobProjectModel` (2 bones con jerarquía padre-hijo y rotación no trivial, 2 cuboids) en v4 y v5, y compara: (a) el conjunto de cuboids por uuid con sus `from`/`to`/`origin` exactamente iguales en ambos formatos, (b) la jerarquía completa (qué hijos directos — bones y/o cuboids — tiene cada bone) leída de cada estructura de `outliner` con su propia forma (stub v5 vs. inline v4), reducida a una representación neutral antes de comparar.

**AC #3 — BLOQUEADO, mismo motivo y misma decisión explícita del Product Owner que el ticket 012 (2026-09-08):** los archivos `.bbmodel` reales mínimos EN FORMATO V4 requieren Blockbench real, no disponible en este entorno. **Este ticket permanece en `in-process/`, NO se mueve a `done/`**, hasta que esos archivos existan (pueden ser los mismos que arregla 012, adaptados, o archivos v4 dedicados — a decidir cuando lleguen).

**AC #4** — no aplica todavía (depende del ticket 033, que no ha arrancado).

**Tests**: 3 nuevos (`BBModelExporterV4Test`), 100% en verde — 75 tests totales en el módulo backend (`./gradlew build -x sonar`), 0 fallos.
