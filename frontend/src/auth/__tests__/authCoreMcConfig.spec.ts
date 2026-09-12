import { describe, expect, it } from 'vitest'
import { AUTH_CLIENT_ID, authCoreMcUrl } from '../authCoreMcConfig'

describe('authCoreMcConfig', () => {
  it('resuelve el emisor real de PROD a partir del hostname de galgoth-studio', () => {
    expect(authCoreMcUrl('studio.galgoth.64bitstudio.com')).toBe('https://auth.64bitstudio.com')
  })

  it('resuelve el emisor real de QA', () => {
    expect(authCoreMcUrl('studio-qa.galgoth.64bitstudio.com')).toBe('https://auth-qa.64bitstudio.com')
  })

  it('resuelve el emisor real de DEV', () => {
    expect(authCoreMcUrl('studio-dev.galgoth.64bitstudio.com')).toBe('https://auth-dev.64bitstudio.com')
  })

  it('un hostname desconocido (dev local) cae al default de localhost:8080', () => {
    expect(authCoreMcUrl('localhost')).toBe('http://localhost:8080')
  })

  it('AUTH_CLIENT_ID es el mismo en los 3 ambientes (auth-core-mc#052)', () => {
    expect(AUTH_CLIENT_ID).toBe('galgoth-studio')
  })
})
