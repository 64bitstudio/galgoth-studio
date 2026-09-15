import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '../../auth/sessionStore'
import * as productProfileApi from '../productProfileApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('productProfileApi', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useSessionStore().accessToken = 'token'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getProductProfile hace GET /api/account/profile (backend propio, con Bearer)', async () => {
    const profile = { avatarUrl: null, notifyEmail: true, notifyProductNews: true, notifySaveReminders: true }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(profile))
    vi.stubGlobal('fetch', fetchMock)

    const result = await productProfileApi.getProductProfile()

    expect(result).toEqual(profile)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/account/profile')
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer token')
  })

  it('updatePreferences manda las 3 preferencias siempre explícitas', async () => {
    const profile = { avatarUrl: null, notifyEmail: false, notifyProductNews: true, notifySaveReminders: true }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(profile))
    vi.stubGlobal('fetch', fetchMock)

    await productProfileApi.updatePreferences(false, true, true)

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/account/preferences')
    expect(init?.method).toBe('PATCH')
    expect(JSON.parse(init?.body as string)).toEqual({ notifyEmail: false, notifyProductNews: true, notifySaveReminders: true })
  })

  it('uploadAvatar manda el archivo crudo con su Content-Type real', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ avatarUrl: '/api/account/avatar/u1' }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File([new Uint8Array([1, 2, 3])], 'avatar.png', { type: 'image/png' })

    const result = await productProfileApi.uploadAvatar(file)

    expect(result).toBe('/api/account/avatar/u1')
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/account/avatar')
    expect(init?.method).toBe('POST')
    expect((init?.headers as Record<string, string>)['Content-Type']).toBe('image/png')
    expect(init?.body).toBe(file)
  })

  it('un error real propaga un ApiError con el código del backend', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'INVALID_AVATAR', message: 'Formato no soportado' }, 400)))
    const file = new File([new Uint8Array([1])], 'a.gif', { type: 'image/gif' })

    await expect(productProfileApi.uploadAvatar(file)).rejects.toMatchObject({ status: 400, code: 'INVALID_AVATAR' })
  })
})
