import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../authApi'
import { useSessionStore } from '../sessionStore'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, register: vi.fn(), login: vi.fn(), refreshAccessToken: vi.fn() }
})

const user: authApi.RegisteredUser = {
  id: 'u1',
  email: 'ada@example.com',
  phone: null,
  nombre: 'Ada',
  apellidos: 'Lovelace',
  emailVerified: false,
  phoneVerified: false,
  hasPassword: true,
}

describe('sessionStore', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('arranca sin sesión cuando sessionStorage está vacío', () => {
    const store = useSessionStore()

    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBeNull()
  })

  it('login exitoso guarda tokens+user y queda autenticado', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const store = useSessionStore()

    const result = await store.login('ada@example.com', 'abcd1234')

    expect(result).toBe('ok')
    expect(store.isAuthenticated).toBe(true)
    expect(store.accessToken).toBe('a1')
    expect(store.user).toEqual(user)
  })

  it('login con 2FA activo devuelve two-factor-required sin guardar sesión', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ twoFactorRequired: true, pendingToken: 'p1', method: 'OTP_EMAIL' })
    const store = useSessionStore()

    const result = await store.login('ada@example.com', 'abcd1234')

    expect(result).toBe('two-factor-required')
    expect(store.isAuthenticated).toBe(false)
  })

  it('la sesión persiste en sessionStorage y un store nuevo la recupera', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const firstStore = useSessionStore()
    await firstStore.login('ada@example.com', 'abcd1234')

    setActivePinia(createPinia())
    const secondStore = useSessionStore()

    expect(secondStore.isAuthenticated).toBe(true)
    expect(secondStore.accessToken).toBe('a1')
  })

  it('register no crea sesión (solo crea la cuenta)', async () => {
    vi.mocked(authApi.register).mockResolvedValue(user)
    const store = useSessionStore()

    const result = await store.register({ email: 'ada@example.com', nombre: 'Ada', apellidos: 'Lovelace', password: 'abcd1234' })

    expect(result).toEqual(user)
    expect(store.isAuthenticated).toBe(false)
  })

  it('refresh exitoso reemplaza accessToken y sigue autenticado', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const store = useSessionStore()
    await store.login('ada@example.com', 'abcd1234')
    vi.mocked(authApi.refreshAccessToken).mockResolvedValue({ accessToken: 'a2', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 })

    const refreshed = await store.refresh()

    expect(refreshed).toBe(true)
    expect(store.accessToken).toBe('a2')
    expect(store.isAuthenticated).toBe(true)
  })

  it('refresh rechazado cierra la sesión (auth-core-mc reiniciado, refresh token también inválido)', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const store = useSessionStore()
    await store.login('ada@example.com', 'abcd1234')
    vi.mocked(authApi.refreshAccessToken).mockRejectedValue(new Error('invalid_token'))

    const refreshed = await store.refresh()

    expect(refreshed).toBe(false)
    expect(store.isAuthenticated).toBe(false)
  })

  it('refresh sin refreshToken (nunca hubo sesión) no llama a auth-core-mc', async () => {
    const store = useSessionStore()

    const refreshed = await store.refresh()

    expect(refreshed).toBe(false)
    expect(authApi.refreshAccessToken).not.toHaveBeenCalled()
  })

  it('logout limpia el estado y sessionStorage', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const store = useSessionStore()
    await store.login('ada@example.com', 'abcd1234')

    store.logout()

    expect(store.isAuthenticated).toBe(false)
    expect(sessionStorage.getItem('galgoth-studio.session')).toBeNull()
  })
})
