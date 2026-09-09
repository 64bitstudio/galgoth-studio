# Blockbench BBModel fixture pack — Galgoth Studio

Fixtures de conformidad REALES (tickets 012/014) — 5 casos mínimos, cada uno en dos variantes:

- `v5/`: `meta.format_version = "5.0"`, jerarquía `groups[] + outliner` separada.
- `v4/`: `meta.format_version = "4.10"`, groups/bones embebidos dentro de `outliner` ("Export Legacy Project").

Casos:
1. `01_cuboid_simple_*` — un solo cuboid, sin jerarquía real.
2. `02_parent_child_hierarchy_*` — 2 bones anidados, con un cuboid en cada nivel.
3. `03_pivot_rotation_*` — pivot fuera del origen y rotación no-cero.
4. `04_multiple_cuboids_*` — 2 cuboids.
5. `05_uv_real_texture_*` — textura real 16×16 embebida + UV por cara.

La textura fuente también se incluye como `textures/fixture_real_texture.png`.

## Procedencia

Una primera versión de este paquete fue generada programáticamente (siguiendo la spec documentada
del formato `.bbmodel`, no exportada de Blockbench real) — su propio README lo admitía explícitamente
y describía el paso pendiente. El Product Owner abrió cada archivo `v5/*.bbmodel` en Blockbench 5.x
real, lo guardó normalmente, y usó "Export Legacy Project" para producir el equivalente `v4/*.bbmodel`.
Confirmado antes de aceptarlos como fixtures de conformidad: los archivos actuales contienen campos
idiosincrásicos reales de Blockbench que nuestro propio exportador nunca produce (`unhandled_root_fields`,
`multi_file_ruleset`, `allow_mirror_modeling`, `mirror_uv`, `selected`, metadata completa de textura) —
evidencia de que vienen de la app real, no de una reconstrucción a partir de la misma spec que ya usamos
para programar el exportador (lo cual solo probaría que somos consistentes con nosotros mismos).

## Qué valida

`backend/src/test/java/.../domain/export/blockbenchreal/BlockbenchRealFixturePackConformanceTest.java`
(TEST-ONLY, ver el docstring de `BlockbenchBbmodelTestParser`): cada archivo parsea sin pérdida relevante,
valida contra el JSON Schema formal, y se re-exporta sin errores en su propio formato (v5→`BBModelExporterV5`,
v4→`BBModelExporterV4`) — más un test dedicado por caso confirmando la propiedad específica que demuestra
(jerarquía real, pivot+rotación no triviales, múltiples cuboides, textura real aplicada vía UV).
