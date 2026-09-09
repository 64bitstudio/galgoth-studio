# 014 — `BBModelExporterV4` (compatibilidad)

**Milestone:** no bloquea M1 · requerido antes de M6 · **Depende de:** 010 · **HUs:** HU-19, HU-21 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 15 del master prompt, adaptador de compatibilidad v4). Implementar el exportador `.bbmodel` versión 4 (sin la separación `groups`/`outliner` de v5) como subtarea de compatibilidad de la misma épica de export — **no bloquea el Gate M1** (que solo exige v5), pero **debe estar completo antes del Gate M6** (Technical Alpha final).

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` simple, cuando se exporta en formato v4, entonces produce un `.bbmodel` con `meta.format_version` correspondiente a v4 y estructura de outliner de esa versión.
- Dado el mismo `MobProjectModel` exportado en v4 y v5, cuando se comparan, entonces ambos representan la misma geometría/jerarquía, solo con la estructura de formato distinta.
- Dado los archivos `.bbmodel` reales v4 mínimos (ver 012), cuando se agregan como fixtures de conformidad para v4, entonces `BBModelExporterV4` pasa la comparación estructural.
- Dado el flujo E2E de aceptación (033), cuando se ejecuta contra el Gate M6, entonces V4 está disponible y probado — su ausencia bloquea el cierre del Technical Alpha aunque no haya bloqueado M1.

## Hecho

**AC #1 (formato v4 real) — cumplido:**
- `BBModelExporterV4.export(model[, uvLayoutStrategy])`: misma API que `BBModelExporterV5`. `format_version: "4.10"` y ausencia total de la clave `groups` (verificado que no existe como campo top-level) — cada bone se serializa embebido completo (`name`, `origin`, `rotation` opcional -- omitido si es `[0,0,0]`, igual convención observada en el `.bbmodel` real del proyecto --, `color:0`, `uuid`, `export`, `isOpen`, `children`) dentro del propio árbol `outliner` (`BBV4OutlinerGroup`/`BBV4OutlinerLeaf`, interfaz sellada + `JsonSerializer` dedicado, mismo patrón que v5).
- **Refactor al agregar V4** (sin cambiar comportamiento de v5, sus 8 tests de tickets 010/011 siguen pasando sin tocarlos): se extrajo `BBModelExportSupport` (paquete-visible) con la lógica 100% compartida entre V4 y V5 — `toElement(Cuboid)`, `isZero(Vec3)`, `buildPlaceholderTexture`, `withFreshUv` (recomputa UV vía `UvLayoutStrategy`), `serialize`. Evita duplicar esa lógica palabra por palabra en el nuevo exportador (y el `new_duplicated_lines_density` del Quality Gate).

**AC #2 (misma geometría/jerarquía que v5) — cumplido:**
- Test dedicado que exporta el MISMO `MobProjectModel` (2 bones con jerarquía padre-hijo y rotación no trivial, 2 cuboids) en v4 y v5, y compara: (a) el conjunto de cuboids por uuid con sus `from`/`to`/`origin` exactamente iguales en ambos formatos, (b) la jerarquía completa (qué hijos directos — bones y/o cuboids — tiene cada bone) leída de cada estructura de `outliner` con su propia forma (stub v5 vs. inline v4), reducida a una representación neutral antes de comparar.

**AC #3 (fixtures v4 reales) — cumplido (2026-09-09), mismo bloqueo y misma resolución que el ticket 012 (ver su propio Hecho para el detalle completo del hallazgo de que el primer paquete entregado no era real, y la corrección):** 5 casos dedicados en formato v4 (`meta.format_version:"4.10"`, "Export Legacy Project" desde el mismo proyecto de Blockbench usado para los v5 de 012), en `backend/src/test/resources/fixtures/blockbench-real/v4/`: `01_cuboid_simple`, `02_parent_child_hierarchy`, `03_pivot_rotation`, `04_multiple_cuboids`, `05_uv_real_texture`. `BlockbenchRealFixturePackConformanceTest` (mismo test que cierra 012) parametriza sobre las 10 fixtures (v5+v4): cada una parsea, valida contra el schema, y se re-exporta sin errores con el exportador que corresponde a su formato (`BBModelExporterV4` para las v4) -- confirmando estructuralmente `groups` ausente y bones embebidos en `outliner`, igual que produce nuestro propio exportador.

**AC #4 (Gate M6 / ticket 033)** — sigue sin aplicar todavía: depende de que el ticket 033 (Playwright E2E) arranque y llegue a su propio Gate M6, ahora desbloqueado por este cierre.

**Tests**: mismos 15 nuevos del cierre de 012 (compartido, un solo test parametrizado cubre ambos tickets) -- 216 tests totales en el módulo backend, 0 fallos.
