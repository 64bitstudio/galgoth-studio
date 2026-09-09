# 035 — Pipeline CI/CD y despliegue real a la VM compartida

## Objetivo
Conectar Galgoth Studio a la infraestructura compartida ya establecida
(ver memoria de equipo `saas-paas-cores-strategy`/`vm-deploy-infra-roadmap`
y el patrón ya vigente en `auth-core-mc`, `mail-core-mc`,
`texture-studio-mc`): imagen Docker real, subdominio propio, y despliegue
automático dev→qa, con promoción manual a prod exclusiva de Marco — mismo
modelo de 3 ramas (`dev`/`qa`/`prod`) que el resto de los cores, sin
reinventar nada.

Subdominio confirmado por el Product Owner: `studio.galgoth.64bitstudio.com`
(prod) / `studio-qa.galgoth.64bitstudio.com` (qa) /
`studio-dev.galgoth.64bitstudio.com` (dev) — subdominio de tercer nivel
bajo `galgoth.64bitstudio.com`, no el patrón `<slug>[.-qa][-dev]` plano
usado por los cores anteriores.

Decisión confirmada por el PO: el backend corre con la API key **real**
de Anthropic en los 3 ambientes (sin forzar el provider mock en dev/qa) —
implica gasto real de la cuenta de Marco en cualquier generación/edición
de IA que se dispare contra esos ambientes.

## Criterios de aceptación (TDD)
- Una sola imagen Docker (`backend/Dockerfile`, contexto fijo `./backend`
  por contrato de `corePipeline`) sirve la SPA de Vue (build de
  producción) Y la API (`/api/**`) desde el mismo puerto — mismo patrón ya
  usado en `texture-studio-mc`, adaptado a Spring Boot: el build de la
  imagen la levanta en un contenedor local (`docker run`) y responde 200
  tanto en `/actuator/health` como sirviendo `index.html` en una ruta de
  la SPA (ej. `/projects`) y en un deep-link con parámetros (ej.
  `/projects/x/mobs/y/edit`, sin 404 en el refresh).
- `deploy/docker-compose.{dev,qa,prod}.yml` + `deploy/cleanup.sh` +
  `deploy/.env.{dev,qa,prod}.example` + `deploy/vm-infra/nginx/
  galgoth-studio.conf` existen, siguiendo el mismo patrón ya usado por los
  3 cores anteriores (Postgres+MinIO persistentes, healthcheck contra
  `/actuator/health`, labels de Traefik, red `edge`, nombre de proyecto
  de Compose explícito, puertos de host reservados siguiendo la secuencia
  ya en uso: PROD 8089 / DEV 8091 / QA 8092 — NO 8090, ocupado por
  Jenkins mismo, hallazgo real del primer deploy).
- `Jenkinsfile` con `deploy: true` (ya no `false`), `vhostFile`,
  `certbotDomains` para los 3 subdominios, y el paso de build que aterriza
  el bundle de producción del frontend + el schema compartido de
  `contracts/` dentro del contexto de build fijo de `./backend` (mismo
  hallazgo/patrón que la copia de `frontend/dist` en `texture-studio-mc`,
  adaptado porque este backend además necesita `contracts/schemas` en su
  classpath — ver `build.gradle`, tarea `processResources`, que hoy asume
  un checkout completo y rompe si el contexto de Docker solo trae
  `backend/`).
- Registros DNS reales (Cloudflare, sin proxy — mismo patrón que
  `auth`/`mailcore`/`texture-studio`) para los 3 subdominios, apuntando a
  la VM (`159.54.153.37`).
- Secreto real `DB_PASSWORD` en Vault (`secret/galgoth-studio/{dev,qa,prod}`)
  y archivo real `/home/ubuntu/secrets/galgoth-studio/.env.{dev,qa,prod}`
  en la VM con la API key real de Anthropic y las credenciales reales de
  MinIO (no las de desarrollo local) — ninguno de los dos committeado.
- Push real a `dev` deja `https://studio-dev.galgoth.64bitstudio.com`
  respondiendo 200 con TLS válido desde fuera de la VM, sirviendo la SPA y
  aceptando una llamada real a la API (verificado con curl externo, no
  solo "debería funcionar").
- QA promovido por Claude (mismo mecanismo ya usado en los demás cores —
  merge `dev`→`qa` vía `gh`/`git`), verificado igual que dev.
- PROD queda pendiente de la aprobación explícita de Marco (gate manual
  del propio pipeline, `corePipeline` ya lo exige) — no se fuerza ni se
  simula esa aprobación.

## Hecho

Todos los criterios de aceptación cumplidos, verificados en vivo contra la infra real (no solo local):

