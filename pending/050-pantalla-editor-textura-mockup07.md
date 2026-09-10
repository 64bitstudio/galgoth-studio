# 050 — Pantalla del editor de textura (mockup 07)

**Milestone:** M8 · **Depende de:** 047, 048, 049 · **HUs:** HU-41 · **Épica:** N (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (HU-41, Visual Contract vigente desde Fase 1+2). El tab "Textura" (presente pero deshabilitado desde el ticket 002/036) pasa a funcional, ensamblando los componentes ya construidos en 047/048/049 según el layout del mockup 07.

## Criterios de aceptación (TDD)
- Dado que entro al tab "Textura" de un mob, cuando la pantalla carga, entonces el tab pasa de "Próximamente" a funcional, siguiendo el layout del mockup 07 (UV Editor con selector de región y controles de color/tamaño a la izquierda, Vista previa 3D a la derecha).
- Dado el Visual Contract vigente (dark graphite/mint accent, viewport protagonista, colores propios del mob nunca reemplazados por el accent de UI), cuando se implementa la pantalla, entonces se respeta sin reinterpretar la estructura sin aprobación explícita del PO.
- Dado los tabs de workspace (Modelo | Textura | Animación), cuando este ticket se completa, entonces "Textura" deja de mostrarse deshabilitado — "Animación" permanece "Próximamente" (fuera de alcance de Fase 3).
- **Revisión visual en vivo obligatoria antes de cerrar** (skill `cerrar-ticket`): contrastar la pantalla corriendo contra el mockup 07 con Claude in Chrome, no solo el chequeo estático de accesibilidad de cada commit.

## Hecho
