// deploy: false -- todavía no hay Dockerfile/deploy real (el Technical
// Alpha, docs/definiciones/galgoth-studio-mvp.md, no despliega a ningún
// dominio real este ciclo, solo Docker Compose local). Sin vhostFile ni
// certbotDomains por el mismo motivo.
//
// buildAndTest corre frontend (ticket 002) y backend (ticket 003) en
// secuencia dentro del mismo closure -- corePipeline solo admite un
// buildAndTest por Jenkinsfile.
//
// Frontend: lint + tests (Vitest, cobertura lcov) + build + Sonar.
// Backend: ./gradlew build sonar -- build ya corre test (JUnit +
// Testcontainers, requiere docker.sock, Jenkins ya lo monta -- mismo
// patrón que mail-core-mc) y jacocoTestReport (dependencia explícita de
// la tarea sonar en build.gradle); toolchain Java 25 -- Jenkins ya tiene
// Temurin 25 instalado (mismo que auth-core-mc).
//
// Ambos con withSonarQubeEnv('sonarqube-vm') real -- corePipeline exige
// un análisis previo o la etapa "Quality Gate de SonarQube" falla con
// IllegalStateException (gotcha real encontrado en el primer build del
// ticket 002, documentado también en el skill bootstrap-proyecto). Usa
// el credential de Sonar YA configurado en Jenkins -- no depende de
// SONARQUBE_CLI_TOKEN_VM (CLI personal de Marco, mecanismo aparte).
//
// E2E (ticket 033, HU-23): la suite Playwright de aceptación (frontend/e2e/,
// scripts/e2e.sh) está completa y verificada -- corriendo LOCAL, en
// verde, de forma reproducible. **NO está wireada en este Jenkinsfile
// -- gap de infra real y documentado, no un olvido.** Se intentó a
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
// lo que se puede diagnosticar a ciegas leyendo logs. Ver el `## Hecho`
// del ticket 033 y docs/ARQUITECTURA.md para el detalle completo de las
// 6 rondas. Uso local: `./scripts/e2e.sh` desde la raíz del repo.
@Library('platform') _

corePipeline(
    projectName: 'galgoth-studio',
    deploy: false,
    buildAndTest: {
        withEnv(["PATH+SONAR=/opt/sonar-scanner/bin"]) {
            dir('frontend') {
                sh 'npm ci'
                sh 'npm run lint'
                sh 'npm run test:coverage'
                sh 'npm run build'
                withSonarQubeEnv('sonarqube-vm') {
                    sh 'sonar-scanner'
                }
            }
        }
        dir('backend') {
            withSonarQubeEnv('sonarqube-vm') {
                sh './gradlew build sonar'
            }
        }
        // E2E (ticket 033): NO corre acá -- ver el comentario de cabecera.
    }
)
