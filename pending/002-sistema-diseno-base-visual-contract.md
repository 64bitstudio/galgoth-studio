# 002 — Sistema de diseño base (Visual Contract)

**Milestone:** M0 · **Depende de:** 001 · **HUs:** —

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección "Visual Contract"). Implementar los tokens de diseño (dark graphite/charcoal, verde menta como acento funcional) y los componentes Vue base, fieles a los 12 mockups — precede a la implementación de cualquier pantalla productiva.

## Criterios de aceptación (TDD)
- Dado los tokens definidos (`--bg #0B0F14`, `--panel #111820`, `--surface #171F29`, `--accent #48E5A0`, etc.), cuando se renderiza cualquier componente base, entonces usa exclusivamente esos tokens (sin colores hardcodeados fuera del sistema).
- Dado un componente que representa un estado activo/seleccionado, cuando se muestra, entonces comunica el estado en verde menta **y** con una señal no-color (forma/ícono) — no solo color.
- Dado el sidebar global, cuando se renderiza, entonces contiene únicamente Nuevo proyecto / Mis proyectos / Recientes / Configuración — sin ítem "Volver".
- Dado un botón o control interactivo, cuando se inspecciona, entonces tiene un hit target ≥40px y un estado de foco visible por teclado.
- Dado un revisor comparando el showcase de componentes contra `mockups/00_all_views.png`, cuando lo hace, entonces no hay reinterpretación de estructura/layout sin aprobación (Visual Contract, punto 11 del documento de definición).
