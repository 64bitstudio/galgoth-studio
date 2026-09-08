# 008 — Three.js: viewport de solo-render + development harness (M0-M2)

**Milestone:** M0 · **Depende de:** 004 · **HUs:** — (soporte técnico de M0-M2)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §3). Construir el viewport Three.js mínimo capaz de renderizar cuboids/bones desde un `MobProjectModel`, cargando `samples/carcomido_minecraft_cuboids.bbmodel` (convertido) como fixture temprana. Incluye un **development harness temporal**: debe ser posible abrir el sample Carcomido directamente en el editor sin depender todavía de CRUD/proyectos (021/022) — **no es UI productiva**, es una ruta de desarrollo/QA que se retira o queda oculta detrás de un flag una vez M3 esté listo.

## Criterios de aceptación (TDD)
- Dado el sample `carcomido_minecraft_cuboids.bbmodel` convertido a `MobProjectModel`, cuando se carga en el viewport, entonces se renderizan todos sus cuboids y bones en la jerarquía correcta (usando el `CoordinateSystemContract` de 004).
- Dado el development harness, cuando se accede a una ruta/comando de desarrollo (no enlazada desde la navegación productiva), entonces el sample Carcomido se abre directamente en el editor sin pasar por creación de proyecto/mob.
- Dado el viewport, cuando se navega entre pantallas que lo reutilizan (editor, resultado), entonces se comparte un único renderer/canvas en vez de instanciar uno nuevo (sienta la base para 016/023).