- **Imagen única**: `backend/Dockerfile` (multi-stage, Temurin 25) sirve la SPA de Vue y la API desde el mismo puerto. El contexto de build fijo de `corePipeline` (`./backend`) nunca ve `frontend/`/`contracts/` -- el `Jenkinsfile` aterriza ahí el bundle de producción del frontend (`VITE_API_BASE_URL` vacío) y `contracts/schemas` justo antes del build de imagen. Verificado local con `docker build`+`docker run` real antes de cada commit, y de nuevo en vivo en la VM.
- **`SpaResourceConfig`**: fallback real a `index.html` para las rutas de Vue Router. Un primer intento con regex por convención de nombres se detectó roto ANTES de commitear (capturaba también los assets reales, `text/html` en vez del JS real) -- corregido con un `PathResourceResolver` que pregunta si el archivo existe de verdad.
- **`deploy/docker-compose.{dev,qa,prod}.yml` + `cleanup.sh` + `.env.{dev,qa,prod}.example` + vhost de nginx**: mismo patrón que los 3 cores anteriores. Puertos finales: PROD 8089 / DEV 8091 / QA 8092.
- **`Jenkinsfile`**: `deploy: true`, `vhostFile`/`certbotDomains` para `studio[.-qa][-dev].galgoth.64bitstudio.com`.
- **DNS real** (Cloudflare, 3 registros A sin proxy → `159.54.153.37`) y **certificado TLS real** (Let's Encrypt vía `certbot`, los 3 subdominios).
- **Secretos reales**: `DB_PASSWORD` en Vault (`secret/galgoth-studio/{dev,qa,prod}`, vía el AppRole `platform-admin`, sin tocar el token root) + `ANTHROPIC_API_KEY`/credenciales de MinIO reales en `/home/ubuntu/secrets/galgoth-studio/.env.{dev,qa,prod}` -- ninguno committeado.
- **DEV y QA verificados end-to-end desde fuera de la VM**: `https://studio-dev.galgoth.64bitstudio.com` y `https://studio-qa.galgoth.64bitstudio.com` responden 200 en `/actuator/health`, sirven la SPA real (deep-link sin 404, asset real `text/javascript`) y `/api/projects` real -- verificado con `curl` externo.
- **QA promovido por Claude** (`dev`→`qa`, fast-forward, mismo mecanismo que los demás cores).
- **PROD queda pendiente de la aprobación manual de Marco** -- `corePipeline` ya pausó el pipeline en el gate `input` exclusivo de él (timeout 7 días). Comportamiento esperado del AC, no un bloqueo.

### Addendum (mismo día, tras el cierre): bug real encontrado por Marco navegando la app real

Marco abrió `https://studio-dev.galgoth.64bitstudio.com` en el navegador real y encontró que "Crear proyecto" fallaba (`ERR_CONNECTION_REFUSED` contra `http://localhost:8080/api/projects` en DevTools) -- el frontend desplegado SÍ tenía el fallback de desarrollo local baked-in, pese a que el AC de "API real" arriba se marcó cumplido. **Gap real en mi propia verificación**: probé `/api/projects` con `curl` directo contra el subdominio (mismo origen, ruta relativa correcta) -- eso confirma que la API responde, pero nunca ejercita qué URL usa de verdad el JS del navegador. Nunca abrí la app real en un navegador contra el subdominio desplegado antes de marcar el AC como cumplido.

**Causa raíz real**: `withEnv(['VITE_API_BASE_URL='])` (Groovy, valor vacío) no propagaba la variable al proceso `sh` -- Vite veía `VITE_API_BASE_URL` como `undefined` (no como string vacío), así que `?? 'http://localhost:8080'` sí resolvía al literal y quedaba embebido en el bundle. Confirmado inspeccionando el JS servido (`var e=\`http://localhost:8080\`;`). Fix real: `sh 'VITE_API_BASE_URL= npm run build'` (asignación inline de shell, sin indirección de `withEnv`) -- verificado localmente ANTES de commitear, y de nuevo en vivo tras el redeploy (el chunk `ApiError-*.js` ya no tiene ningún literal de URL -- se volvió `""`, minificado a nada -- y una llamada real `POST /api/projects` desde curl contra el subdominio, simulando lo que el navegador ahora hace, creó un proyecto real, borrado después de la verificación).

**Lección para el próximo despliegue que necesite una env var de build-time**: no confiar en `withEnv` de Jenkins para un valor vacío -- usar asignación inline de shell (`VAR=valor comando`), que es el mecanismo que de verdad se probó y funcionó tanto local como en CI.

**Hallazgo real encontrado en el primer deploy (no en ningún test)**: el bloque de puertos reservado (PROD 8089/DEV 8090/QA 8091, siguiendo la secuencia de los 3 cores anteriores) nunca se cruzó contra los puertos de la infra COMPARTIDA misma -- 8090 ya lo publica Jenkins mismo (`127.0.0.1:8090`), nunca documentado en la tabla "Convenciones de la VM" de `auth-core-mc` (que solo listaba cores de aplicación). `docker compose up -d` falló con `port is already allocated` en el primer intento (build 36). Corregido: DEV→8091/QA→8092 (PROD 8089 no colisionaba) vía PR #38, y la tabla de convenciones corregida en `auth-core-mc#95` (con la lista completa de puertos de infra compartida) para que el próximo core no repita el mismo hallazgo a ciegas.

**Decisión real tomada con el PO durante el trabajo**: `galgoth.64bitstudio.com`/`store.galgoth.64bitstudio.com` ya existían apuntando a la VM de Diana (Minecraft server), no a la VM de plataforma -- se confirmó explícitamente con el PO que `studio.galgoth.64bitstudio.com` va a la VM de plataforma (159.54.153.37), no a la de Diana, antes de crear ningún registro DNS.

Sin hallazgos automáticos de QA/Sonar pendientes en ninguno de los 3 PRs (#37, #38) -- ambos gates de Sonar (frontend/backend) verificados manualmente contra `sonarqube-db` en cada uno (gap conocido del ticket 008).
