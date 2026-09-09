# Galgoth Studio

Editor web asistido por IA para mobs de Minecraft Java Edition — de imagen de concept art a `.bbmodel` validado para Blockbench y FreeMinecraftModels.

Ciclo actual: **Technical Alpha (Fase 1 + Fase 2)**. Ver `docs/definiciones/galgoth-studio-mvp.md` para el alcance completo (documento aprobado, baseline congelado).

## Estado

Sistema de diseño base del frontend (`done/002-...`) y esqueleto real del backend (`done/003-...`, Spring Boot 4.1.0 + Java 25 + Postgres vía Flyway) ya funcionando. Todavía sin pantallas productivas ni lógica de negocio (`project`/`asset`/`ai-orchestrator`/`model-validation`/`export`) — llegan en los tickets 004+.

## Setup local

Requiere: Node 24+, JDK 25 (toolchain vía Gradle si no está instalado), Docker (Testcontainers y `docker compose` lo necesitan).

**Base de datos:**
```bash
cd docker && docker compose up -d
```

**Backend** (desde `backend/`):
```bash
./gradlew bootRun      # arranca contra el Postgres de docker-compose (autodetectado)
./gradlew test         # tests reales contra Postgres vía Testcontainers, no mocks
```

**Frontend** (desde `frontend/`):
```bash
npm install
npm run dev             # http://localhost:5173 — /dev/design-system es la vitrina de componentes
npm run test            # Vitest
```

**Suite E2E de aceptación** (ticket 033, HU-23) -- flujo completo del Technical Alpha contra un stack real (Docker Compose + backend con providers mock + frontend), sin necesidad de la API real de Anthropic:
```bash
./scripts/e2e.sh        # levanta todo, corre Playwright, apaga todo siempre al salir
```
El script asume que nada más está usando los puertos 8080/5173 ni el `docker compose` del proyecto -- párralos primero si los tenías corriendo a mano.

## Estructura del repo

```text
frontend/       Vue 3 + TypeScript + Vite + Pinia + Three.js
backend/        Spring Boot 4.1.0 + Java 25, monolito modular (Gradle)
contracts/      JSON Schemas compartidos (MobProjectModel, ModelIntent, GeometryOperation[]) -- todavía vacío
docker/         docker-compose.yml (Postgres) para desarrollo local -- MinIO se agrega en el ticket 024
postman/        Colección Postman de la API REST -- todavía vacío, sin endpoints
docs/           Documentación viva (este directorio)
pending/ in-process/ done/    Tickets del proyecto (skill nuevo-ticket)
```
