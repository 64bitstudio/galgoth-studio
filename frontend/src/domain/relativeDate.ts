/**
 * Formato relativo simple para metadata de cards (ticket 036, pasada de
 * fidelidad visual -- mockup 01/12: "Editado hoy"/"Editado ayer"/"Hace 3
 * días"). Puramente de presentación sobre `updatedAt`, un campo que YA
 * existe en `ProjectSummary`/`MobSummary` -- no agrega ningún dato nuevo,
 * solo lo muestra.
 */
export function formatRelativeDate(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) {
    return ''
  }
  const diffMs = Date.now() - then
  const diffDays = Math.floor(diffMs / (24 * 60 * 60 * 1000))

  if (diffDays <= 0) {
    return 'Editado hoy'
  }
  if (diffDays === 1) {
    return 'Editado ayer'
  }
  return `Editado hace ${diffDays} días`
}
