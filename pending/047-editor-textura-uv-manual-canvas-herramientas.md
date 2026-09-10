# 047 — Editor de textura/UV manual: canvas y herramientas

**Milestone:** M8 · **Depende de:** 002, 008, 016, 040, 046 · **HUs:** HU-24, HU-26, HU-27 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Alcance/HU-24/26/27). Pixel editor (canvas 2D/OffscreenCanvas) sobre el atlas de textura del mob, con el layout UV ya calculado como guía visual, herramientas estándar de edición de píxeles, y preview 3D en vivo reutilizando el `ThreeViewportService` singleton ya existente. No incluye selección cruzada cuboid↔UV (ticket 049) ni import de PNG (ticket 048) — solo el canvas y sus herramientas de pintado.

## Criterios de aceptación (TDD)
- Dado un mob con geometría ya usable (`revision_number >= 1`), cuando entro al tab "Textura", entonces el canvas muestra el atlas completo con el layout UV superpuesto como guía, y un selector de región (dropdown) resalta/enfoca la región elegida.
- Dado un mob SIN ninguna revisión guardada todavía (draft en memoria), cuando entro al tab Textura, entonces carga sobre el draft vacío, nunca bloqueado — mismo criterio ya resuelto para el editor de modelo (ticket 034).
- Dado que elijo un color y pinto con el pincel, cuando el trazo se aplica, entonces usa ese color exacto sin antialiasing (pixel-perfect, verificado a nivel de píxel en el test).
- Dado que uso la Cubeta sobre una región de color contiguo, cuando hago clic, entonces se rellena esa región contigua (flood-fill estándar) — nunca fuera de sus límites de color.
- Dado que uso el Eyedropper sobre un píxel, cuando hago clic, entonces el color activo pasa a ser el de ese píxel exacto.
- Dado que cambio el tamaño de pincel/borrador, cuando pinto, entonces el trazo respeta ese tamaño en píxeles del ATLAS, no en píxeles de pantalla (independiente del zoom del canvas).
- Dado que activo el toggle de cuadrícula, cuando lo activo, entonces se superpone una grilla visual — nunca se persiste como parte de la textura exportada (test que confirma que el bitmap subido/persistido no incluye la grilla).
- Dado que aplico cualquier trazo (pincel/borrador/cubeta), cuando el trazo se completa, entonces el preview 3D (mismo viewport singleton reutilizado, sin instanciar uno nuevo) refleja el cambio de inmediato.
- Cada herramienta produce sus cambios como `TexturePatchCommand` (ticket 046) — sin excepciones.

## Hecho
