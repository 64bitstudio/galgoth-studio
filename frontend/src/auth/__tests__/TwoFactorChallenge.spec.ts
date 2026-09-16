import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import * as authApi from '../authApi'
import TwoFactorChallenge from '../TwoFactorChallenge.vue'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, verifyTwoFactorLogin: vi.fn(), resendTwoFactorCode: vi.fn() }
})

const user: authApi.RegisteredUser = {
  id: 'u1',
  email: 'ada@example.com',
  phone: null,
  nombre: 'Ada',
  apellidos: 'Lovelace',
  emailVerified: true,
  phoneVerified: false,
  hasPassword: true,
  country: null,
  username: null,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('TwoFactorChallenge.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('código correcto emite "success" y guarda la sesión', async () => {
    vi.mocked(authApi.verifyTwoFactorLogin).mockResolvedValue({
      user,
      tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    })
    const wrapper = mount(TwoFactorChallenge, { props: { pendingToken: 'p1', method: 'TOTP' } })

    await wrapper.find('input[aria-label="Código de verificación"]').setValue('123456')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(authApi.verifyTwoFactorLogin).toHaveBeenCalledWith('p1', '123456')
    expect(wrapper.emitted('success')).toHaveLength(1)
  })

  it('código incorrecto muestra el error real y no emite "success"', async () => {
    vi.mocked(authApi.verifyTwoFactorLogin).mockRejectedValue(
      new ApiError('The pending token is invalid, expired, or already used', 400, 'invalid_token'),
    )
    const wrapper = mount(TwoFactorChallenge, { props: { pendingToken: 'p1', method: 'TOTP' } })

    await wrapper.find('input[aria-label="Código de verificación"]').setValue('000000')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('invalid, expired, or already used')
    expect(wrapper.emitted('success')).toBeUndefined()
  })

  it('"Volver" emite "cancel"', async () => {
    const wrapper = mount(TwoFactorChallenge, { props: { pendingToken: 'p1', method: 'TOTP' } })

    await wrapper.findAll('button').find((b) => b.text() === 'Volver')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('método TOTP no ofrece "Reenviar código" (nada que reenviar, vive en la app autenticadora)', () => {
    const wrapper = mount(TwoFactorChallenge, { props: { pendingToken: 'p1', method: 'TOTP' } })

    expect(wrapper.findAll('button').some((b) => b.text().includes('Reenviar'))).toBe(false)
  })

  it.each(['OTP_EMAIL', 'OTP_SMS'])('método %s ofrece "Reenviar código" y llama a resendTwoFactorCode', async (method) => {
    vi.mocked(authApi.resendTwoFactorCode).mockResolvedValue(undefined)
    const wrapper = mount(TwoFactorChallenge, { props: { pendingToken: 'p1', method } })

    const resendButton = wrapper.findAll('button').find((b) => b.text().includes('Reenviar'))!
    await resendButton.trigger('click')
    await flushPromises()

    expect(authApi.resendTwoFactorCode).toHaveBeenCalledWith('p1')
    expect(wrapper.text()).toContain('Código reenviado')
  })

  it('un reenvío rechazado (ej. 429 too_many_attempts) muestra el error real', async () => {
    vi.mocked(authApi.resendTwoFactorCode).mockRejectedValue(new ApiError('Too many attempts', 429, 'too_many_attempts'))
    const wrapper = mount(TwoFactorChallenge, { props: { pendingToken: 'p1', method: 'OTP_EMAIL' } })

    const resendButton = wrapper.findAll('button').find((b) => b.text().includes('Reenviar'))!
    await resendButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Too many attempts')
  })
})
