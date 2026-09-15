import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useSessionStore } from '../../auth/sessionStore'
import router from '../router'

/**
 * Ticket 087 -- el guard vive en el propio `router.ts` (`meta.requiresAuth`
 * + `beforeEach`); estos tests ejercen el router real exportado (no una
 * copia de prueba) para verificar el comportamiento real de producción,
 * no una reimplementación paralela del guard.
 */
describe('router (ticket 087 -- guard de rutas autenticadas)', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    await router.push('/login') // arranca en una ruta pública, sin sesión.
  })

  it('una ruta con meta.requiresAuth sin sesión redirige a /login preservando la ruta de vuelta', async () => {
    await router.push('/projects')

    expect(router.currentRoute.value.fullPath).toBe('/login?redirect=/projects')
  })

  it('una ruta con meta.requiresAuth con sesión activa navega normalmente', async () => {
    const session = useSessionStore()
    session.accessToken = 'token'
    session.refreshToken = 'refresh'

    await router.push('/projects')

    expect(router.currentRoute.value.path).toBe('/projects')
  })

  it('una ruta pública (login) es alcanzable sin sesión, sin redirigir', async () => {
    await router.push('/register')

    expect(router.currentRoute.value.path).toBe('/register')
  })

  it('project-detail NO exige sesión (un proyecto PUBLIC debe ser visible sin loguearse, ver ticket 085/088)', async () => {
    await router.push('/projects/p1')

    expect(router.currentRoute.value.path).toBe('/projects/p1')
  })

  // Ticket 088 -- Explorar es pública por diseño (HU-5/HU-6): ninguna de las dos rutas exige sesión.
  it('/explore NO exige sesión', async () => {
    await router.push('/explore')

    expect(router.currentRoute.value.path).toBe('/explore')
  })

  it('/explore/:id NO exige sesión', async () => {
    await router.push('/explore/p1')

    expect(router.currentRoute.value.path).toBe('/explore/p1')
  })
})
