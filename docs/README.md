# Galgoth Studio

Editor web asistido por IA para mobs de Minecraft Java Edition — de imagen de concept art a `.bbmodel` validado para Blockbench y FreeMinecraftModels.

Ciclo actual: **Technical Alpha (Fase 1 + Fase 2)**. Ver `docs/definiciones/galgoth-studio-mvp.md` para el alcance completo (documento aprobado, baseline congelado).

## Estado

Repositorio recién bootstrapeado (ticket `pending/001-bootstrap-repo.md`). Todavía no hay código de aplicación — `frontend/`, `backend/`, `contracts/` y `docker/` son andamiaje vacío. Este archivo se completa con instrucciones reales de instalación/setup/run conforme avance el ticket 001 y los siguientes del milestone M0.

## Estructura del repo

```text
frontend/       Vue 3 + TypeScript + Vite + Pinia + Three.js
backend/        Spring Boot 3 + Java 21, monolito modular
contracts/      JSON Schemas compartidos (MobProjectModel, ModelIntent, GeometryOperation[])
docker/         docker-compose.yml (Postgres + backend + MinIO) para desarrollo local
postman/        Colección Postman de la API REST
docs/           Documentación viva (este directorio)
pending/ in-process/ done/    Tickets del proyecto (skill nuevo-ticket)
```
