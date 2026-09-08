# 010 — `BBModelExporterV5`

**Milestone:** M1 · **Depende de:** 004, 005, 006 · **HUs:** HU-19, HU-21 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §7, formato v5 como camino principal). Implementar el exportador `.bbmodel` versión 5 (separación `groups`/`outliner` de Blockbench 5), en backend, operando sobre un `MobProjectModel` (geometría + bones, sin textura/animación funcional este ciclo).

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` simple (un cuboid, un bone), cuando se exporta, entonces produce un `.bbmodel` con `meta.format_version` correspondiente a v5 y `groups`/`outliner` correctamente separados.
- Dado un `MobProjectModel` con jerarquía padre-hijo de bones, cuando se exporta, entonces la jerarquía se refleja fielmente en `outliner` usando las conversiones del `CoordinateSystemContract` (004).
- Dado cualquier export, cuando se inspecciona el JSON resultante, entonces todos los UUIDs son únicos y las referencias de `groups`/`outliner` son válidas.
- Dado un cuboid con `from`/`to`/`rotation` no triviales, cuando se exporta, entonces los valores numéricos en el `.bbmodel` corresponden exactamente a la conversión definida en 004 (sin pérdida ni desfase de ejes/grados).

## Hecho

Implementado en `backend/src/main/java/com/galgothstudio/backend/domain/export/`:

- **`BBModelExporterV5.export(MobProjectModel)`**: función pura, retorna el JSON como `String`. Copia DIRECTA de campos (`origin`/`rotation` como `[x,y,z]`, `from`/`to` sin transformar) — per el ADR 0001, que ya fija que `MobProjectModel` y `.bbmodel` comparten unidades/ejes/convención de rotación por diseño; este exportador no vuelve a decidir eso, solo lo aplica.
- **Investigación previa a escribir código** (no se inventó el formato v5): el `.bbmodel` de muestra del proyecto (`format_version: "4.10"`) usa el formato VIEJO (groups embebidos inline en `outliner`, rotación de elementos como escalar+eje único). El formato v5 real se verificó leyendo el código fuente actual de Blockbench (`JannisX11/blockbench`, `js/formats/bbmodel.js` — constante `FORMATV = '5.0'`; `js/outliner/outliner.js` — `Outliner.toJSON()`; `js/outliner/types/group.js` — `Group.getSaveCopy()`/`getChildlessCopy()`; `js/outliner/types/cube.js` — `Cube.getSaveCopy()`), no asumido:
  - `groups[]`: array PLANO de bones (name/uuid/origin/rotation/export/isOpen), **sin** `children` — la jerarquía NO vive aquí.
  - `outliner[]`: árbol que mezcla dos formas en el mismo nivel — un string uuid suelto para un elemento hoja, o un objeto `{uuid, isOpen, children}` para un group. Implementado con una interfaz sellada (`BBOutlinerEntry` → `BBOutlinerLeaf`/`BBOutlinerGroupRef`) + un `JsonSerializer` dedicado, en vez de `Map<String,Object>` sin tipar.
  - Los cuboids (`Cube.getSaveCopy()`) SÍ escriben `rotation` como `[x,y,z]` (omitido por completo si es `[0,0,0]`) en el código fuente ACTUAL de Blockbench — la forma escalar+eje del sample del proyecto es una variante vieja/distinta, exactamente como ya lo advertía el ADR 0001 ("no algo que nuestro exportador produzca"). Los groups (bones), en cambio, SIEMPRE escriben `origin`/`rotation` explícitos, nunca los omiten (sin el guard que sí tienen los cuboids) — verificado en `getChildlessCopy()`.
- **`CuboidFaces`/`Face`/`Vec3`/`Vec4` del dominio se reutilizan directamente** para los elementos del `.bbmodel` — su shape JSON (`{uv:[u0,v0,u1,v1], texture}`, claves `north/south/east/west/up/down`) ya coincide exactamente con lo que Blockbench espera; no se crearon tipos paralelos redundantes.
- Campos opcionales del formato real (`variable_placeholders`, `display`, `backgrounds`, `history`) se omiten deliberadamente — el código fuente de Blockbench los trata como opcionales al cargar (cada uso detrás de un `if (model.xxx)`), su ausencia no dispara el diálogo de reparación.

**Tests**: 6 nuevos (`BBModelExporterV5Test`), 100% en verde — 54 tests totales en el módulo backend (`./gradlew build -x sonar`), 0 fallos. Cubren las 4 AC del ticket más un caso adicional (omisión de `rotation` cuando es `[0,0,0]`, verificado contra el código fuente real).

**Riesgo abierto heredado del ADR 0001, no resuelto en este ticket:** el orden exacto de composición de rotación (`R = Rz·Ry·Rx`) sigue siendo "convención documentada con evidencia comunitaria, no certeza absoluta" — este ticket implementa la copia directa que el ADR ya definía, pero la validación empírica contra un `.bbmodel` real exportado desde Blockbench (abrir/comparar) queda para el ticket 012 (fixtures reales), que es donde el propio ADR dice que debe resolverse. No se tuvo acceso a una instalación real de Blockbench en este entorno para una verificación visual directa.

**Fuera de alcance de este ticket (según su propio Objetivo):** textura (011), animación (fuera del Technical Alpha), endpoint REST para descargar el export (ningún ticket lo pide todavía — llega junto con la pantalla de exportación, ticket 032).
