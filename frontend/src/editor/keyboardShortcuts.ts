/**
 * Helpers de atajos de teclado compartidos entre `EditorToolbar.vue`
 * (Modelo) y `TextureCanvas.vue` (Textura, ticket 068/hallazgo real
 * corregido en el mismo ticket que agrega este archivo -- el botón de
 * Deshacer/Rehacer de Textura nunca se había cableado a pesar de que el
 * mecanismo del store ya existía desde el 046). Antes vivían duplicados
 * verbatim en `EditorToolbar.vue`; se extraen acá para no repetirlos una
 * tercera vez.
 */

/** `metaKey` (Cmd) en Mac, `ctrlKey` (Ctrl) en el resto -- mismo criterio ya usado por el tooltip real de Undo/Redo. */
export const MODIFIER_KEY = /mac/i.test(navigator.platform || navigator.userAgent) ? 'Cmd' : 'Ctrl'

/** Un atajo de teclado (Ctrl/Cmd+Z, +/-, Espacio, etc.) nunca debe dispararse mientras el foco está en un campo de texto real. */
export function isEditableTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')
}
