/**
 * Ticket 047 -- paleta fija de 8 colores del editor de textura, extraída
 * a su propio módulo en el ticket 058 para que `TextureColorPicker.vue`
 * (el nuevo trigger/panel de color de la toolbar rediseñada) y
 * `TextureCanvas.vue` (que sigue siendo dueño de `activeColorHex`)
 * compartan EXACTAMENTE la misma paleta sin duplicarla -- antes vivía
 * como una constante inline dentro de `TextureCanvas.vue`.
 */
export const PALETTE: string[] = ['#f3f6f8', '#0b0f14', '#e0574c', '#f2c66d', '#48e5a0', '#4d8bf0', '#a35bd6', '#8a5a3b']

export const DEFAULT_COLOR: string = PALETTE[2]!
