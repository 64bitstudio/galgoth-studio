import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import * as authApi from '../authApi'
import EmailChangeConfirmView from '../EmailChangeConfirmView.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, confirmEmailChange: vi.fn() }
})

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/change-email/confirm', component: EmailChangeConfirmView },
      { path: '/login', component: { template: '<div />' } },
      { path: '/usuario', component: { template: '<div />' } },
    ],
  })
}

async function mountWithToken(token: string | undefined): Promise<ReturnType<typeof mount>> {
  const router = testRouter()
  await router.push({ path: '/change-email/confirm', query: token === undefined ? {} : { token } })
  const wrapper = mount(EmailChangeConfirmView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('EmailChangeConfirmView.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sin token en la URL muestra "link inválido" y nunca llama al backend', async () => {
    const wrapper = await mountWithToken(undefined)

    expect(wrapper.text()).toContain('Link inválido')
    expect(authApi.confirmEmailChange).not.toHaveBeenCalled()
  })

  it('con token válido, confirma y muestra éxito', async () => {
    vi.mocked(authApi.confirmEmailChange).mockResolvedValue(undefined)

    const wrapper = await mountWithToken('real-token')

    expect(authApi.confirmEmailChange).toHaveBeenCalledWith('real-token')
    expect(wrapper.text()).toContain('Correo actualizado')
  })

  it('un token expirado/inválido muestra el error real del backend', async () => {
    vi.mocked(authApi.confirmEmailChange).mockRejectedValue(new ApiError('El link ya expiró.', 400, 'invalid_token'))

    const wrapper = await mountWithToken('expired-token')

    expect(wrapper.text()).toContain('El link ya expiró.')
    expect(wrapper.text()).not.toContain('Correo actualizado')
  })
})
