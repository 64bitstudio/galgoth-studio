import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { formatRelativeDate } from '../relativeDate'

const NOW = new Date('2026-09-11T12:00:00.000Z')

function daysAgo(days: number): string {
  return new Date(NOW.getTime() - days * 24 * 60 * 60 * 1000).toISOString()
}

describe('formatRelativeDate', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('hoy (0 días) y una fecha futura por reloj desincronizado', () => {
    expect(formatRelativeDate(daysAgo(0))).toBe('Editado hoy')
    expect(formatRelativeDate(daysAgo(-1))).toBe('Editado hoy')
  })

  it('ayer', () => {
    expect(formatRelativeDate(daysAgo(1))).toBe('Editado ayer')
  })

  it('días (2-6)', () => {
    expect(formatRelativeDate(daysAgo(2))).toBe('Editado hace 2 días')
    expect(formatRelativeDate(daysAgo(6))).toBe('Editado hace 6 días')
  })

  // Ticket 072 -- antes se quedaba en "días" sin límite (ej. "hace 87 días"); la referencia pide semanas/meses/años.
  it('semanas (7-29 días)', () => {
    expect(formatRelativeDate(daysAgo(7))).toBe('Editado hace 1 semana')
    expect(formatRelativeDate(daysAgo(13))).toBe('Editado hace 1 semana')
    expect(formatRelativeDate(daysAgo(14))).toBe('Editado hace 2 semanas')
    expect(formatRelativeDate(daysAgo(29))).toBe('Editado hace 4 semanas')
  })

  it('meses (30-364 días)', () => {
    expect(formatRelativeDate(daysAgo(30))).toBe('Editado hace 1 mes')
    expect(formatRelativeDate(daysAgo(60))).toBe('Editado hace 2 meses')
    expect(formatRelativeDate(daysAgo(364))).toBe('Editado hace 12 meses')
  })

  it('años (365+ días)', () => {
    expect(formatRelativeDate(daysAgo(365))).toBe('Editado hace 1 año')
    expect(formatRelativeDate(daysAgo(800))).toBe('Editado hace 2 años')
  })

  it('una fecha inválida no rompe -- devuelve string vacío', () => {
    expect(formatRelativeDate('no-es-una-fecha')).toBe('')
  })
})
