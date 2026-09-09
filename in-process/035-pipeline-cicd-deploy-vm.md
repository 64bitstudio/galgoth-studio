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
  ya en uso: PROD 8089 / DEV 8090 / QA 8091).
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
(se completa al cerrar el ticket)
