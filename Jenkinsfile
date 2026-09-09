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
// E2E (ticket 033, HU-23): la suite Playwright de aceptación corre
// después de lint/test/build/Sonar de ambos, para no gastar tiempo de
// stack real si algo más básico ya falló.
//
// **Dos hallazgos reales de infra, resueltos en los primeros intentos
// de CI de 033** (ver docs/ARQUITECTURA.md y el `## Hecho` del ticket
// para el detalle completo): (1) `--with-deps` de `playwright install`
// necesita root, el agente no lo tiene -- se instala sin esa flag.
// (2) el puerto fijo de MinIO (9000) colisionaba con otro job en el
// agente compartido -- resuelto con puerto dinámico + descubrimiento
// (`scripts/e2e-up.sh`).
//
// **Tercer hallazgo, el más profundo**: aun con el navegador instalado,
// Chromium no podía LANZARSE en el agente (`libglib-2.0.so.0` faltante,
// sin root para instalarla vía apt). `scripts/e2e.sh` se partió en
// `e2e-up.sh` (levanta Docker Compose + backend + frontend, sin
// bloquear) / `e2e-down.sh` (los apaga) para que el paso que SÍ toca
// Chromium corra en un contenedor Docker con Playwright + sus
// dependencias YA resueltas (`mcr.microsoft.com/playwright`, imagen
// oficial) -- `--network host` para que ese contenedor vea el backend/
// frontend ya levantados en el agente como si fueran locales (`localhost`
// compartido). El resto (Compose/backend/frontend/gradle/npm) sigue
// corriendo en el agente normal -- la imagen de Playwright no tiene
// JDK/Gradle. `finally` garantiza `e2e-down.sh` incluso si Playwright
// falla dentro del contenedor.
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
        sh './scripts/e2e-up.sh'
        try {
            docker.image('mcr.microsoft.com/playwright:v1.63.0-noble').inside('--network host') {
                dir('frontend') {
                    sh 'npx playwright test'
                }
            }
        } finally {
            sh './scripts/e2e-down.sh --dump-logs'
        }
    }
)
