/**
 * Ticket 047 -- conversión entre el hex que usan los controles de UI
 * (`<input type="color">`, nativo, siempre `#rrggbb` sin alfa) y el
 * `RgbaColor` que consumen las herramientas de píxeles (`pixelTools.ts`).
 * El alfa de UI es siempre 255 (opaco) -- ningún control de opacidad
 * parcial está en el alcance de este ticket (ver `## Hecho`); el
 * Borrador es el único camino a alfa 0, vía `TRANSPARENT` directo, sin
 * pasar por hex.
 */
import type { RgbaColor } from './pixelTools'

export function hexToRgba(hex: string): RgbaColor {
  const clean = hex.replace('#', '')
  const r = Number.parseInt(clean.slice(0, 2), 16)
  const g = Number.parseInt(clean.slice(2, 4), 16)
  const b = Number.parseInt(clean.slice(4, 6), 16)
  return [r, g, b, 255]
}

export function rgbaToHex([r, g, b]: RgbaColor): string {
  const toHex = (n: number): string => n.toString(16).padStart(2, '0')
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`
}
