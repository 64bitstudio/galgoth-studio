// Ticket 035: reemplaza el Jenkinsfile mínimo (`deploy: false`, sin
// vhost/certbot) -- ahora que existen backend/Dockerfile,
// deploy/docker-compose.{dev,qa,prod}.yml y deploy/cleanup.sh, se activa
// el pipeline de aplicación real: build + test con cobertura + análisis
// SonarQube + build de imagen + deploy dev/qa/prod + promoción manual a
// PROD (gate exclusivo de Marco, sin cambios -- lo gestiona corePipeline
// mismo). Mismo modelo ya vigente en auth-core-mc/mail-core-mc/
// texture-studio-mc, sin reinventar nada.
//
// buildAndTest corre frontend (ticket 002) y backend (ticket 003) en
// secuencia dentro del mismo closure -- corePipeline solo admite un
// buildAndTest por Jenkinsfile.
//
// Frontend: lint + tests (Vitest, cobertura lcov) + build + Sonar.
// `VITE_API_BASE_URL` vacío SOLO para el build que termina empaquetado
// en la imagen -- en despliegue real, frontend y backend comparten
// origen (mismo contenedor, mismo puerto, ver
// backend/Dockerfile/SpaResourceConfig), así que las llamadas a la API
// deben ser rutas relativas ("/api/..."), no "http://localhost:8080"
// (el default de desarrollo local, ver frontend/src/api/apiConfig.ts).
// No afecta a Sonar (analiza fuente + cobertura, no el contenido del
// bundle).
//
// Hallazgo real (primer deploy a DEV, verificado en vivo -- el bundle
// desplegado SÍ traía "http://localhost:8080" baked-in, DevTools
// mostraba `ERR_CONNECTION_REFUSED` contra localhost:8080 desde
// studio-dev.galgoth.64bitstudio.com): `withEnv(['VITE_API_BASE_URL='])`
// (Groovy, un solo elemento con valor vacío) NO propagó la variable al
// proceso `sh` -- confirmado real, no teórico (probado localmente con
// `VITE_API_BASE_URL= npm run build` y SÍ funcionaba ahí). Sea por cómo
// `EnvActionImpl`/`EnvVars` de Jenkins tratan un par "KEY=" con valor
// vacío, o por cómo el step `sh` hereda ese entorno, el resultado real
// fue que Vite viera `import.meta.env.VITE_API_BASE_URL` como
// `undefined` (no como string vacío) y el `?? 'http://localhost:8080'`
// del código SÍ se resolviera al literal, quedando embebido en el
// bundle. Fix: asignación inline de shell (`VAR= comando`), sin
// indirección de `withEnv` -- el mismo mecanismo que sí funcionó en la
// verificación manual.
//
// Tras el build del frontend, dos copias ANTES de que corePipeline
// invoque `docker build ./backend` (contexto de build fijo, nunca ve
// directorios hermanos -- ver la nota de cabecera de backend/Dockerfile
// para el detalle completo de por qué ambas son necesarias):
//   1. `frontend/dist` -> `backend/src/main/resources/static/` (Spring
//      Boot sirve classpath:/static automáticamente).
//   2. `contracts/schemas` -> `backend/contracts/schemas` (lo necesita
//      `build.gradle`, tarea `processResources`, dentro del build de la
//      imagen).
// Ambas rutas son gitignored -- se recrean en cada build, nunca
// contenido real de git.
//
// Backend: Java 25/Temurin explícito (la imagen de Jenkins solo trae
// JDK 21 para correr Jenkins mismo -- mismo hallazgo real ya documentado
// en auth-core-mc) + `./gradlew build sonar` (build ya corre test JUnit
// + Testcontainers, requiere docker.sock, Jenkins ya lo monta -- mismo
// patrón que auth-core-mc/mail-core-mc; jacocoTestReport es dependencia
// explícita de la tarea sonar en build.gradle).
//
// Ambos con withSonarQubeEnv('sonarqube-vm') real -- corePipeline exige
// un análisis previo o la etapa "Quality Gate de SonarQube" falla con
// IllegalStateException.
//
// vhostFile/certbotDomains: subdominio de tercer nivel confirmado por el
// PO -- studio[.-qa][-dev].galgoth.64bitstudio.com (no el patrón plano
// <slug>[.-qa][-dev].64bitstudio.com de los 3 cores anteriores). Ver
// deploy/vm-infra/nginx/galgoth-studio.conf.
//
// containerPort/healthPath/healthyPattern: se omiten -- los defaults de
// corePipeline (8080, /actuator/health, '"status":"UP"') ya son
// correctos para este backend Spring Boot Actuator, sin necesidad de
// overrides como los que sí necesitó mail-core-mc (NestJS)/
// texture-studio-mc (Express).
//
// E2E (ticket 033, HU-23; ampliada en el ticket 056, HU-43, Fase 3): la
// suite Playwright de aceptación (frontend/e2e/, scripts/e2e.sh, 2
// specs -- Technical Alpha + Fase 3) está completa y verificada --
// corriendo LOCAL, en verde, de forma reproducible (el ticket 056
// además encontró y cerró 3 bugs reales pre-existentes -- SSE, CORS,
// mock de IA -- que impedían correrla de punta a punta, ver
// docs/ARQUITECTURA.md). **NO está wireada en este Jenkinsfile -- gap
// de infra real y documentado, no un olvido.** Se intentó a
// fondo (6 rondas de CI real, 5 hallazgos resueltos: permisos de
// `playwright install --with-deps`, colisión de puerto fijo de MinIO,
// Chromium sin librerías de sistema para lanzarse, `--network host` vs.
// `--network container:<agente>` en la topología Docker-outside-of-
// Docker del agente) hasta toparse con un problema de red que persistió
// incluso haciendo explícito el CORS del backend: el preflight OPTIONS
// desde un navegador corriendo en el contenedor hermano de Playwright
// vuelve sin cabeceras CORS -- nunca reproducido en desarrollo local
// (mismo código, misma config, docenas de verificaciones en vivo esta
// sesión). Mismo tipo de gap que el de Sonar del ticket 008: real,
// documentado, requiere investigación con acceso directo al agente de
// Jenkins o un cambio de infra mayor (imagen Docker todo-en-uno con
// JDK+Node+Chromium+deps, evitando la topología de contenedor hermano
// por completo) -- decisión que le corresponde a `platform`, fuera de
// lo que se puede diagnosticar a ciegas leyendo logs. El PO (Marco)
// decidió explícitamente el 2026-09-09 aceptar este gap como riesgo
// conocido y cerrar el ticket 033 así (`done/033-...`), en vez de seguir
// invirtiendo en el diagnóstico de red del agente compartido -- retomable
// como ticket nuevo si se decide investigar más adelante. El ticket 056
// (mismo día) heredó la misma decisión sin reabrir el diagnóstico, al
// ampliar esta misma suite para Fase 3. Ver el `## Hecho` de ambos
// tickets y docs/ARQUITECTURA.md para el detalle completo. Uso local:
// `./scripts/e2e.sh` desde la raíz del repo.
@Library('platform') _

