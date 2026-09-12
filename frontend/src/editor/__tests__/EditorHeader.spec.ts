import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import EditorHeader from '../EditorHeader.vue'

/**
 * Ticket 050 (HU-41): el tab "Textura" deja de estar deshabilitado --
 * pasa a ser una tab funcional, controlada por `activeTab`/`update:active-tab`
 * (mismo mecanismo de v-model que ya expone `GTabs.vue`). "Animación"
 * permanece "Próximamente" (fuera de alcance de Fase 3, ticket 002/036).
 *
 * Post-073: el breadcrumb usa `<router-link>` reales (mismo diseño y
 * funcionalidad que `ProjectDetail.vue`) -- mount() necesita un router de
 * prueba montado, si no `<router-link>` lanza fuera de un componente real.
 */
function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/projects', component: { template: '<div />' } },
      { path: '/projects/:id', component: { template: '<div />' } },
    ],
  })
}

function mountHeader(props: Partial<{ projectId: string; projectName: string; mobName: string; activeTab: string }> = {}) {
  return mount(EditorHeader, {
    props: { projectId: 'p1', projectName: 'Galgoth Models', mobName: 'Carcomido', activeTab: 'modelo', ...props },
    global: { plugins: [testRouter()] },
  })
}

describe('EditorHeader.vue', () => {
  it('el tab "Textura" ya NO está deshabilitado', () => {
    const wrapper = mountHeader()
    const textura = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Textura'))!

    expect(textura.attributes('disabled')).toBeUndefined()
    expect(textura.text()).not.toContain('Próximamente')
  })

  it('el tab "Animación" sigue deshabilitado con "Próximamente" visible (fuera de alcance de Fase 3)', () => {
    const wrapper = mountHeader()
    const animacion = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Animación'))!

    expect(animacion.attributes('disabled')).toBeDefined()
    expect(animacion.text()).toContain('Próximamente')
  })

  it('clickear el tab "Textura" emite update:active-tab con "textura"', async () => {
    const wrapper = mountHeader()
    const textura = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Textura'))!

    await textura.trigger('click')

    expect(wrapper.emitted('update:activeTab')?.[0]).toEqual(['textura'])
  })

  it('refleja el `activeTab` recibido como la tab activa (aria-selected)', () => {
    const wrapper = mountHeader({ activeTab: 'textura' })
    const textura = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Textura'))!
    const modelo = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Modelo'))!

    expect(textura.attributes('aria-selected')).toBe('true')
    expect(modelo.attributes('aria-selected')).toBe('false')
  })

  // Post-073 -- mismo diseño y funcionalidad del breadcrumb de ProjectDetail.vue/ProjectsDashboard.vue.
  describe('breadcrumb (post-073)', () => {
    it('muestra la jerarquía completa "Galgoth Studio > Mis proyectos > {proyecto} > {mob}"', () => {
      const wrapper = mountHeader({ projectName: 'Galgoth Models', mobName: 'Carcomido' })
      const breadcrumb = wrapper.get('.editor-header__breadcrumb')

      expect(breadcrumb.text()).toContain('Galgoth Studio')
      expect(breadcrumb.text()).toContain('Mis proyectos')
      expect(breadcrumb.text()).toContain('Galgoth Models')
      expect(breadcrumb.text()).toContain('Carcomido')
    })

    it('"Galgoth Studio"/"Mis proyectos"/el proyecto son links reales, el mob es texto (página actual)', () => {
      const wrapper = mountHeader({ projectId: 'p1', projectName: 'Galgoth Models', mobName: 'Carcomido' })
      const breadcrumb = wrapper.get('.editor-header__breadcrumb')
      const links = breadcrumb.findAll('a')

      expect(links.map((a) => a.attributes('href'))).toEqual(['/', '/projects', '/projects/p1'])
      expect(breadcrumb.find('span').text()).toBe('Carcomido')
    })
  })
})
