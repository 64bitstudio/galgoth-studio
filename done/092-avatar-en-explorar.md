# 092 — Mostrar el avatar del autor en Explorar

## Objetivo
Ver `docs/definiciones/perfil-de-usuario.md` (VoBo de Marco recibido) —
HU-4. Con `user_profile.avatar_key` ya existiendo (ticket `091`), la
sección Explorar (ticket `086`/`088`) puede mostrar la foto real del
autor de cada proyecto público en vez de solo su nombre.

**Depende de:** `091` (columna `avatar_key`), `086`/`088` (Explorar, ya
hecho).

## Alcance
- **Sí incluye:**
  - El endpoint de Explorar (`GET /api/explore/projects`) y el detalle
    público de un proyecto (`GET /api/projects/{id}` en modo lectura)
    hacen join/lookup contra `user_profile` por `owner_ref` y agregan la
    URL del avatar a la respuesta.
  - Sin avatar (`avatar_key IS NULL`), se omite el campo — el frontend
    ya sabe mostrar un avatar por defecto (inicial del nombre).
- **No incluye:** cambios a la subida/gestión del avatar en sí (ticket
  `091`).

## Criterios de aceptación (TDD)
- Un proyecto público cuyo dueño tiene avatar → la respuesta de
  Explorar/detalle incluye su URL.
- Un proyecto público cuyo dueño NO tiene avatar → el campo es `null`,
  nunca una URL rota.
- Suite completa en verde.
- Verificación en vivo contra DEV.

## Hecho

**PR:** [#131](https://github.com/64bitstudio/galgoth-studio/pull/131), mergeado a `dev` (`737162e`). Deploy real a DEV verificado en verde (build 130 de Jenkins).

**Implementado tal cual el alcance, más un pull-forward de frontend con VoBo explícito:**
- `ProjectSummary`/`ProjectDetail` ganan `avatarUrl` (`null` si el dueño no tiene avatar, ticket 091). `ProjectService.toDetail`/`toSummary` lo resuelven vía la nueva `UserProfileService.avatarUrlIfPresent(UUID)` -- un `SELECT` por proyecto listado, mismo criterio N+1 ya aceptado para las miniaturas de mobs en `toSummary` (documentado, sin requisito de escala que lo justifique todavía). `GET /api/explore/projects` y `GET /api/projects/{id}` ya traen el campo -- ningún endpoint nuevo.
- **Pull-forward de frontend (decisión vía `AskUserQuestion`, VoBo explícito de Marco):** el Alcance del ticket ("Sí incluye") solo listaba trabajo de backend, pero su propio Objetivo describe el resultado visible ("puede mostrar la foto real del autor"), y ningún otro ticket conectaba ese dato al frontend -- ni el 093 (que es sobre la pantalla "Usuario", el propio avatar de cada quien, no avatares ajenos en Explorar). Marco eligió explícitamente conectar el consumo ahora: `ExploreProjectCard.vue`/`ExploreProjectDetail.vue` muestran la imagen real, o la inicial del nombre como fallback si el dueño no tiene avatar (`avatarUrl()` nuevo en `apiConfig.ts`, mismo criterio que `thumbnailUrl()`).
- **Corrección real a la premisa del propio ticket:** el texto decía "el frontend ya sabe mostrar un avatar por defecto (inicial del nombre)" -- eso era falso, no existía ningún componente ni lógica de fallback en el frontend antes de este ticket. Se construyó como parte del pull-forward de arriba, no se asumió como ya hecho.

**Tests:** 4 nuevos en el backend (`ExploreProjectControllerTest` + `ProjectControllerTest`, con/sin avatar, vía Testcontainers real) + 4 nuevos en el frontend (`ExploreProjectCard.spec.ts` + `ExploreProjectDetail.spec.ts`). Backend: suite completa en verde. Frontend: 805 tests en verde, `npm run build` (`vue-tsc -b` real) y `eslint --max-warnings 0` limpios.

**Verificación en vivo contra DEV real (navegador real, Chrome, no simulada):**
- Cuenta de prueba real registrada, logueada, y con un avatar PNG real subido vía `POST /api/account/avatar`.
- Proyecto real creado (`avatarUrl` ya presente en la respuesta de creación) y publicado.
- `GET /api/explore/projects` sin sesión confirma `avatarUrl` en la respuesta.
- **Pestaña nueva del navegador sin sesión** navega a `/explore`: la imagen real del avatar se renderiza en la tarjeta (no la inicial de fallback).
- Abrir el detalle (`/explore/{id}`) muestra la misma imagen real junto a "por QA Ticket092".
- Limpieza posterior: proyecto, fila de `user_profile` y cuenta de prueba borrados a mano vía SQL directo en las bases de DEV (bytes del avatar en MinIO quedan huérfanos bajo su key -- mismo tradeoff ya aceptado y documentado para thumbnails/referencias en tickets anteriores).
