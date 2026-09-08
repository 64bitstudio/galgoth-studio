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
    }
)
