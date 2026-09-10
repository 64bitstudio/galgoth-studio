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
