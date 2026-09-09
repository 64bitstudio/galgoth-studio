# 013 — `FmmCompatibilityValidator`

**Milestone:** M1 · **Depende de:** 010, 011, 012 · **HUs:** HU-20 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 15 del master prompt, HU-20). Implementar el validador de compatibilidad FMM con los checks aplicables a geometría/bones/UV/textura placeholder este ciclo (sin checks de animación, fuera de alcance).

## Criterios de aceptación (TDD)
- Dado un `.bbmodel` generado por 010+011, cuando se valida, entonces se verifican: JSON válido, unicidad de UUID, referencias de `groups`/`outliner` válidas, sin dimensiones de cuboid negativas/cero/inválidas, UV válida en todas las caras (ver 006/011), índices de textura existentes (placeholder incluido).
- Dado un bone con nombre especial (`hitbox`, `tag_name`, `mount_*`), cuando se valida, entonces se verifica que el nombre siga la convención esperada.
- Dado un modelo con un problema de validación, cuando se reporta, entonces el error es específico (qué elemento, qué regla) — no un mensaje genérico.
- Dado un modelo que cumple todos los checks aplicables a este ciclo, cuando se valida, entonces el resultado se marca explícitamente PASS/válido para exportar.

## Hecho

Implementado en `backend/src/main/java/com/galgothstudio/backend/domain/export/validation/`:

- **`FmmCompatibilityValidator.validate(String bbmodelJson)`**: opera sobre el `.bbmodel` ya exportado (no sobre `MobProjectModel`) — última barrera antes de entregar el archivo, no confía en que quien lo generó ya garantizó las invariantes. Checks implementados: JSON válido, unicidad de UUID entre `elements`/`groups`, referencias de `outliner` resueltas (recursivo), dimensiones de cuboid > 0 en los 3 ejes, UV de cada cara con 4 componentes dentro de `[0,textureWidth]x[0,textureHeight]`, índice de textura de cada cara dentro de los límites de `textures[]`.
- **`ValidationIssue{severity, rule, element, message}`** / **`ValidationResult{issues}`**: cada hallazgo nombra la regla y el elemento concreto (uuid), nunca un mensaje genérico (AC #3). `ValidationResult.pass()` es `true` solo si no hay ningún hallazgo `ERROR` — los `WARNING` se reportan pero no bloquean (AC #4).
- **Hallazgo real, corregido antes de implementar el check de "nombre especial de bone" (AC #2):** la convención de FreeMinecraftModels que `galgoth_studio_build_pack/TECHNICAL_REFERENCES.md` resumía como "`hitbox`, `tag_name`, y bones con prefijo `mount_`" es imprecisa. Se verificó contra el código fuente real (`BoneBlueprint.java`, `MagmaGuy/FreeMinecraftModels`): los prefijos reales son `tag_` (meta/tag), `b_` (no se muestra), `h_` (bone de cabeza), `m_` (mount point — **no** `mount_`); y por separado, un bone cuyo nombre es EXACTAMENTE (sin distinguir mayúsculas) `hitbox` o `tag_name` hace que `generateAndWriteCubes` retorne temprano — sus cuboids hijos, si tiene, **nunca se escriben** al modelo importado, un efecto real no documentado en ningún lado del proyecto hasta ahora. El validador implementa el check contra esta convención VERIFICADA, no la resumida: reporta `SPECIAL_BONE_GEOMETRY_DROPPED` (WARNING) cuando un bone `hitbox`/`tag_name` tiene cuboids hijos que se perderían en silencio. `galgoth_studio_build_pack/` es un asset fuente y no se tocó — la corrección se documenta en `docs/ARQUITECTURA.md` y aquí, no en el build pack.
- **Dependencia real vs. declarada:** el ticket lista 010/011/012 como dependencias; 012 sigue parcialmente bloqueado (ver su propio Hecho) pero ninguna de las AC de este ticket requería sus fixtures reales pendientes para implementarse ni testearse — se usó el output real de `BBModelExporterV5` (010+011) directamente.

**Tests**: 10 nuevos (`FmmCompatibilityValidatorTest`), 100% en verde — 72 tests totales en el módulo backend (`./gradlew build -x sonar`), 0 fallos. Cubren las 4 AC, incluyendo el caso "bone hitbox sin cuboids hijos" (no debe disparar ningún hallazgo) para evitar falsos positivos.

**Fuera de alcance de este ticket (según su propio Objetivo):** checks de animación (explícitamente excluidos, Fase 4 fuera del Technical Alpha).
