# 128 — Gestión explícita de espacio de color, o declarar que asumimos sRGB

## Objetivo
Referencia: `docs/definiciones/fidelidad-de-color-en-la-generacion-de-texturas.md` (Decisión 5), aprobado con VoBo del PO.

Hoy el espacio de color del pipeline es un **efecto colateral**, no una decisión. Hay cuatro `TYPE_INT_ARGB` forzados con un `drawImage` de por medio —`TextureSheetSlicer.java:120`, `TextureCompositorService.java:83`, `TextureGenerationService.java:533` y `:630`— y un re-encode final (`project/texture/TextureService.java:149-168`) cuyo writer PNG **descarta perfiles ICC y chunks `gAMA`/`cHRM`**.

Si el PNG del proveedor viniera con un perfil embebido (por ejemplo Display P3), pasa una de dos: Java2D lo convierte a sRGB implícitamente en el blit, o los valores pasan crudos y quedan **mal interpretados** como sRGB. En los dos casos, los números que medimos después no son los del archivo original.

Lo que no puede seguir es que nadie lo haya decidido.

**Se puede hacer en paralelo**, pero su diseño se informa del ticket 124, que mide de paso si el proveedor manda perfil ICC.

## Alcance
**Incluye:**
- Determinar si los PNG del proveedor traen perfil ICC o chunks de gamma/cromaticidad (dato que produce el 124).
- **Tomar la decisión y escribirla**: o se asume sRGB de punta a punta, convirtiendo explícitamente lo que llegue con otro perfil, o se preserva el perfil a lo largo del pipeline.
- Que los cuatro puntos de normalización dejen de ser implícitos: o se documentan como conversión deliberada, o se corrigen.
- Documentar la decisión en `/docs`, donde se pueda encontrar sin leer el código.

**No incluye:**
- La corrección de color por estilo (ticket 127).
- Cambiar el formato de persistencia del atlas.

## Criterios de aceptación (TDD)
- Dado un PNG de entrada **con** perfil ICC no-sRGB, cuando pasa por el pipeline, entonces el comportamiento es el decidido y está cubierto por un test — hoy no hay ninguno.
- Dado un PNG sin perfil, entonces el comportamiento no cambia respecto de hoy.
- Dado el atlas persistido, entonces queda documentado en qué espacio de color está, sin ambigüedad.
- La decisión (asumir sRGB vs preservar perfil) queda escrita en `/docs` con su tradeoff, no solo en un Javadoc.

## Hecho
