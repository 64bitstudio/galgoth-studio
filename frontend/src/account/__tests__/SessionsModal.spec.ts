import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import SessionsModal from '../SessionsModal.vue'
import IconBrowserChrome from '../../design-system/icons/IconBrowserChrome.vue'
import IconBrowserSafari from '../../design-system/icons/IconBrowserSafari.vue'
import IconDevice from '../../design-system/icons/IconDevice.vue'
import type { SessionSummary } from '../../auth/accountApi'

beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '')
    }
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open')
    }
  }
})

const recentSession: SessionSummary = {
  id: 's1',
  browser: 'Chrome',
  os: 'macOS',
  city: 'Ciudad de México',
  country: 'México',
  createdAt: '2026-01-01T00:00:00Z',
  lastUsedAt: new Date().toISOString(),
  current: true,
}

const oldSession: SessionSummary = {
  id: 's2',
  browser: 'Safari',
  os: 'iOS',
  city: null,
  country: null,
  createdAt: '2026-01-01T00:00:00Z',
  lastUsedAt: '2026-01-01T00:00:00Z',
  current: false,
}

describe('SessionsModal.vue', () => {
  it('muestra ciudad+país cuando el backend los manda, y "Activa ahora" para uso reciente', () => {
    const wrapper = mount(SessionsModal, { props: { sessions: [recentSession], revokingSessionId: null } })

    expect(wrapper.text()).toContain('Ciudad de México, México')
    expect(wrapper.text()).toContain('Activa ahora')
    expect(wrapper.text()).toContain('Actual')
  })

  it('sin city/country (degradación de GeoLite2) no muestra ubicación, solo dispositivo y fecha', () => {
    const wrapper = mount(SessionsModal, { props: { sessions: [oldSession], revokingSessionId: null } })

    expect(wrapper.text()).toContain('Safari')
    expect(wrapper.text()).not.toContain('null')
  })

  it('"Cerrar sesión" desde el menú de una sesión que no es la actual emite revoke con su id', async () => {
    const wrapper = mount(SessionsModal, { props: { sessions: [oldSession], revokingSessionId: null } })

    await wrapper.find('[aria-label="Acciones de la sesión en Safari"]').trigger('click')
    await wrapper.findAll('button[role="menuitem"]').find((b) => b.text() === 'Cerrar sesión')!.trigger('click')

    expect(wrapper.emitted('revoke')).toEqual([['s2']])
  })

  it('el ítem "Cerrar sesión" de la sesión actual está deshabilitado con su razón visible', async () => {
    const wrapper = mount(SessionsModal, { props: { sessions: [recentSession], revokingSessionId: null } })

    await wrapper.find('[aria-label="Acciones de la sesión en Chrome"]').trigger('click')

    const item = wrapper.findAll('button[role="menuitem"]').find((b) => b.text().includes('Cerrar sesión'))!
    expect(item.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('No puedes cerrar la sesión que estás usando ahora.')
  })

  // Ticket 095 (hallazgo real reportado en vivo: faltaban los íconos de navegador) -- `UserAgentParser.browser()` solo devuelve un puñado de valores reconocidos; cualquier otro cae al ícono genérico.
  it('muestra el ícono de marca del navegador cuando lo reconoce (Chrome/Safari)', () => {
    const wrapper = mount(SessionsModal, { props: { sessions: [recentSession, oldSession], revokingSessionId: null } })

    expect(wrapper.findComponent(IconBrowserChrome).exists()).toBe(true)
    expect(wrapper.findComponent(IconBrowserSafari).exists()).toBe(true)
  })

  it('un navegador no reconocido cae al ícono genérico de dispositivo', () => {
    const wrapper = mount(SessionsModal, {
      props: { sessions: [{ ...oldSession, browser: 'Internet Explorer' }], revokingSessionId: null },
    })

    expect(wrapper.findComponent(IconDevice).exists()).toBe(true)
  })

  it('"Cerrar" emite cancel', async () => {
    const wrapper = mount(SessionsModal, { props: { sessions: [], revokingSessionId: null } })

    await wrapper.findAll('button').find((b) => b.text() === 'Cerrar')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
