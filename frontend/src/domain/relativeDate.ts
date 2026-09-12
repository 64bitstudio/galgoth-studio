/**
 * Formato relativo simple para metadata de cards (ticket 036, pasada de
 * fidelidad visual -- mockup 01/12: "Editado hoy"/"Editado ayer"/"Hace 3
 * días"). Puramente de presentación sobre `updatedAt`, un campo que YA
 * existe en `ProjectSummary`/`MobSummary` -- no agrega ningún dato nuevo,
 * solo lo muestra.
 *
 * Ticket 072 (rediseño de "Mis proyectos", mockup con fidelidad visual
 * estricta) -- agrega semanas/meses/años (antes se quedaba en "días" sin
 * límite, ej. "hace 87 días"; la referencia pide "hace 1 semana"). Meses
 * usa 30 días y años 365 -- aproximación deliberada (mismo criterio de
 * simplicidad que el resto de esta utilidad, sin calendario real de por
 * medio), suficiente para metadata secundaria de una card, no para nada
 * que dependa de precisión de fecha real.
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
  if (diffDays < 7) {
    return `Editado hace ${diffDays} días`
  }
  if (diffDays < 30) {
    const weeks = Math.floor(diffDays / 7)
    return weeks === 1 ? 'Editado hace 1 semana' : `Editado hace ${weeks} semanas`
  }
  if (diffDays < 365) {
    const months = Math.floor(diffDays / 30)
    return months === 1 ? 'Editado hace 1 mes' : `Editado hace ${months} meses`
  }
  const years = Math.floor(diffDays / 365)
  return years === 1 ? 'Editado hace 1 año' : `Editado hace ${years} años`
}
