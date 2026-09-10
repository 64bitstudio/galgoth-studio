/**
 * Ticket 046 -- tipo del Command de Undo/Redo del editor de textura
 * (Diseño técnico §9 de `docs/definiciones/galgoth-studio-fase3-textura.md`).
 *
 * Deliberadamente NO es un snapshot del atlas completo: `beforePixels`/
 * `afterPixels` cubren EXCLUSIVAMENTE el área de `rect` (bounding box
 * mínimo tocado por la operación), nunca el atlas entero -- snapshotear
 * el bitmap completo por cada trazo es inviable en memoria (un atlas de
 * 256x256 son 256KB+ sin comprimir, por Command, por trazo).
 *
 * Ejemplos de qué produce cada herramienta (§9, sin implementarlas
 * todavía -- eso es el ticket 047):
 * - Brush/erase: `rect` = bounding box acumulado del trazo completo entre
 *   `pointerdown` y `pointerup` (un trazo completo = un Command).
 * - Fill (cubeta): `rect` = bounding box de la región contigua rellenada.
 * - Paste/import de región UV: `rect` = la región seleccionada.
 * - Import de atlas completo: `rect` = atlas completo.
 * - Apply de generación IA: `rect` = unión de los `atlasUvRect` de todas
 *   las caras tocadas, sin importar cuántas llamadas de sheet involucró.
 */
export interface TextureRect {
  x: number
  y: number
  width: number
  height: number
}

export interface TexturePatchCommand {
  /** Bounding box mínimo tocado, en coordenadas de píxel del atlas. */
  rect: TextureRect
  /** Píxeles RGBA (4 bytes/píxel) de `rect`, ANTES de la operación -- longitud `rect.width * rect.height * 4`. */
  beforePixels: Uint8ClampedArray
  /** Píxeles RGBA (4 bytes/píxel) de `rect`, DESPUÉS de la operación -- misma longitud que `beforePixels`. */
  afterPixels: Uint8ClampedArray
}
