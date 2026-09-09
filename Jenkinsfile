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
// E2E (ticket 033, HU-23): `scripts/e2e.sh` levanta el stack real
// completo (Docker Compose Postgres+MinIO, backend con providers mock,
// frontend) y corre la suite Playwright de aceptación -- después de
// lint/test/build/Sonar de ambos, para no gastar tiempo de stack real
// si algo más básico ya falló.
//
// **Hallazgo real confirmado en el primer PR (033)**: `npx playwright
// install --with-deps chromium` falla ("su: Authentication failure")
// -- el agente de Jenkins no tiene sudo/root, y `--with-deps` necesita
// root para `apt install` las librerías de sistema de Chromium. Sin
// `--with-deps` (solo descarga el binario del navegador, sin tocar
// paquetes de sistema) -- si el agente ya tiene las librerías
// necesarias (glibc/libnss3/libatk/etc., típico en una imagen Ubuntu
// completa) esto alcanza; si no, Chromium fallará al LANZARSE (no al
// instalarse) y haría falta una imagen Docker con Playwright pre-armado
// (`mcr.microsoft.com/playwright:*`) -- cambio de infra mayor (agente
// Docker dedicado), fuera de alcance de este ticket, a decidir con VoBo
// sobre `platform` si este intento más liviano tampoco alcanza.
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
                sh 'npx playwright install chromium'
            }
        }
        dir('backend') {
            withSonarQubeEnv('sonarqube-vm') {
                sh './gradlew build sonar'
            }
        }
        sh './scripts/e2e.sh'
    }
)
