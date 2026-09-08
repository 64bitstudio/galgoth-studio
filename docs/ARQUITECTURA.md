# Arquitectura — Galgoth Studio

Ver `docs/definiciones/galgoth-studio-mvp.md` (sección "Diseño técnico") para el diseño completo con tradeoffs — este archivo es el resumen pedagógico que se mantiene vivo conforme se implementa.

## Visión general

Monorepo con un frontend Vue 3 (editor 3D basado en Three.js) y un backend Spring Boot 4.1.0 / Java 25 (monolito modular, sin microservicios — corregido desde "3.x/Java 21" al implementar el ticket 003, ver Addendum de `docs/definiciones/galgoth-studio-mvp.md`), comunicados por REST + SSE. Postgres como base de datos (migraciones Flyway), MinIO como storage S3-compatible para imágenes/texturas/exports (todavía no levantado — se agrega cuando el ticket 024 lo necesite de verdad).

```text
Frontend (Vue3+Three.js) --REST/SSE--> Backend (Spring Boot, monolito modular)
                                          ├─ project            (proyectos/mobs)
                                          ├─ asset              (imágenes de referencia, MinIO)
                                          ├─ ai-orchestrator     (jobs de IA, SSE)
                                          ├─ model-validation    (AutoUv, FmmCompatibilityValidator)
                                          └─ export              (BBModelExporter v4/v5)
                                        --> Postgres + MinIO
```

## Estado actual

- **Frontend**: sistema de diseño base implementado (`done/002-...`) — sin pantallas productivas todavía.
- **Backend**: esqueleto real Spring Boot funcionando (`done/003-...`) — `GalgothStudioApplication` arranca, se conecta a Postgres vía `docker/docker-compose.yml` (autodetectado por `spring-boot-docker-compose`), y Flyway aplica el esquema inicial (8 tablas, ver `docs/BASE_DE_DATOS.md`). Sin Dockerfile ni deploy real todavía — sin paquetes de dominio de negocio (`project`/`asset`/`ai-orchestrator`/`model-validation`/`export`) ni ArchUnit, que aterrizan con el primer código de negocio real (ticket 005+).
- **Dominio compartido** (`done/004-...`): `MobProjectModel` -- JSON Schema (`contracts/schemas/`, fuente de verdad formal), tipos TS (`frontend/src/domain/`) y DTOs Java (`backend/.../domain/model/`), verificados por round-trip contra el mismo fixture (`contracts/fixtures/model-spec-example.json`). `CoordinateSystemContract` (`docs/adr/0001-coordinate-system-contract.md`) fija unidades/ejes/rotación/composición padre-hijo, implementado en ambos lados (`coordinateSystem.ts` / `CoordinateSystem.java`) y verificado contra el mismo fixture (`contracts/fixtures/coordinate-system-fixture.json`) — todo módulo futuro que necesite convertir coordenadas usa exclusivamente estas funciones, no reimplementa la suya.
- **CI**: `Jenkinsfile` en la raíz corre `buildAndTest` de frontend (lint+test+build+Sonar) y backend (`./gradlew build sonar`, incluye Testcontainers) en el mismo pipeline.

Este archivo se completa a medida que cada ticket del milestone M0 en adelante aterriza código real (ver `pending/`/`in-process/`/`done/` para el estado de cada pieza).

## Referencias

- Documento de definición aprobado: `docs/definiciones/galgoth-studio-mvp.md`
- ADRs de decisiones técnicas puntuales: `docs/adr/`
