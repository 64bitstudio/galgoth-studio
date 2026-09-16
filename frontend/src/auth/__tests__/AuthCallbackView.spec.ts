import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import * as authApi from '../authApi'
import AuthCallbackView from '../AuthCallbackView.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, exchangeSocialCode: vi.fn() }
})

const user: authApi.RegisteredUser = {
  id: 'u1',
  email: 'ada@example.com',
  phone: null,
  nombre: 'Ada',
  apellidos: 'Lovelace',
  emailVerified: true,
  phoneVerified: false,
  hasPassword: false,
  country: null,
  username: null,
  createdAt: '2026-01-01T00:00:00Z',
}

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/auth/callback', component: AuthCallbackView },
      { path: '/', component: { template: '<div />' } },
      { path: '/login', component: { template: '<div />' } },
      { path: '/projects', component: { template: '<div />' } },
    ],
  })
}

async function mountAtCallback(query: Record<string, string>): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = testRouter()
  await router.push({ path: '/auth/callback', query })
  const wrapper = mount(AuthCallbackView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('AuthCallbackView.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it('con ?code= real, canjea el código, guarda la sesión y navega a "/" por defecto', async () => {
    vi.mocked(authApi.exchangeSocialCode).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })

    const { router } = await mountAtCallback({ code: 'code-abc' })

    expect(authApi.exchangeSocialCode).toHaveBeenCalledWith('code-abc')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('con un redirect pendiente guardado por LoginView, navega ahí en vez de a "/"', async () => {
    sessionStorage.setItem('galgoth-studio.postLoginRedirect', '/projects')
    vi.mocked(authApi.exchangeSocialCode).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })

    const { router } = await mountAtCallback({ code: 'code-abc' })

    expect(router.currentRoute.value.path).toBe('/projects')
    // El redirect guardado se consume una sola vez.
    expect(sessionStorage.getItem('galgoth-studio.postLoginRedirect')).toBeNull()
  })

  it('con ?error=social_login_cancelled muestra el mensaje explícito sin llamar al backend', async () => {
    const { wrapper, router } = await mountAtCallback({ error: 'social_login_cancelled' })

    expect(authApi.exchangeSocialCode).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Cancelaste el inicio de sesión')
    expect(router.currentRoute.value.path).toBe('/auth/callback')
  })

  it('un código inválido/expirado muestra el error real del backend', async () => {
    vi.mocked(authApi.exchangeSocialCode).mockRejectedValue(
      new ApiError('The exchange code is invalid, expired, or already used', 400, 'invalid_token'),
    )

    const { wrapper } = await mountAtCallback({ code: 'bad-code' })

    expect(wrapper.text()).toContain('The exchange code is invalid, expired, or already used')
  })

  it('2FA activo muestra el mensaje explícito sin navegar (galgoth-studio no lo soporta todavía)', async () => {
    vi.mocked(authApi.exchangeSocialCode).mockResolvedValue({ twoFactorRequired: true, pendingToken: 'p1', method: 'TOTP' })

    const { wrapper, router } = await mountAtCallback({ code: 'code-abc' })

    expect(wrapper.text()).toContain('verificación en dos pasos')
    expect(router.currentRoute.value.path).toBe('/auth/callback')
  })

  it('sin ?code= ni ?error= muestra el error genérico', async () => {
    const { wrapper } = await mountAtCallback({})

    expect(authApi.exchangeSocialCode).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('No se pudo completar el inicio de sesión')
  })
})
