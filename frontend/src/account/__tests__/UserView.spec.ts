import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '../../auth/sessionStore'
import UserView from '../UserView.vue'

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

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const authProfile = {
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
  createdAt: '2026-01-15T00:00:00Z',
}

const productProfile = { avatarUrl: null, notifyEmail: true, notifyProductNews: false, notifySaveReminders: true }

const defaultSession = { id: 's1', browser: 'Chrome', os: 'macOS', city: null, country: null, createdAt: '2026-01-01T00:00:00Z', lastUsedAt: '2026-01-01T00:00:00Z', current: true }

function baseFetchMock(overrides: Partial<Record<string, () => Response>> = {}): ReturnType<typeof vi.fn> {
  return vi.fn<typeof fetch>(async (url, init) => {
    const path = String(url)
    const method = init?.method ?? 'GET'

    if (path.includes('/api/v1/account/profile') && method === 'GET') {
      return overrides.getProfile?.() ?? jsonResponse(authProfile)
    }
    if (path.includes('/api/v1/account/profile') && method === 'PATCH') {
      const body = JSON.parse(init?.body as string)
      return overrides.updateProfile?.() ?? jsonResponse({ ...authProfile, ...body })
    }
    if (path.includes('/api/account/profile')) {
      return overrides.getProductProfile?.() ?? jsonResponse(productProfile)
    }
    if (path.includes('/api/v1/account/sessions/revoke-others')) {
      return overrides.revokeAll?.() ?? new Response(null, { status: 204 })
    }
    if (path.includes('/api/v1/account/sessions')) {
      return overrides.listSessions?.() ?? jsonResponse([defaultSession])
    }
    if (path.includes('/api/v1/account/connected-providers') && method === 'DELETE') {
      return overrides.unlinkProvider?.() ?? new Response(null, { status: 204 })
    }
    if (path.includes('/api/v1/account/connected-providers')) {
      return overrides.listProviders?.() ?? jsonResponse([{ provider: 'GOOGLE', linked: false }, { provider: 'FACEBOOK', linked: true }])
    }
    if (path.includes('/api/v1/account') && method === 'DELETE') {
      return overrides.deleteAccount?.() ?? new Response(null, { status: 204 })
    }
    return jsonResponse({})
  })
}

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/usuario', component: UserView },
      { path: '/login', component: { template: '<div />' } },
      { path: '/', component: { template: '<div />' } },
    ],
  })
}

