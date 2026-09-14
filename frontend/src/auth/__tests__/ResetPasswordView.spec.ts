import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import * as authApi from '../authApi'
import ResetPasswordView from '../ResetPasswordView.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, confirmPasswordReset: vi.fn() }
})

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/password-reset/confirm', component: ResetPasswordView },
      { path: '/login', component: { template: '<div />' } },
      { path: '/forgot-password', component: { template: '<div />' } },
    ],
  })
}

async function mountWithToken(token: string | undefined): Promise<ReturnType<typeof mount>> {
  const router = testRouter()
  await router.push({ path: '/password-reset/confirm', query: token === undefined ? {} : { token } })
  return mount(ResetPasswordView, { global: { plugins: [router] } })
}

describe('ResetPasswordView.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sin token en la URL muestra "link inválido" y no renderiza el formulario', async () => {
    const wrapper = await mountWithToken(undefined)

    expect(wrapper.text()).toContain('Link inválido')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('con token válido, envía token+newPassword y muestra éxito', async () => {
    vi.mocked(authApi.confirmPasswordReset).mockResolvedValue(undefined)
    const wrapper = await mountWithToken('real-token')

    await wrapper.find('input[type="password"]').setValue('newpass123')
    await wrapper.findAll('input[type="password"]')[1]!.setValue('newpass123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(authApi.confirmPasswordReset).toHaveBeenCalledWith('real-token', 'newpass123')
    expect(wrapper.text()).toContain('Contraseña actualizada')
  })

  it('contraseñas que no coinciden bloquean el envío del lado del cliente', async () => {
    const wrapper = await mountWithToken('real-token')

    await wrapper.find('input[type="password"]').setValue('newpass123')
    await wrapper.findAll('input[type="password"]')[1]!.setValue('distinta456')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Las contraseñas no coinciden')
    expect(authApi.confirmPasswordReset).not.toHaveBeenCalled()
  })

  it('un token expirado/inválido muestra el error real del backend sin cambiar nada', async () => {
    vi.mocked(authApi.confirmPasswordReset).mockRejectedValue(
      new ApiError('Password reset link is invalid or has expired', 400, 'invalid_token'),
    )
    const wrapper = await mountWithToken('expired-token')

    await wrapper.find('input[type="password"]').setValue('newpass123')
    await wrapper.findAll('input[type="password"]')[1]!.setValue('newpass123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Password reset link is invalid or has expired')
    expect(wrapper.text()).not.toContain('Contraseña actualizada')
  })
})
