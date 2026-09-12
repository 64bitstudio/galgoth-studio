import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../authApi'
import LoginView from '../LoginView.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, login: vi.fn() }
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

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/login', component: LoginView },
      { path: '/register', component: { template: '<div />' } },
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
})
