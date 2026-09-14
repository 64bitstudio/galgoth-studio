import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../authApi'
import ForgotPasswordView from '../ForgotPasswordView.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, requestPasswordReset: vi.fn() }
})

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/forgot-password', component: ForgotPasswordView },
      { path: '/login', component: { template: '<div />' } },
    ],
  })
}

async function mountAtForgotPassword(): Promise<ReturnType<typeof mount>> {
  const router = testRouter()
  await router.push('/forgot-password')
  return mount(ForgotPasswordView, { global: { plugins: [router] } })
}

describe('ForgotPasswordView.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('envía el identificador y muestra el mensaje genérico en éxito', async () => {
    vi.mocked(authApi.requestPasswordReset).mockResolvedValue(undefined)
    const wrapper = await mountAtForgotPassword()

    await wrapper.find('input[type="text"]').setValue('ada@example.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(authApi.requestPasswordReset).toHaveBeenCalledWith('ada@example.com')
    expect(wrapper.text()).toContain('Revisa tu correo')
  })

  /**
   * auth-core-mc nunca revela si la cuenta existe (siempre 202) -- esta
   * pantalla no debe hacerlo tampoco ni siquiera ante un error real de
   * red, o estaría filtrando una distinción que el backend
   * deliberadamente no hace.
   */
  it('incluso si la petición falla, muestra el mismo mensaje genérico (nunca revela si la cuenta existe)', async () => {
    vi.mocked(authApi.requestPasswordReset).mockRejectedValue(new Error('network error'))
    const wrapper = await mountAtForgotPassword()

    await wrapper.find('input[type="text"]').setValue('ghost@example.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Revisa tu correo')
  })
})
