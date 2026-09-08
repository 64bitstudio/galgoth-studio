# Arquitectura — Galgoth Studio

Ver `docs/definiciones/galgoth-studio-mvp.md` (sección "Diseño técnico") para el diseño completo con tradeoffs — este archivo es el resumen pedagógico que se mantiene vivo conforme se implementa.

## Visión general

Monorepo con un frontend Vue 3 (editor 3D basado en Three.js) y un backend Spring Boot 3 (monolito modular, sin microservicios), comunicados por REST + SSE. Postgres como base de datos, MinIO como storage S3-compatible para imágenes/texturas/exports.

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

Solo andamiaje (ticket `pending/001-bootstrap-repo.md`) — sin lógica de negocio implementada todavía. Este archivo se completa a medida que cada ticket del milestone M0 en adelante aterriza código real (ver `pending/`/`in-process/`/`done/` para el estado de cada pieza).

## Referencias

- Documento de definición aprobado: `docs/definiciones/galgoth-studio-mvp.md`
- ADRs de decisiones técnicas puntuales: `docs/adr/`
