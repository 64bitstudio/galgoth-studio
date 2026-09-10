/**
 * Ticket 047 -- etiquetado de regiones UV para el selector de región
 * (dropdown, HU-24 AC #3) y el overlay de guía del canvas.
 *
 * Riesgo #1 documentado en `docs/definiciones/galgoth-studio-fase3-textura.md`
 * ("Riesgos y preguntas abiertas"): NO existe todavía una convención fija
 * de nombres para bones/cuboids (la IA o el usuario los nombran libremente,
 * no hay un esqueleto vanilla Minecraft fijo) -- se acepta ese riesgo tal
 * cual y se etiqueta cada región como `${cuboid.name} (${face})`, EXACTO
 * el mismo formato que ya usa `MobEditor.vue` (ticket 043) para el modal
 * de confirmación de pérdida de pintura ("Cabeza (north)") -- reutiliza
 * una convención ya vigente en vez de inventar una nueva (regla del
 * equipo de consistencia de UI).
 *
 * Regiones `ORPHAN` (cuboid ya eliminado, ver `UvRegionStatus`) se
 * excluyen del selector: su `cuboidId` ya no resuelve a ningún cuboid
 * real, así que no hay nombre que mostrar ni nada que enfocar en el
 * viewport 3D -- siguen ocupando espacio en el atlas (bookkeeping), pero
 * no son una opción navegable de este selector.
 */
import type { Cuboid, UvRegion } from '../../domain/MobProjectModel'

export interface SelectableRegion {
  cuboidId: string
  face: UvRegion['face']
  label: string
  rect: readonly [number, number, number, number]
}

/** Valor especial del `<select>` -- "ninguna región enfocada", todas se muestran igual. */
export const ALL_REGIONS_VALUE = '__all__'

export function regionKey(region: Pick<SelectableRegion, 'cuboidId' | 'face'>): string {
  return `${region.cuboidId}:${region.face}`
}

export function buildSelectableRegions(regions: UvRegion[], cuboids: Cuboid[]): SelectableRegion[] {
  const cuboidsById = new Map(cuboids.map((cuboid) => [cuboid.id, cuboid]))
  const selectable: SelectableRegion[] = []
  for (const region of regions) {
    if (region.status === 'orphan') {
      continue
    }
    const cuboid = cuboidsById.get(region.cuboidId)
    if (!cuboid) {
      continue
    }
    selectable.push({ cuboidId: region.cuboidId, face: region.face, label: `${cuboid.name} (${region.face})`, rect: region.rect })
  }
  return selectable
}
