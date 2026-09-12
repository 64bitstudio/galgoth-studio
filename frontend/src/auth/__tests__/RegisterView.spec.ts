import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import * as authApi from '../authApi'
import RegisterView from '../RegisterView.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, register: vi.fn() }
})

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/register', component: RegisterView },
      { path: '/login', component: { template: '<div />' } },
    ],
  })
}

async function mountAtRegister(): Promise<ReturnType<typeof mount>> {
  const router = testRouter()
  await router.push('/register')
  return mount(RegisterView, { global: { plugins: [router] } })
}

/** Rellena todos los campos con datos válidos, contraseñas coincidentes por defecto (ticket 082). */
async function fillForm(
  wrapper: ReturnType<typeof mount>,
  { password = 'abcd1234', confirmPassword = password }: { password?: string; confirmPassword?: string } = {},
): Promise<void> {
  await wrapper.find('input[type="text"]').setValue('Ada')
  await wrapper.findAll('input[type="text"]')[1]!.setValue('Lovelace')
  await wrapper.find('input[type="email"]').setValue('ada@example.com')
  await wrapper.find('input[type="password"]').setValue(password)
  await wrapper.findAll('input[type="password"]')[1]!.setValue(confirmPassword)
}

describe('RegisterView.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('envía nombre/apellidos/email/password y muestra "revisa tu correo" en éxito', async () => {
    vi.mocked(authApi.register).mockResolvedValue({
      id: 'u1',
      email: 'ada@example.com',
      phone: null,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      emailVerified: false,
      phoneVerified: false,
      hasPassword: true,
    })
    const wrapper = await mountAtRegister()

    await fillForm(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(authApi.register).toHaveBeenCalledWith({ email: 'ada@example.com', nombre: 'Ada', apellidos: 'Lovelace', password: 'abcd1234' })
    expect(wrapper.text()).toContain('Revisa tu correo')
    expect(wrapper.text()).toContain('ada@example.com')
  })

  it('no entrega sesión -- la pantalla de éxito no navega sola', async () => {
    vi.mocked(authApi.register).mockResolvedValue({
      id: 'u1',
      email: 'ada@example.com',
      phone: null,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      emailVerified: false,
      phoneVerified: false,
      hasPassword: true,
    })
    const router = testRouter()
    await router.push('/register')
    const wrapper = mount(RegisterView, { global: { plugins: [router] } })

    await fillForm(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/register')
  })

  it('un email ya registrado muestra el error real del backend', async () => {
    vi.mocked(authApi.register).mockRejectedValue(new ApiError('Ese email ya está en uso', 409, 'duplicate_identifier'))
    const wrapper = await mountAtRegister()

    await fillForm(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Ese email ya está en uso')
    expect(wrapper.text()).not.toContain('Revisa tu correo')
  })

  it('contraseñas que no coinciden bloquean el envío con un error explícito', async () => {
    const wrapper = await mountAtRegister()

    await fillForm(wrapper, { password: 'abcd1234', confirmPassword: 'distinta5678' })
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Las contraseñas no coinciden')
    expect(authApi.register).not.toHaveBeenCalled()
  })
})
