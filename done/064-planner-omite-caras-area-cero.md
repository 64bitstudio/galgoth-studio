# 064 — Fix crítico: el planner explota con caras de área cero (geometría real, no dato corrupto)

**Milestone:** post-M8 (hallazgo en producción/dev) · **Relacionado:** 053, 059-063 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Reportado por el PO tras la cadena 059-063: "Generar con IA" sobre `head` funcionó de punta a punta, pero **"Modelo completo" falló** con:
```
No se pudo recortar el sheetRect [52,0,0,2] de cuboid '77fa3970-b105-45e6-a1c2-00ffaf9a5979' cara NORTH -- fuera de los límites de la imagen generada (1408x480).
```
Auditado el atlas real del mob `Carcomido_v1` en `studio-dev`: ese cuboid (un elemento decorativo delgado, parte del efecto visual de "rayo/chispa") tiene 4 de sus 6 caras con `atlasUvRect` de ÁREA CERO -- ej. `rect: [34,18,34,20]` (x0==x1, ancho 0). **Esto es geometría legítima** (un cuboid con una dimensión colapsada a 0 para simular una superficie plana/delgada), no un dato corrupto -- `UvLayoutSelector`/`StableUvStrategy` (041/042) lo empaquetan igual. El planner incluía esa cara tal cual en el plan, y `TextureSheetSlicer` explotaba al intentar `BufferedImage.getSubimage(x, y, 0, height)` -- ancho/alto 0 es inválido para un raster de Java, sin importar si el rect "cabe" dentro de los límites de la imagen.

## Criterios de aceptación (TDD)
- `TextureGenerationSheetPlanner` omite del plan cualquier cara cuyo `atlasUvRect` tenga ancho o alto cero -- no tiene ningún píxel visible que texturear, se salta sin generar placement ni disparar ninguna llamada de IA para ella.
- Un bone cuyas caras sean TODAS de área cero devuelve una lista VACÍA de sheets -- nunca un sheet fantasma 0x0 (`ShelfBinPacker.pack([])` siempre agrega un bin final aunque no se haya colocado ningún item; sin este corte, se hubiera disparado una llamada real a la API para generar literalmente nada).
- Las demás caras del mismo cuboid (con área real) se procesan con total normalidad.
- Test de regresión con las dimensiones EXACTAS del caso real (`[34,18,34,20]`).
- Suite completa de backend en verde.

## Hecho
Implementado:
- `backend/src/main/java/.../aiorchestrator/texture/TextureGenerationSheetPlanner.java`: `buildRawPlacements` salta (continue) cualquier cara con `width<=0 || height<=0`; `plan()` corta temprano devolviendo `List.of()` si `rawPlacements` queda vacío tras el filtro (evita el bin fantasma de `ShelfBinPacker`). Javadoc documenta el hallazgo completo con el cuboid/rect real.
- `backend/src/main/java/.../aiorchestrator/texture/ShelfBinPacker.java`: Javadoc de `pack()` documenta explícitamente la invariante "items vacío devuelve un bin vacío, nunca una lista vacía de bins" (la causa de por qué el corte temprano en el planner es necesario).
- `backend/src/test/java/.../aiorchestrator/texture/TextureGenerationSheetPlannerTest.java`: +2 tests -- una cara de área cero se omite (5 de 6 caras quedan en el plan), y un bone con TODAS sus caras de área cero devuelve lista vacía de sheets.

**TDD real**: confirmado que ambos tests fallan contra el código sin este fix (`git stash`, re-corrida, 2 `AssertionError` reales) antes de aplicar la corrección.

**Relacionado y complementario**: ver también `done/065-slicer-escala-sheetrect-al-tamano-real.md` -- descubierto y corregido en la misma investigación (la razón por la que "Modelo completo" progresó más lejos que antes, revelando ESTE hallazgo nuevo, es que 059-063 ya arreglaron los bugs que bloqueaban antes).

**Tests**: backend en verde (ver conteo final en 065, mismo PR). `./gradlew clean test` corrido localmente antes de push.

**Verificación en vivo pendiente**: repetir "Generar con IA" con "Modelo completo" contra `studio-dev` una vez mergeado y desplegado.
