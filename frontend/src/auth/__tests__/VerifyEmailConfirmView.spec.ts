import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import * as authApi from '../authApi'
import VerifyEmailConfirmView from '../VerifyEmailConfirmView.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, confirmEmailVerification: vi.fn() }
})

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/verify-email/confirm', component: VerifyEmailConfirmView },
      { path: '/login', component: { template: '<div />' } },
      { path: '/register', component: { template: '<div />' } },
    ],
  })
}

async function mountWithToken(token: string | undefined): Promise<ReturnType<typeof mount>> {
  const router = testRouter()
  await router.push({ path: '/verify-email/confirm', query: token === undefined ? {} : { token } })
  const wrapper = mount(VerifyEmailConfirmView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('VerifyEmailConfirmView.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sin token en la URL muestra "link inválido" y nunca llama al backend', async () => {
    const wrapper = await mountWithToken(undefined)

    expect(wrapper.text()).toContain('Link inválido')
    expect(authApi.confirmEmailVerification).not.toHaveBeenCalled()
  })

  it('con token válido, confirma y muestra éxito', async () => {
    vi.mocked(authApi.confirmEmailVerification).mockResolvedValue(undefined)

    const wrapper = await mountWithToken('real-token')

    expect(authApi.confirmEmailVerification).toHaveBeenCalledWith('real-token')
    expect(wrapper.text()).toContain('Correo confirmado')
  })

  it('un token expirado/inválido muestra el error real del backend', async () => {
    vi.mocked(authApi.confirmEmailVerification).mockRejectedValue(new ApiError('El link ya expiró.', 400, 'invalid_token'))

    const wrapper = await mountWithToken('expired-token')

    expect(wrapper.text()).toContain('El link ya expiró.')
    expect(wrapper.text()).not.toContain('Correo confirmado')
  })
})
