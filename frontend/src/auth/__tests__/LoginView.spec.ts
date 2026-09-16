import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../authApi'
import LoginView from '../LoginView.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, login: vi.fn(), socialLoginUrl: vi.fn() }
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
  country: null,
  username: null,
  createdAt: '2026-01-01T00:00:00Z',
}

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/login', component: LoginView },
      { path: '/register', component: { template: '<div />' } },
      { path: '/forgot-password', component: { template: '<div />' } },
      { path: '/projects', component: { template: '<div />' } }, // ticket 087 -- destino de `?redirect=` en los tests de abajo.
    ],
  })
}

describe('LoginView.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('envía identifier+password y navega a "/" en éxito', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const router = testRouter()
    await router.push('/login')
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    await wrapper.find('input[type="text"]').setValue('ada@example.com')
    await wrapper.find('input[type="password"]').setValue('abcd1234')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(authApi.login).toHaveBeenCalledWith('ada@example.com', 'abcd1234')
    expect(router.currentRoute.value.path).toBe('/')
  })

  // Ticket 087 -- vuelve a `?redirect=` (lo agrega el guard de rutas de `router.ts` al mandar para acá a un visitante sin sesión).
  it('con ?redirect= presente, navega ahí en vez de a "/" tras loguearse', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const router = testRouter()
    await router.push('/login?redirect=/projects')
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    await wrapper.find('input[type="text"]').setValue('ada@example.com')
    await wrapper.find('input[type="password"]').setValue('abcd1234')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/projects')
  })

  // Ticket 087 -- un `?redirect=` que no sea una ruta interna (URL absoluta o `//host` protocol-relative) se ignora -- nunca abre una redirección abierta.
  it('con ?redirect= a un origen externo, lo ignora y navega a "/" (nunca una redirección abierta)', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const router = testRouter()
    await router.push('/login?redirect=' + encodeURIComponent('//evil.example.com'))
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    await wrapper.find('input[type="text"]').setValue('ada@example.com')
    await wrapper.find('input[type="password"]').setValue('abcd1234')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('credenciales inválidas muestran un error y no navegan', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new (await import('../../api/ApiError')).ApiError('Credenciales inválidas', 401, 'invalid_credentials'))
    const router = testRouter()
    await router.push('/login')
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    await wrapper.find('input[type="text"]').setValue('ada@example.com')
    await wrapper.find('input[type="password"]').setValue('wrong')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Credenciales inválidas')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('2FA activo muestra el mensaje explícito en vez de navegar', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ twoFactorRequired: true, pendingToken: 'p1', method: 'OTP_EMAIL' })
    const router = testRouter()
    await router.push('/login')
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    await wrapper.find('input[type="text"]').setValue('ada@example.com')
    await wrapper.find('input[type="password"]').setValue('abcd1234')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('verificación en dos pasos')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  // Ticket 072 de auth-core-mc -- login social real.
  it('"Google" navega el navegador completo a la URL real devuelta por el backend', async () => {
    const realLocation = window.location
    Object.defineProperty(window, 'location', { value: { ...realLocation, href: '' }, writable: true, configurable: true })
    vi.mocked(authApi.socialLoginUrl).mockResolvedValue('https://auth-dev.example.com/oauth2/authorization/x::google')
    const router = testRouter()
    await router.push('/login')
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    const googleButton = wrapper.findAll('button').find((b) => b.text().includes('Google'))!
    await googleButton.trigger('click')
    await flushPromises()

    expect(authApi.socialLoginUrl).toHaveBeenCalledWith('google')
    expect(window.location.href).toBe('https://auth-dev.example.com/oauth2/authorization/x::google')

    Object.defineProperty(window, 'location', { value: realLocation, writable: true, configurable: true })
  })

  it('con ?redirect= presente, lo guarda en sessionStorage antes de navegar al login social', async () => {
    const realLocation = window.location
    Object.defineProperty(window, 'location', { value: { ...realLocation, href: '' }, writable: true, configurable: true })
    vi.mocked(authApi.socialLoginUrl).mockResolvedValue('https://auth-dev.example.com/oauth2/authorization/x::facebook')
    sessionStorage.removeItem('galgoth-studio.postLoginRedirect')
    const router = testRouter()
    await router.push('/login?redirect=/projects')
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    const facebookButton = wrapper.findAll('button').find((b) => b.text().includes('Facebook'))!
    await facebookButton.trigger('click')
    await flushPromises()

    expect(sessionStorage.getItem('galgoth-studio.postLoginRedirect')).toBe('/projects')

    Object.defineProperty(window, 'location', { value: realLocation, writable: true, configurable: true })
    sessionStorage.removeItem('galgoth-studio.postLoginRedirect')
  })

  it('un error al pedir la URL de login social se muestra sin navegar', async () => {
    vi.mocked(authApi.socialLoginUrl).mockRejectedValue(
      new (await import('../../api/ApiError')).ApiError('No se pudo iniciar sesión con ese proveedor. Intenta de nuevo.', 500),
    )
    const router = testRouter()
    await router.push('/login')
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    const googleButton = wrapper.findAll('button').find((b) => b.text().includes('Google'))!
    await googleButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('No se pudo iniciar sesión con ese proveedor')
  })
})