corePipeline(
    projectName: 'galgoth-studio',
    vhostFile: 'deploy/vm-infra/nginx/galgoth-studio.conf',
    certbotDomains: ['studio.galgoth.64bitstudio.com', 'studio-qa.galgoth.64bitstudio.com', 'studio-dev.galgoth.64bitstudio.com'],
    buildAndTest: {
        withEnv(["PATH+SONAR=/opt/sonar-scanner/bin"]) {
            dir('frontend') {
                sh 'npm ci'
                sh 'npm run lint'
                sh 'npm run test:coverage'
                sh 'VITE_API_BASE_URL= npm run build'
                withSonarQubeEnv('sonarqube-vm') {
                    sh 'sonar-scanner'
                }
            }

            sh '''
                rm -rf backend/src/main/resources/static
                mkdir -p backend/src/main/resources/static
                cp -r frontend/dist/. backend/src/main/resources/static/

                rm -rf backend/contracts
                mkdir -p backend/contracts
                cp -r contracts/schemas backend/contracts/schemas
            '''
        }

        withEnv([
            "JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-arm64",
            "PATH=/usr/lib/jvm/temurin-25-jdk-arm64/bin:${env.PATH}"
        ]) {
            dir('backend') {
                withSonarQubeEnv('sonarqube-vm') {
                    sh './gradlew build sonar'
                }
            }
        }
        // E2E (ticket 033, ampliada en el 056): NO corre acá -- ver el comentario de cabecera.
    }
)
