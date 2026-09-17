# 117 — El preview 3D no muestra la textura, aunque el canvas 2D sí

## Objetivo
Encontrado al verificar el ticket 115 en vivo sobre `Carcomido v3` (`studio-dev`), ya con el 115 desplegado: el editor de textura pinta el atlas **correctamente** en el canvas 2D (47.968 píxeles opacos, idénticos al PNG persistido), pero el modelo 3D no muestra esa textura.

Dos comportamientos distintos, medidos en la misma sesión:
- **Preview 3D de la pestaña Textura** (HU-26, "textura 3D en vivo"): el mob se ve **negro**, sin las grietas violetas ni el tono verdoso que el atlas claramente tiene. No cambia tras una segunda carga, así que no es la carrera de pintado que arregló el 115.
- **Viewport de la pestaña Modelo**: el mob se ve **gris plano**, o sea sin textura aplicada en absoluto.

Que sean distintos entre sí es la pista: el preview de Textura parece estar aplicando algo (queda negro, no gris), mientras que el de Modelo no aplica nada.

## Alcance
**Incluye:**
- Determinar si el viewport de la pestaña **Modelo** debe mostrar la textura o si el gris plano es intencional (editor de geometría). Si es intencional, se documenta y se cierra esa mitad; si no, se arregla.
- Arreglar el preview 3D de la pestaña **Textura**, que sí debe mostrarla por HU-26.
- Diagnosticar por qué queda negro en vez de mostrar el contenido: `syncDataTexture` construye un `DataTexture` sobre `atlas.pixels` y llama a `applyPreviewModel()`; hay que verificar si el `threeViewportService` lo recibe, si el material lo usa, y si las UV del modelo muestrean donde hay contenido (el atlas mide 1024 de alto y el contenido vive solo en las ~360 filas superiores).

**No incluye:**
- El canvas 2D (arreglado y verificado en el 115).
- La calidad de la textura generada (caras chicas sin contenido: ver hallazgo 1 del `done/114`).

## Criterios de aceptación (TDD)
- Dado un mob con textura persistida, cuando se abre la pestaña Textura, entonces el preview 3D muestra esa textura y no un modelo negro.
- Dado ese mismo mob, cuando se pinta un trazo en el canvas 2D, entonces el preview 3D lo refleja en vivo (HU-26 no es solo la carga inicial).
- La decisión sobre el viewport de la pestaña Modelo queda documentada, se arregle o se declare intencional — no se deja ambigua.
- Verificación en vivo contra `studio-dev` sobre `Carcomido v3`, que es donde está reproducido.

## Hecho

### La causa
Una línea. `syncDataTexture` **no marcaba `needsUpdate` en la textura recién creada**, así que three.js nunca subía los píxeles a la GPU: el material muestreaba una textura vacía, que se ve **negra**.

El propio código tenía el A/B adentro, y es lo que lo hizo evidente una vez visto:

```ts
if (isNewBuffer) {
  const nextTexture = new DataTexture(atlas.pixels, ...)   // ← nunca marcaba needsUpdate
  ...
} else if (dataTexture) {
  dataTexture.needsUpdate = true                            // ← sí marcaba
}
```

Eso explica el síntoma completo, incluida la parte que parecía contradictoria: el atlas estaba bien, el canvas 2D lo pintaba bien, el material tenía el `map` con los píxeles correctos — y aun así el modelo se veía negro.

### Cómo se encontró: pintando
El diagnóstico estático se agotó. Siete causas descartadas con evidencia medida, no por lectura:

1. Los rects UV de `cuboid.faces` están sincronizados con `uv.regions` (234/234 en el v3, 270/270 en el v4).
2. El atlas del store tiene contenido real (47.968 píxeles opacos) y el canvas 2D lo pinta.
3. Un `Uint8ClampedArray` sube a WebGL sin error (probado contra un contexto `webgl2` real en la página).
4. Hay luces y `MeshStandardMaterial` renderiza visible (el marcador del pivote se ve).
5. El tinte del material es blanco, no ennegrece el `map`.
6. Las dimensiones del atlas y del UV coinciden.
7. El wiring: el preview **sí** recibe el atlas como `map` con los píxeles correctos, incluso por el camino asíncrono (test nuevo, que era el único hueco de cobertura real).

Con todo eso descartado, el experimento que lo resolvió fue **pintar un trazo en vivo**: el modelo apareció **entero y texturizado** con la primera pincelada. Ese trazo cae en la rama `else` — la que sí marcaba `needsUpdate` — y ahí quedó señalada la rama que no lo hacía.

### Evidencia cuantitativa de que era un bug real
Con una textura de luminancia ~60/255 y luces `ambient 0.6` + `directional 0.8`, una cara debería renderizar alrededor de **74/255**. En pantalla medía **15-25**, unas tres veces más oscuro. No era "la textura es oscura y se ve oscura".

### Sobre el viewport de la pestaña Modelo
Se ve **gris plano** porque `buildMobGroup` recibe `atlasTexture = null` desde `ThreeViewport` (la pestaña Modelo nunca le pasa el atlas). Eso es **intencional**: es el editor de geometría, donde el color plano deja ver la forma. Queda documentado acá, como pedía el ticket, y no se cambió.

### Defecto aparte, no arreglado
`frontend/src/viewport` **no setea `colorSpace` en ninguna textura** con three 0.186. Una textura de color sin `SRGBColorSpace` se interpreta como lineal y se renderiza **más clara** de lo que corresponde. Es incorrecto, pero **no era la causa del negro** (la falta de conversión aclara, no oscurece). **Queda a decisión del PO** si se abre ticket.

### Tests
2 tests nuevos en `TextureCanvas.spec.ts`, el de la causa **verificado primero en rojo** (`version === 0`, es decir nunca marcada). El otro cubre el hueco encontrado en el camino: el test de HU-26 que existía montaba **sin** textura persistida, o sea el camino síncrono; el real espera la descarga. Frontend completo: **928/928 en verde**, tsc y lint limpios.
