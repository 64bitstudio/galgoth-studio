# 001 — Bootstrap del repo

**Milestone:** M0 · **Depende de:** — · **HUs:** —

## Objetivo
Nace de la definición aprobada en `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §1). Levantar el andamiaje del monorepo — `frontend/`, `backend/`, `contracts/`, `docker/`, `postman/galgoth-studio/`, `docs/adr/`, CI/CD base — para que el resto de tickets tengan dónde vivir.

> **Corrección post-bootstrap (2026-09-08):** el documento de definición asumía GitHub Actions para CI/CD (información vigente cuando se escribió). La infra real del equipo cambió mientras tanto: cada proyecto usa un `Jenkinsfile` que invoca la Shared Library centralizada de `64bitstudio/platform` (`corePipeline`), corriendo en la VM compartida — no GitHub Actions por repo. Ver `platform/docs/ARQUITECTURA.md` (ticket 002, runbook "conectar un proyecto nuevo") y el skill `bootstrap-proyecto` ya corregido. Los criterios de aceptación de abajo reflejan esto; no es un cambio de alcance del Technical Alpha, solo una corrección de mecanismo de CI/CD.

## Criterios de aceptación (TDD)
- Dado el repo clonado en limpio, cuando se corre `docker compose up`, entonces levanta Postgres + backend (esqueleto) + MinIO sin error (desarrollo local — sin cambios).
- Dado el repo en GitHub, cuando se ejecuta `./deploy/scripts/bootstrap-project-branches.sh galgoth-studio` (script de `platform`), entonces quedan creadas las ramas `dev`/`qa`/`prod` con branch protection y el webhook GitHub→Jenkins.
- Dado un `Jenkinsfile` mínimo (`deploy: false`, sin `buildAndTest` todavía — no hay Dockerfile/lógica de negocio real en este ticket), cuando se pushea, entonces Jenkins lo descubre vía el webhook y corre un build placeholder en verde.
- Dado el paquete de ArchUnit configurado (cuando exista código backend real, ticket 005+), cuando se agregue una clase que viole los límites de paquete (`project`/`asset`/`ai-orchestrator`/`model-validation`/`export`), entonces el build falla — criterio que se activa en tickets posteriores, no en este bootstrap vacío.
- Dado `SONARQUBE_CLI_TOKEN_VM` en `~/dev-infra/.env`, cuando está vacío (pendiente de que Marco lo genere en `sonarqube.64bitstudio.com`), entonces el registro de SonarQube para este proyecto queda explícitamente marcado como bloqueado — no se inventa ni se omite en silencio.
- Dado que el repo NO tiene workflows de GitHub Actions propios, cuando se verifica (`gh api repos/<org>/galgoth-studio/actions/workflows`), entonces la lista viene vacía.
- Notificaciones a Telegram: se integran vía la Shared Library de Jenkins (mismo patrón `✅`/`🔴`), no como paso de GitHub Actions.
