// Ticket pending/001-bootstrap-repo.md — placeholder mínimo de este bootstrap.
// deploy: false porque todavía no existe Dockerfile ni lógica de negocio real
// (mismo patrón que usó mail-core-mc antes de su ticket 011 de pipeline real,
// ver platform/docs/ARQUITECTURA.md ticket 002 punto 8). Sin vhostFile ni
// certbotDomains a propósito: el Technical Alpha (docs/definiciones/
// galgoth-studio-mvp.md) no despliega a ningún dominio real este ciclo —
// solo Docker Compose local. Este Jenkinsfile se reemplaza por uno con
// buildAndTest real cuando aterrice el primer código de frontend/backend
// (tickets 004+).
@Library('platform') _

corePipeline(
    projectName: 'galgoth-studio',
    deploy: false
)
