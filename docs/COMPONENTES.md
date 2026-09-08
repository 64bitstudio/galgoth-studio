# Componentes — Galgoth Studio

Sin pantallas productivas implementadas todavía. Este archivo se completa conforme cada pantalla aterrice, siempre contrastada contra su mockup correspondiente (ver "Visual Contract" en `docs/definiciones/galgoth-studio-mvp.md`).

## Sistema de diseño base (ticket `002-sistema-diseno-base-visual-contract`)

Implementado en `frontend/src/design-system/`:

- **Tokens** (`tokens/tokens.css`): colores, tipografía (Inter Variable + JetBrains Mono, self-hosted vía `@fontsource`), escala de espaciado, radios, sombras, anillo de foco — valores fieles al master prompt §2.
- **Componentes base** (`components/`): `GButton` (primary/secondary/danger/ghost, hit target ≥40px), `GSidebar` (Inicio/Mis proyectos/Explorar/Plantillas arriba, Configuración/Usuario abajo — corregido contra el mockup real 2026-09-08, ver Addendum de `docs/definiciones/galgoth-studio-mvp.md`; "Nuevo proyecto" es la tarjeta CTA del dashboard, no un ítem de sidebar; sin "Volver"), `GStatusPill` (estado con ícono + color, nunca solo color), `GPanel` (superficie austera, deliberadamente sin sombra por defecto para desalentar anidar cards), `GTabs` (patrón Modelo/Textura/Animación, con Textura/Animación presentes pero deshabilitadas este ciclo).
- **Íconos** (`icons/`): set propio de línea simple (sin librería externa) — home, folder, búsqueda, grilla, engranaje, avatar, fieles a `mockups/01_inicio_mis_proyectos.png`. `IconNewProject` (voxel+"+") queda sin usar por ahora, reservado para la tarjeta CTA del ticket 021.
- **Gap conocido, documentado a propósito:** "Usuario" en `GSidebar` se renderiza como un ítem de nav simple, sin el tratamiento de tarjeta de perfil (avatar + subtítulo) que muestra el mockup — depende de datos reales que no existen todavía en este ticket.
- **Vitrina de desarrollo** (`design-system/Showcase.vue`, ruta `/dev/design-system`): no es una pantalla productiva — existe para contrastar visualmente el sistema contra `mockups/00_all_views.png` antes de construir pantallas reales sobre él.

Pantallas reales (dashboard, editor, wizard, etc.) todavía no existen — llegan en los tickets 021+.

## Viewport Three.js (ticket `008-threejs-viewport-solo-render-dev-harness`)

Implementado en `frontend/src/viewport/`:

- **`ThreeViewport.vue`**: wrapper que adjunta el canvas Three.js compartido (`ThreeViewportService`, singleton) a su contenedor mientras está montado. No crea su propio `WebGLRenderer` — sienta la base para que 016 (viewport interactivo) y 023 (thumbnails) reutilicen el mismo canvas sin agotar contextos WebGL.
- **`ViewportHarness.vue`** (ruta `/dev/viewport-harness`, NO enlazada desde la navegación productiva): abre el sample real Carcomido directamente en el viewport, sin pasar por creación de proyecto/mob (021/022, que no existen todavía). Se retira o queda oculta detrás de un flag una vez M3 esté listo, igual que `/dev/design-system`.
- Renderiza los 24 cuboids del sample (más un marcador esférico por cada uno de los 6 bones, en su pivote mundial compuesto — puede quedar visualmente oculto dentro de la geometría opaca, es cosmético) usando exclusivamente el `CoordinateSystemContract` para toda transformación.

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
