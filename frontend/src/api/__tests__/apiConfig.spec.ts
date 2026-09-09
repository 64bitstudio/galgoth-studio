import { describe, expect, it } from 'vitest'
import { API_BASE_URL, thumbnailUrl } from '../apiConfig'

describe('thumbnailUrl', () => {
  it('antepone API_BASE_URL a una ruta relativa servible del backend', () => {
    expect(thumbnailUrl('/api/mobs/m1/thumbnail')).toBe(`${API_BASE_URL}/api/mobs/m1/thumbnail`)
  })

  it('devuelve null si no hay thumbnail (evita construir una URL rota)', () => {
    expect(thumbnailUrl(null)).toBeNull()
  })
})
