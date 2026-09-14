import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { describe, expect, it } from 'vitest'
import TermsView from '../TermsView.vue'

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/terms', component: TermsView },
      { path: '/register', component: { template: '<div />' } },
    ],
  })
}

async function mountAtTerms(): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = testRouter()
  await router.push('/terms')
  const wrapper = mount(TermsView, { global: { plugins: [router] } })
  return { wrapper, router }
}

describe('TermsView.vue', () => {
  it('muestra la sección 1 por defecto, con las 7 secciones listadas en la navegación', async () => {
    const { wrapper } = await mountAtTerms()

    expect(wrapper.text()).toContain('Uso de la plataforma')
    expect(wrapper.text()).toContain('Al utilizar nuestros servicios, aceptas estos términos y condiciones en su totalidad.')
    const navItems = wrapper.findAll('.terms__nav-item')
    expect(navItems).toHaveLength(7)
  })

  it('hacer clic en otra sección de la navegación cambia el contenido visible', async () => {
    const { wrapper } = await mountAtTerms()

    await wrapper.findAll('.terms__nav-item')[6]!.trigger('click') // "7. Contacto"

    expect(wrapper.text()).toContain('soporte@galgoth.64bitstudio.com')
  })

  it('"Aceptar y continuar" está deshabilitado hasta marcar el checkbox', async () => {
    const { wrapper } = await mountAtTerms()

    const acceptBtn = wrapper.find('.terms__accept-btn')
    expect(acceptBtn.attributes('disabled')).toBeDefined()

    await wrapper.find('input[type="checkbox"]').setValue(true)

    expect(wrapper.find('.terms__accept-btn').attributes('disabled')).toBeUndefined()
  })

  it('"Aceptar y continuar" navega de regreso una vez aceptado', async () => {
    const { wrapper, router } = await mountAtTerms()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('.terms__accept-btn').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/register')
  })

  it('"Volver" navega de regreso sin necesidad de aceptar', async () => {
    const { wrapper, router } = await mountAtTerms()

    await wrapper.find('.terms__back-btn').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/register')
  })
})
