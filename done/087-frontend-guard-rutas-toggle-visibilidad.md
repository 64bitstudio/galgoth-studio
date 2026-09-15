# 087 — Guard de rutas autenticadas + toggle de visibilidad (frontend)

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — cierre de HU-1/HU-2/HU-4 del lado del frontend. Con el
backend ya exigiendo login para "Mis proyectos" (ticket `085`), el
frontend hoy no redirige a nadie a `/login` — un visitante sin sesión
llega a `/projects` y solo vería errores `401` sin explicación.

**Depende de:** `085` (backend ya debe exigir `authenticated()` en las
rutas propias) y `086` (para el toggle de visibilidad).

## Alcance
- **Sí incluye:**
  - `router.beforeEach` nuevo: rutas `home`, `projects-dashboard`,
    `project-detail` (cuando el proyecto es propio), `mob-editor`,
    `export-screen`, `ai-mob-wizard` exigen
    `sessionStore.isAuthenticated` — si no, redirige a `/login`
    (guardando la ruta destino para volver tras loguearse, mismo patrón
    ya usado en tickets `080`-`083`).
  - `ProjectDetail.vue`: control de visibilidad (toggle o menú "Hacer
    público"/"Hacer privado") sobre `PATCH /api/projects/{id}/visibility`
    del ticket `086`.
- **No incluye:** la sección Explorar en sí (ticket `088`); que
  `projectsApi.ts`/`mobsApi.ts` adjunten `Authorization` — se adelantó
  como hotfix (ticket `089`) al descubrirse como regresión visible en
  vivo apenas se desplegó el ticket `084`, antes de que este ticket
  arrancara.

## Criterios de aceptación (TDD)
- Visitante sin sesión que navega a `/projects` es redirigido a
  `/login`.
- Usuario logueado puede togglear la visibilidad de un proyecto propio y
  ve el cambio reflejado sin recargar.
- Suite de tests del frontend en verde (Vitest).
- Verificación en vivo: navegación real contra DEV sin sesión iniciada
  confirmando el redirect, y con sesión confirmando el toggle.

## Hecho

**PR:** [#127](https://github.com/64bitstudio/galgoth-studio/pull/127), mergeado a `dev` (`04bea6b`). Deploy real a DEV verificado en verde (build 126 de Jenkins).

**Implementado tal cual el alcance:**
- `router.ts`: `meta.requiresAuth` + `beforeEach` — `home`, `projects-dashboard`, `mob-editor`, `export-screen`, `ai-mob-wizard` redirigen a `/login?redirect=<ruta>` sin sesión. `LoginView.vue` vuelve a `route.query.redirect` tras loguearse (validando que sea una ruta interna -- nunca una URL externa/protocol-relative, para no abrir una redirección abierta).
- **`project-detail` NO lleva `meta.requiresAuth`** (desviación deliberada del texto original del ticket, documentada): un proyecto `PUBLIC` es legítimamente visible sin sesión desde el ticket 085, y el ticket 088 (Explorar) depende de que siga siéndolo. El caso real que el ticket buscaba resolver ("proyecto PRIVATE, visitante sin loguear") se cubre reactivamente dentro de `ProjectDetail.vue`: si la carga falla y no hay sesión activa, redirige a login en vez de mostrar "no existe" como definitivo (el backend nunca distingue los dos casos, mismo `404`).
- Toggle "Hacer público"/"Hacer privado" en el menú ⋮ de `ProjectDetail.vue`, sobre el `PATCH` del ticket 086, más un badge de visibilidad junto al título. Actualiza el estado con la respuesta del PATCH directamente (sin volver a pedir `GET`), cumpliendo el AC "sin recargar".

**Hallazgo real (no estaba en el alcance original, se resolvió sobre la marcha):** `ProjectSummary`/`ProjectDetail` en el backend ya mandaban `visibility` desde el ticket 084 (`V5`), pero esa columna nunca se agregó a las interfaces TypeScript del frontend — nadie la había necesitado hasta este ticket. Se agregó (`ProjectVisibility = 'PRIVATE' | 'PUBLIC'`) junto con los demás cambios.

**Tests:** `router.spec.ts` nuevo (4 tests, contra el router real exportado, no una reimplementación paralela del guard) + 2 tests nuevos en `LoginView.spec.ts` (`?redirect=` interno se respeta; uno externo se ignora) + 2 tests nuevos en `ProjectDetail.spec.ts` (toggle real vía PATCH sin recargar; con sesión activa un 404 real sigue mostrando el mensaje explícito) + 1 test existente corregido para reflejar el nuevo comportamiento sin sesión (antes esperaba ver el error crudo; ahora se verifica el redirect a login) + 1 test nuevo en `projectsApi.spec.ts`. 775 tests frontend en verde, `npm run build` (`vue-tsc -b` real, no solo `--noEmit` -- ver memoria `vue-tsc-build-vs-noemit-mismatch`) limpio.

**Verificación en vivo contra DEV real (navegador real, Chrome, no simulada):**
- Cuenta de prueba real registrada contra `auth-dev.64bitstudio.com`.
- Navegación anónima a `https://studio-dev.galgoth.64bitstudio.com/projects` → redirige de inmediato a `/login?redirect=/projects` (captura de pantalla verificada).
- Login real vía el formulario (no la API directa) → aterriza en `/projects` (no en `/`), confirmando que `?redirect=` se respeta de punta a punta.
- Proyecto real creado ("QA Ticket 087"), nace con badge "Privado".
- Menú ⋮ → "Hacer público" → badge cambia a "Público" al instante, sin recargar la página (capturas antes/después verificadas), y el propio ítem del menú cambia a "Hacer privado" en la siguiente apertura.
- Alternado de vuelta a "Hacer privado" → badge vuelve a "Privado".
- Limpieza posterior: proyecto y cuenta de prueba borrados a mano vía SQL directo en las bases de DEV (mismas tablas y FKs que en el ticket 086).

**Nota de proceso (mejora al propio flujo, ticket 010 `dev-org-hooks-suite`):** durante el cierre se detectó una 5ª variante del gotcha ya documentado de `git add`/`git mv` (memoria `git-mv-stale-index-gotcha`): un `git add` con una lista de rutas donde una ya no existía (por un `git mv` previo) abortó el comando completo sin stagear NADA de la lista, y el `git status --short` inmediato se leyó mal (columna de staged vs no-staged). El primer commit de este ticket solo llevó 2 de 14 archivos reales -- detectado por `git log --stat` mostrando un conteo de archivos mucho menor al esperado, corregido con `git reset --soft` + un commit de seguimiento normal (nunca `push --force`, bloqueado por política del entorno). Memoria actualizada con la regla general: comparar siempre el conteo de archivos del commit contra lo que se esperaba tocar.
