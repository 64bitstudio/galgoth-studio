import { BoxGeometry, type BufferAttribute } from 'three'
import { describe, expect, it } from 'vitest'
import type { CuboidFaces } from '../../domain/MobProjectModel'
import { applyCuboidFaceUvs, BOX_GEOMETRY_FACE_ORDER } from '../textureUvMapping'

const ZERO_FACE = { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null }

function facesWith(overrides: Partial<CuboidFaces>): CuboidFaces {
  return { north: ZERO_FACE, south: ZERO_FACE, east: ZERO_FACE, west: ZERO_FACE, up: ZERO_FACE, down: ZERO_FACE, ...overrides }
}

/** `BoxGeometry` recién creada -- siempre un `BufferAttribute` plano, nunca `InterleavedBufferAttribute` (eso solo aplica a geometrías que comparten un buffer intercalado). */
function uvAttributeOf(geometry: BoxGeometry): BufferAttribute {
  return geometry.getAttribute('uv') as BufferAttribute
}

describe('applyCuboidFaceUvs', () => {
  it('mapea el rect de una cara (píxeles del atlas) a UV 0..1, sin invertir eje U', () => {
    const geometry = new BoxGeometry(1, 1, 1)
    const faces = facesWith({ up: { uv: [0, 0, 16, 16], texture: 0 } })

    applyCuboidFaceUvs(uvAttributeOf(geometry), faces, 64, 64)

    const upFaceIndex = BOX_GEOMETRY_FACE_ORDER.indexOf('up')
    const uvAttribute = uvAttributeOf(geometry)
    // Las 4 esquinas de la cara 'up' deben caer en el cuadrante [0, 0.25]x[0, 0.25].
    for (let corner = 0; corner < 4; corner += 1) {
      const vertexIndex = upFaceIndex * 4 + corner
      expect(uvAttribute.getX(vertexIndex)).toBeGreaterThanOrEqual(0)
      expect(uvAttribute.getX(vertexIndex)).toBeLessThanOrEqual(0.25)
      expect(uvAttribute.getY(vertexIndex)).toBeGreaterThanOrEqual(0)
      expect(uvAttribute.getY(vertexIndex)).toBeLessThanOrEqual(0.25)
    }
  })

  it('cada cara usa su propio rect -- no se mezclan entre sí', () => {
    const geometry = new BoxGeometry(1, 1, 1)
    const faces = facesWith({
      up: { uv: [0, 0, 8, 8], texture: 0 },
      down: { uv: [32, 32, 40, 40], texture: 0 },
    })

    applyCuboidFaceUvs(uvAttributeOf(geometry), faces, 64, 64)
    const uvAttribute = uvAttributeOf(geometry)

    const upIndex = BOX_GEOMETRY_FACE_ORDER.indexOf('up') * 4
    const downIndex = BOX_GEOMETRY_FACE_ORDER.indexOf('down') * 4

    expect(uvAttribute.getX(upIndex)).not.toBeCloseTo(uvAttribute.getX(downIndex))
  })

  it('marca needsUpdate en el atributo tras reescribirlo -- Three.js expone esto como version++ (BufferAttribute.needsUpdate es setter-only, sin getter)', () => {
    const geometry = new BoxGeometry(1, 1, 1)
    const uvAttribute = uvAttributeOf(geometry)
    const versionBefore = uvAttribute.version

    applyCuboidFaceUvs(uvAttribute, facesWith({}), 64, 64)

    expect(uvAttribute.version).toBeGreaterThan(versionBefore)
  })

  it('el orden de caras de BoxGeometry es +x,-x,+y,-y,+z,-z -- east/west/up/down/south/north (convención Minecraft, ADR 0001)', () => {
    expect(BOX_GEOMETRY_FACE_ORDER).toEqual(['east', 'west', 'up', 'down', 'south', 'north'])
  })

  /**
   * Ticket 112 -- orientación vertical, que hasta ahora NINGÚN test fijaba.
   *
   * La textura se sube como `DataTexture` con `flipY = false` (`TextureCanvas.vue`)
   * y su buffer viene de `drawImage` + `getImageData`, o sea fila 0 = ARRIBA de
   * la imagen. Con `flipY=false`, v=0 muestrea esa fila 0. Los vértices SUPERIORES
   * de cada cara de una `BoxGeometry` traen v=1 por defecto, así que para que la
   * cara se vea derecha hay que asignarles el borde SUPERIOR del rect (y0), no el
   * inferior (y1).
   */
  it('los vértices superiores de la cara muestran el borde SUPERIOR del rect (no invierte verticalmente)', () => {
    const geometry = new BoxGeometry(1, 1, 1)
    // Rect alto y separado del origen: y0=10 (arriba), y1=50 (abajo).
    const faces = facesWith({ north: { uv: [0, 10, 20, 50], texture: 0 } })

    applyCuboidFaceUvs(uvAttributeOf(geometry), faces, 100, 100)

    const uvAttribute = uvAttributeOf(geometry)
    const base = BOX_GEOMETRY_FACE_ORDER.indexOf('north') * 4
    // Vértices de BoxGeometry: 0=arriba-izq, 1=arriba-der, 2=abajo-izq, 3=abajo-der.
    expect(uvAttribute.getY(base)).toBeCloseTo(10 / 100)
    expect(uvAttribute.getY(base + 2)).toBeCloseTo(50 / 100)
  })

})