async function mountUserView(fetchMock: ReturnType<typeof vi.fn>, query: Record<string, string> = {}): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  vi.stubGlobal('fetch', fetchMock)
  const router = testRouter()
  await router.push({ path: '/usuario', query })
  const wrapper = mount(UserView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('UserView.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const session = useSessionStore()
    session.accessToken = 'token'
    session.refreshToken = 'refresh-token'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('carga y muestra nombre, correo, "Miembro desde" y la inicial como avatar (sin foto subida)', async () => {
    const { wrapper } = await mountUserView(baseFetchMock())

    expect(wrapper.text()).toContain('Ada Lovelace')
    expect(wrapper.text()).toContain('ada@example.com')
    expect(wrapper.text()).toContain('Miembro desde')
    expect(wrapper.find('.user-view__avatar-initial').text()).toBe('A')
  })

  it('un error de carga real muestra un mensaje explícito, no una pantalla vacía', async () => {
    const { wrapper } = await mountUserView(baseFetchMock({ getProfile: () => jsonResponse({ error: 'unauthorized', message: 'Sesión inválida.' }, 401) }))

    expect(wrapper.text()).toContain('Sesión inválida.')
  })

  it('"Editar perfil" lleva el foco al campo Nombre de Información personal', async () => {
    // `document.activeElement` solo se actualiza para nodos realmente adjuntos al DOM -- a diferencia
    // del resto de esta suite, este test necesita `attachTo` y desmontar al terminar.
    vi.stubGlobal('fetch', baseFetchMock())
    const router = testRouter()
    await router.push('/usuario')
    const wrapper = mount(UserView, { global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    const editButton = wrapper.findAll('button').find((b) => b.text() === 'Editar perfil')!
    await editButton.trigger('click')
    await flushPromises()

    expect(document.activeElement).toBe(wrapper.find('[aria-label="Nombre"]').element)
    wrapper.unmount()
  })

  it('guardar información personal llama a PATCH /account/profile y actualiza sessionStore.user', async () => {
    const { wrapper } = await mountUserView(baseFetchMock())
    const session = useSessionStore()

    await wrapper.find('[aria-label="Nombre"]').setValue('Ada Marie')
    const saveButton = wrapper.findAll('button').find((b) => b.text() === 'Guardar cambios')!
    await saveButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Información guardada.')
    expect(session.user?.nombre).toBe('Ada Marie')
  })

  it('"Cambiar" abre el modal de correo, confirmar manda la solicitud y muestra el banner de "revisa tu correo"', async () => {
    const { wrapper } = await mountUserView(baseFetchMock())

    const changeButton = wrapper.findAll('button').find((b) => b.text() === 'Cambiar')!
    await changeButton.trigger('click')
    await wrapper.find('input[type="email"]').setValue('nueva@example.com')
    const confirmButton = wrapper.findAll('button').find((b) => b.text().includes('Enviar'))!
    await confirmButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('nueva@example.com')
  })

  // Ticket 079 -- badge de estado + reenvío, solo tiene sentido si hay correo y no está verificado ya.
  it('con emailVerified=true muestra el badge "Verificado" y no el botón de reenviar', async () => {
    const { wrapper } = await mountUserView(baseFetchMock())

    expect(wrapper.text()).toContain('Verificado')
    expect(wrapper.text()).not.toContain('Reenviar correo de verificación')
  })

  it('con emailVerified=false muestra "Sin verificar" y el botón de reenviar', async () => {
    const { wrapper } = await mountUserView(baseFetchMock({ getProfile: () => jsonResponse({ ...authProfile, emailVerified: false }) }))

    expect(wrapper.text()).toContain('Sin verificar')
    expect(wrapper.text()).toContain('Reenviar correo de verificación')
  })

  it('reenviar correo de verificación llama a POST /verify-email/request con el userId real y confirma el envío', async () => {
    const fetchMock = baseFetchMock({ getProfile: () => jsonResponse({ ...authProfile, emailVerified: false }) })
    const { wrapper } = await mountUserView(fetchMock)

    const resendButton = wrapper.findAll('button').find((b) => b.text().includes('Reenviar correo de verificación'))!
    await resendButton.trigger('click')
    await flushPromises()

    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/v1/verify-email/request'))
    expect(call).toBeDefined()
    expect(JSON.parse(call![1]!.body as string)).toEqual({ userId: 'u1' })
    expect(wrapper.text()).toContain('Te enviamos un correo nuevo de verificación.')
  })

  // Ticket 095 -- "Cambiar contraseña"/"Sesiones activas" ahora abren un modal aparte (mockup original) en vez del formulario/lista inline del 093.
  it('con hasPassword=false, la fila dice "Establecer contraseña" y el modal no pide la contraseña actual', async () => {
    const { wrapper } = await mountUserView(baseFetchMock({ getProfile: () => jsonResponse({ ...authProfile, hasPassword: false }) }))

    expect(wrapper.text()).toContain('Establecer contraseña')
    const row = wrapper.findAll('button').find((b) => b.text().includes('Establecer contraseña'))!
    await row.trigger('click')
    await flushPromises()

    expect(wrapper.find('[aria-label="Contraseña actual"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="Nueva contraseña"]').exists()).toBe(true)
  })

  it('"Cambiar contraseña" abre el modal, confirmar llama a PATCH /account/password con la actual y la nueva', async () => {
    const fetchMock = baseFetchMock()
    const { wrapper } = await mountUserView(fetchMock)

    const row = wrapper.findAll('button').find((b) => b.text().includes('Cambiar contraseña'))!
    await row.trigger('click')
    await flushPromises()

    await wrapper.find('[aria-label="Contraseña actual"]').setValue('vieja1234')
    await wrapper.find('[aria-label="Nueva contraseña"]').setValue('nueva12345')
    const confirmButton = wrapper.findAll('button').find((b) => b.text() === 'Actualizar contraseña')!
    await confirmButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Contraseña actualizada.')
    expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/api/v1/account/password') && (c[1] as RequestInit | undefined)?.method === 'PATCH')).toBe(true)
  })

  it('muestra las cuentas conectadas reales (Google sin vincular, Facebook vinculada)', async () => {
    const { wrapper } = await mountUserView(baseFetchMock())

    expect(wrapper.text()).toContain('Google')
    expect(wrapper.text()).toContain('Facebook')
    expect(wrapper.text()).toContain('Vinculada')
    expect(wrapper.findAll('button').some((b) => b.text() === 'Conectar')).toBe(true)
  })

  it('"Conectar" navega el navegador completo a la URL real devuelta por el backend', async () => {
    // jsdom no navega de verdad entre documentos -- reemplazamos `window.location` por un objeto
    // mutable real para poder verificar la asignación (mismo mecanismo estándar de Vitest/jsdom).
    const realLocation = window.location
    Object.defineProperty(window, 'location', { value: { ...realLocation, href: '' }, writable: true, configurable: true })
    const fetchMock = baseFetchMock()
    fetchMock.mockImplementation(async (url) => {
      const path = String(url)
      if (path.includes('/link-provider/google')) {
        return jsonResponse({ redirectUrl: 'https://auth-dev.example.com/oauth2/authorization/x' })
      }
      if (path.includes('/api/v1/account/profile')) {
        return jsonResponse(authProfile)
      }
      if (path.includes('/api/account/profile')) {
        return jsonResponse(productProfile)
      }
      if (path.includes('/api/v1/account/sessions')) {
        return jsonResponse([])
      }
      if (path.includes('/api/v1/account/connected-providers')) {
        return jsonResponse([{ provider: 'GOOGLE', linked: false }])
      }
      return jsonResponse({})
    })
    const { wrapper } = await mountUserView(fetchMock)

    const connectButton = wrapper.findAll('button').find((b) => b.text() === 'Conectar')!
    await connectButton.trigger('click')
    await flushPromises()

    expect(window.location.href).toContain('/oauth2/authorization/x')

    Object.defineProperty(window, 'location', { value: realLocation, writable: true, configurable: true })
  })

  // Ticket 095 / auth-core-mc#069 -- menú "···" de cada proveedor vinculado, "Desvincular" llama al DELETE nuevo.
  it('"Desvincular" en el menú de un proveedor vinculado llama a DELETE /connected-providers/{provider} y lo muestra como no vinculado', async () => {
    const fetchMock = baseFetchMock()
    const { wrapper } = await mountUserView(fetchMock)

    const menuTrigger = wrapper.find('[aria-label="Acciones de Facebook"]')
    await menuTrigger.trigger('click')
    await flushPromises()
    const unlinkItem = wrapper.findAll('button[role="menuitem"]').find((b) => b.text() === 'Desvincular')!
    await unlinkItem.trigger('click')
    await flushPromises()

    expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/connected-providers/facebook') && (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Conectar')).toBe(true)
  })

  it('un 409 real al desvincular (único método de acceso) se muestra como mensaje explícito', async () => {
    const fetchMock = baseFetchMock({
      unlinkProvider: () => jsonResponse({ error: 'cannot_unlink_last_login_method', message: 'Cannot unlink your only way to sign in -- set a password first' }, 409),
    })
    const { wrapper } = await mountUserView(fetchMock)

    const menuTrigger = wrapper.find('[aria-label="Acciones de Facebook"]')
    await menuTrigger.trigger('click')
    await flushPromises()
    const unlinkItem = wrapper.findAll('button[role="menuitem"]').find((b) => b.text() === 'Desvincular')!
    await unlinkItem.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Cannot unlink your only way to sign in')
  })

  it('"Sesiones activas" abre el modal con las sesiones reales y permite cerrar una que no es la actual', async () => {
    const fetchMock = baseFetchMock({
      listSessions: () =>
        jsonResponse([
          { id: 's1', browser: 'Chrome', os: 'macOS', city: 'Ciudad de México', country: 'México', createdAt: '2026-01-01T00:00:00Z', lastUsedAt: '2026-01-01T00:00:00Z', current: true },
          { id: 's2', browser: 'Safari', os: 'iOS', city: null, country: null, createdAt: '2026-01-01T00:00:00Z', lastUsedAt: '2026-01-01T00:00:00Z', current: false },
        ]),
    })
    const { wrapper } = await mountUserView(fetchMock)

    const row = wrapper.findAll('button').find((b) => b.text().includes('Sesiones activas'))!
    await row.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Chrome')
    expect(wrapper.text()).toContain('Safari')
    expect(wrapper.text()).toContain('Ciudad de México')

    const menuTrigger = wrapper.find('[aria-label="Acciones de la sesión en Safari"]')
    await menuTrigger.trigger('click')
    await flushPromises()
    const revokeItem = wrapper.findAll('button[role="menuitem"]').find((b) => b.text() === 'Cerrar sesión')!
    await revokeItem.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Safari')
  })

  it('la sesión actual no puede cerrarse desde su propio menú (ítem deshabilitado con razón visible)', async () => {
    const { wrapper } = await mountUserView(baseFetchMock())

    const row = wrapper.findAll('button').find((b) => b.text().includes('Sesiones activas'))!
    await row.trigger('click')
    await flushPromises()

    const menuTrigger = wrapper.find('[aria-label="Acciones de la sesión en Chrome"]')
    await menuTrigger.trigger('click')
    await flushPromises()

    const revokeItem = wrapper.findAll('button[role="menuitem"]').find((b) => b.text().includes('Cerrar sesión'))!
    expect(revokeItem.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('No puedes cerrar la sesión que estás usando ahora.')
  })

  it('las 3 preferencias reales se muestran con su estado real, y "Tema oscuro" queda deshabilitado con indicación explícita', async () => {
    const { wrapper } = await mountUserView(baseFetchMock())

    const onSwitch = wrapper.find('[aria-label="Notificaciones por correo"]')
    const offSwitch = wrapper.find('[aria-label="Novedades del producto"]')
    expect(onSwitch.attributes('aria-checked')).toBe('true')
    expect(offSwitch.attributes('aria-checked')).toBe('false')
    expect(wrapper.text()).toContain('Sin tema claro todavía')
  })

  it('togglear una preferencia llama a PATCH /account/preferences con las 3 explícitas', async () => {
    const fetchMock = baseFetchMock()
    const { wrapper } = await mountUserView(fetchMock)

    await wrapper.find('[aria-label="Notificaciones por correo"]').trigger('click')
    await flushPromises()

    const call = fetchMock.mock.calls.find((c) => String(c[0]).includes('/api/account/preferences'))!
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual({ notifyEmail: false, notifyProductNews: false, notifySaveReminders: true })
  })

  it('"Cerrar sesión en todos los dispositivos" pide confirmación, y al confirmar cierra sesión y navega a /login', async () => {
    const fetchMock = baseFetchMock()
    const { wrapper, router } = await mountUserView(fetchMock)
    const session = useSessionStore()

    const dangerButton = wrapper.findAll('button').find((b) => b.text() === 'Cerrar todas')!
    await dangerButton.trigger('click')
    const confirmButton = wrapper.findAll('button').find((b) => b.text() === 'Cerrar todas' && b.element !== dangerButton.element)!
    await confirmButton.trigger('click')
    await flushPromises()

    expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/sessions/revoke-others'))).toBe(true)
    expect(session.accessToken).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('"Eliminar cuenta" exige escribir el correo real antes de habilitar el botón, y al confirmar elimina y navega a /login', async () => {
    const fetchMock = baseFetchMock()
    const { wrapper, router } = await mountUserView(fetchMock)
    const session = useSessionStore()

    const dangerButton = wrapper.findAll('button').find((b) => b.text() === 'Eliminar cuenta')!
    await dangerButton.trigger('click')
    await wrapper.find('.delete-account-dialog__input').setValue('ada@example.com')
    const confirmButton = wrapper.findAll('button').find((b) => b.text().includes('Eliminar mi cuenta'))!
    await confirmButton.trigger('click')
    await flushPromises()

    expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/api/v1/account') && (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(true)
    expect(session.accessToken).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  // Ticket 063 de auth-core-mc -- al volver de vincular una cuenta social, aterriza acá con ?linked=/?link_error= en la query.
  it('con ?linked=google en la query, muestra el banner de éxito y limpia la query', async () => {
    const { wrapper, router } = await mountUserView(baseFetchMock(), { linked: 'google' })

    expect(wrapper.text()).toContain('Google vinculada correctamente')
    expect(router.currentRoute.value.fullPath).toBe('/usuario')
  })

  it('con ?link_error=already_linked en la query, muestra el mensaje explícito correspondiente', async () => {
    const { wrapper } = await mountUserView(baseFetchMock(), { link_error: 'already_linked' })

    expect(wrapper.text()).toContain('ya está vinculada a otro usuario')
  })
})
