// deploy: false -- todavía no hay Dockerfile/deploy real (el Technical
// Alpha, docs/definiciones/galgoth-studio-mvp.md, no despliega a ningún
// dominio real este ciclo, solo Docker Compose local). Sin vhostFile ni
// certbotDomains por el mismo motivo.
//
// buildAndTest (ticket in-process/002-sistema-diseno-base-visual-contract.md):
// primer código real del frontend (sistema de diseño base) -- lint +
// tests (Vitest) + build de verdad, no un placeholder vacío.
//
// Análisis de SonarQube NO incluido todavía a propósito: SONARQUBE_CLI_TOKEN_VM
// sigue pendiente de que Marco lo genere (ver pending/001-bootstrap-repo.md) --
// se agrega en un ticket posterior una vez que el registro del proyecto en
// sonarqube.64bitstudio.com deje de estar bloqueado.
@Library('platform') _

corePipeline(
    projectName: 'galgoth-studio',
    deploy: false,
    buildAndTest: {
        dir('frontend') {
            sh 'npm ci'
            sh 'npm run lint'
            sh 'npm run test'
            sh 'npm run build'
        }
    }
)
