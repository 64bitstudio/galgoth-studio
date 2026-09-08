// deploy: false -- todavía no hay Dockerfile/deploy real (el Technical
// Alpha, docs/definiciones/galgoth-studio-mvp.md, no despliega a ningún
// dominio real este ciclo, solo Docker Compose local). Sin vhostFile ni
// certbotDomains por el mismo motivo.
//
// buildAndTest (ticket in-process/002-sistema-diseno-base-visual-contract.md):
// primer código real del frontend (sistema de diseño base) -- lint +
// tests (Vitest, con cobertura lcov) + build + análisis de SonarQube.
//
// Corrección real (encontrada en el primer build, ver log de Jenkins):
// el contrato de corePipeline (platform/vars/corePipeline.groovy) exige
// que buildAndTest llame withSonarQubeEnv('sonarqube-vm') -- la etapa
// "Quality Gate de SonarQube" de la librería corre siempre que
// buildAndTest esté definido, sin análisis previo waitForQualityGate
// falla con IllegalStateException. Esto usa el credential de SonarQube
// YA configurado en Jenkins (mismo que auth-core-mc/mail-core-mc) --
// no depende de SONARQUBE_CLI_TOKEN_VM (ese es solo para el CLI
// personal de Marco en su Mac, un mecanismo distinto y no relacionado).
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
    }
)
