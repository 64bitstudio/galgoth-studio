# 004 — MobProjectModel: dominio + CoordinateSystemContract

**Milestone:** M0 · **Depende de:** 001 (avanza en paralelo a 003 — DB y dominio son independientes) · **HUs:** —

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 7, `MobProjectModel`). Definir los tipos TS + DTOs Java + JSON Schemas del contrato compartido en `contracts/`, y formalizar el **`CoordinateSystemContract`** canónico que todo el resto del sistema (Geometry Engine, AutoUv, viewport Three.js, exportador) debe respetar sin ambigüedad.

El `CoordinateSystemContract` debe fijar explícitamente:
- unidades (Minecraft pixels, según `samples/model_spec_example.json`)
- ejes X/Y/Z y su orientación
- semántica de `from`/`to` (esquinas del cuboid)
- `origin`/pivot (punto de rotación, distinto del bounding box)
- grados vs. radianes en cada capa (dominio, Three.js, `.bbmodel`)
- orden de aplicación de rotación (rotation order)
- cómo se compone la transformación padre-hijo (bone → bone hijo → cuboid)
- tabla de mapeo explícita Three.js ↔ `MobProjectModel` ↔ `.bbmodel` (qué convierte a qué, y dónde)

## Criterios de aceptación (TDD)
- Dado `samples/model_spec_example.json`, cuando se valida contra el JSON Schema de `MobProjectModel`, entonces pasa sin errores.
- Dado un tipo TS y su DTO Java equivalente, cuando se serializa/deserializa el mismo objeto en ambos lados, entonces produce JSON estructuralmente idéntico.
- Dado el `CoordinateSystemContract` documentado (ADR en `docs/adr/`), cuando otro ticket (Geometry Engine, AutoUv, viewport, exportador) necesita convertir una coordenada/rotación entre capas, entonces usa exclusivamente las funciones de conversión de este contrato — ningún otro módulo reimplementa su propia conversión de ejes/grados/orden de rotación.
- Dado un caso de prueba con rotación de bone padre + cuboid hijo, cuando se aplica la composición de transformaciones descrita en el contrato, entonces el resultado coincide con el comportamiento esperado de Blockbench/Minecraft (verificado contra `samples/carcomido_minecraft_cuboids.bbmodel`).
- Dado los campos `texture`, `uv`, `animations`, `exportSettings` de `MobProjectModel`, cuando se inspecciona el tipo, entonces existen en el esquema aunque no tengan UI/lógica funcional este ciclo (Fase 3/4).
