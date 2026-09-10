# 040 — MobProjectModel: UvRegion/UvRegionStatus + UvReservation

**Milestone:** M7 · **Depende de:** 004, 006 · **HUs:** HU-24, HU-29, HU-33, HU-34, HU-35 · **Épica:** J/L (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §1, VoBo final del PO 10 sep 2026). Extiende `UvRegion` con un campo `status` (`UNPAINTED`/`PAINTED`/`ORPHAN`) y agrega `UvReservation`/`UvReservationReason` como tombstone explícito del espacio de atlas abandonado por un resize destructivo confirmado — sin este contrato, `StableUvStrategy` (ticket 041) no tiene dónde representar ese estado. Es prerrequisito de datos puro, sin lógica de negocio todavía.

## Criterios de aceptación (TDD)
- Dado `UvRegion(cuboidId, face, rect, status)`, cuando se deserializa un JSON de una revisión legacy de Fase 1+2 (sin el campo `status`), entonces `status` toma el default `UNPAINTED` — sin excepción, sin migración de datos.
- Dado `UvLayout(textureWidth, textureHeight, regions, reservations)`, cuando se deserializa una revisión legacy (sin el campo `reservations`), entonces `reservations` es una lista vacía por default.
- Dado un `UvReservation(id, rect, reason, sourceCuboidId, sourceFace)` con `reason=RESIZE_ABANDONED`, cuando se serializa y deserializa, entonces el round-trip es idéntico byte a byte (test de contrato, mismo criterio que el resto de `MobProjectModel`).
- Dado un `MobProjectModel` completo con regiones en los 3 estados y al menos una reserva, cuando se aplica Undo (snapshot completo, ticket 019) sobre un cambio que agregó una reserva, entonces la reserva desaparece junto con el resto del cambio — sin lógica de Undo específica para `reservations` (se prueba que el mecanismo genérico de snapshot ya lo cubre).
- Dado `contracts/schemas/mob-project-model.schema.json` y su espejo `frontend/src/domain/MobProjectModel.ts`, cuando se actualizan con los campos nuevos, entonces un test de round-trip TS↔Java↔JSON Schema (mismo mecanismo ya usado para el resto del contrato) pasa sin discrepancias.

## Hecho

Implementado exactamente como se planteó — prerrequisito de datos puro, sin lógica de negocio.

**Backend**: `UvRegion` gana `status: UvRegionStatus` (`UNPAINTED`/`PAINTED`/`ORPHAN`); `UvLayout` gana `reservations: List<UvReservation>`. Ambos con constructor compacto que normaliza `null` (JSON legacy sin el campo) a los defaults seguros (`UNPAINTED`/`List.of()`), sin excepción ni migración de datos — y un constructor de conveniencia de 3 args para que el código pre-040 (`AlphaAutoPackStrategy`, `GeometryEngine`, `BBModelExportSupport`, `MobGenerationService`) siga compilando sin tocarlo. Nuevo `UvReservation(id, rect, reason, sourceCuboidId, sourceFace)` + enum `UvReservationReason` (`RESIZE_ABANDONED`).

**Contratos espejo**: `contracts/schemas/mob-project-model.schema.json`, `frontend/src/domain/MobProjectModel.ts` y las fixtures de `contracts/fixtures/` actualizados de forma aditiva.

**Hallazgo real, no un parche silencioso**: hacer `status`/`reservations` requeridos en TypeScript (fiel a que el backend siempre los resuelve antes de emitir JSON) rompió el type-check en 16 sitios fuera del alcance nominal del ticket — mocks de test y `emptyMobProjectModel.ts`/`autoUv.ts`. Se corrigieron todos mecánicamente (agregar los campos a los literales existentes), sin tocar lógica de negocio, para que `vue-tsc -b` siguiera en verde.

**AC de Undo**: no existía todavía ninguna operación real que agregara una `UvReservation` (eso llega con `StableUvStrategy`, ticket 041), así que se agregó `commitExternalModel()` a `draftModelStore.ts` — un commit genérico vía el mismo mecanismo de Command que cualquier otra operación (push de `previous`, sin lógica de negocio propia), pensado explícitamente como el hook que 041/043 van a necesitar para confirmar cambios validados por el backend. Con eso se pudo escribir un test real de Undo sobre una reserva.

**Tests**: backend 249 (0 failures) + `UvRegionUvReservationContractTest` nuevo (5 tests). Frontend 379 tests, type-check y lint limpios. Sonar no se corrió localmente (requiere `sonarqube-db`, mismo criterio ya establecido en tickets previos) — verificado vía el pipeline de CI del PR #55, verde.

No se tocó `StableUvStrategy`, `UvLayoutSelector` ni el exportador — confirmado que compilan y sus tests pasan sin cambios, apoyados en los constructores de conveniencia.
