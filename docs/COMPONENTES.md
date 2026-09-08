# Componentes — Galgoth Studio

Sin pantallas productivas implementadas todavía. Este archivo se completa conforme cada pantalla aterrice, siempre contrastada contra su mockup correspondiente (ver "Visual Contract" en `docs/definiciones/galgoth-studio-mvp.md`).

## Sistema de diseño base (ticket `002-sistema-diseno-base-visual-contract`)

Implementado en `frontend/src/design-system/`:

- **Tokens** (`tokens/tokens.css`): colores, tipografía (Inter Variable + JetBrains Mono, self-hosted vía `@fontsource`), escala de espaciado, radios, sombras, anillo de foco — valores fieles al master prompt §2.
- **Componentes base** (`components/`): `GButton` (primary/secondary/danger/ghost, hit target ≥40px), `GSidebar` (los 4 ítems fijos: Nuevo proyecto/Mis proyectos/Recientes/Configuración, sin "Volver"), `GStatusPill` (estado con ícono + color, nunca solo color), `GPanel` (superficie austera, deliberadamente sin sombra por defecto para desalentar anidar cards), `GTabs` (patrón Modelo/Textura/Animación, con Textura/Animación presentes pero deshabilitadas este ciclo).
- **Íconos** (`icons/`): set propio de línea simple (sin librería externa), con guiños de voxel/cuboid en los íconos de proyecto.
- **Vitrina de desarrollo** (`design-system/Showcase.vue`, ruta `/dev/design-system`): no es una pantalla productiva — existe para contrastar visualmente el sistema contra `mockups/00_all_views.png` antes de construir pantallas reales sobre él.

Pantallas reales (dashboard, editor, wizard, etc.) todavía no existen — llegan en los tickets 021+.

## Pantallas previstas (12, ver mockups/00_all_views.png del build pack)

1. Inicio / Mis proyectos
2. Nuevo proyecto / mob setup
3. Generación IA
4. Resultado IA (Descartar / Regenerar / Usar este modelo)
5. Editor de modelo (`hierarchy | viewport | inspector`)
6. Edición mediante IA
7. Editor de textura *(fuera de alcance del Technical Alpha)*
8. Generador IA de textura *(fuera de alcance del Technical Alpha)*
9. Animación *(fuera de alcance del Technical Alpha)*
10. Biblioteca de animaciones *(fuera de alcance del Technical Alpha)*
11. Exportación
12. Detalle de proyecto

Estructura de carpetas prevista en `frontend/src/` según `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §3).
