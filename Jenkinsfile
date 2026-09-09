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
// si algo más básico ya falló. `npx playwright install --with-deps
// chromium` instala el navegador + dependencias de sistema (apt) --
// **riesgo de infra no verificado**: no se confirmó si el agente de
// Jenkins tiene permisos/paquetes para esto (decisión explícita del PO:
// agregarlo igual y resolverlo en el primer PR real si falla, en vez de
// dejar la suite sin ningún camino de CI).
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
                sh 'npx playwright install --with-deps chromium'
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
