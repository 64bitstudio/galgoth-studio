# 085 — Enforcement de acceso a proyectos (dueño / público / privado)

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — HU-3, cierre real del ticket `077` ("queda listo para
que un ticket futuro decida qué rutas proteger" — este es ese ticket).
Con `owner_ref`/`visibility` ya reales (ticket `084`), las 14+ rutas de
proyectos/mobs/drafts/texturas/export/geometría siguen completamente
abiertas: cualquiera con el ID puede editar o borrar el proyecto de
otro.

**Depende de:** `084` (necesita `owner_ref`/`visibility` reales para
tener algo contra qué comparar).

## Alcance
- **Sí incluye:**
  - `ProjectAccessGuard` nuevo (ver "Diseño técnico → 3" del documento de
    definición): `requireOwner(projectId, callerId)` para mutaciones,
    `requireViewable(projectId, callerId)` para lecturas (dueño real O
    `visibility = PUBLIC`, si no `ProjectNotFoundException` → `404`).
  - Los 9 controladores anidados bajo `/api/projects/{projectId}/**`
    (`MobController`, `MobDetailController`, `MobDraftController`,
    `MobExportController`, `MobGeometryController`,
    `MobReferenceImageController`, `MobTextureController`,
    `MobThumbnailController`, y `ProjectController` mismo) pasan por el
    guard antes de operar.
  - `SecurityConfig`: reemplaza `anyRequest().permitAll()` por la tabla
    de reglas explícita de la sección "Diseño técnico → 4" del
    documento — mutaciones y "Mis proyectos"/"recientes" exigen
    `authenticated()`; lecturas de detalle quedan `permitAll()` a nivel
    de Spring Security (la decisión real la toma el guard, para permitir
    lectura anónima de un proyecto público).
  - Intentar `GET`/`PATCH`/`DELETE` un proyecto privado ajeno (o
    cualquier recurso anidado suyo) responde `404`, nunca `403` — no
    revela que el proyecto existe.
- **No incluye:** el endpoint de cambio de visibilidad ni Explorar
  (ticket `086`), nada de frontend.

## Criterios de aceptación (TDD)
- Test explícito: dueño real accede sin problema a cada operación
  (mutación y lectura) de su propio proyecto — sin regresión.
- Test explícito: un usuario distinto al dueño recibe `404` al intentar
  mutar un proyecto ajeno (privado o público), y al leer uno privado
  ajeno.
- Test explícito: una lectura sin `Authorization` de un proyecto
  `PUBLIC` funciona (200); la misma lectura sin `Authorization` de uno
  `PRIVATE` responde `404`.
- Suite completa del backend en verde, incluyendo los tests existentes
  (confirma que no se rompió ningún flujo de dueño real).
- Verificación en vivo contra DEV: dos cuentas de prueba reales, se
  confirma que la cuenta B no puede ver/editar un proyecto privado de la
  cuenta A por URL directa.

## Hecho
