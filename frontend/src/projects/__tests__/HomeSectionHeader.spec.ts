import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { describe, expect, it } from 'vitest'
import HomeSectionHeader from '../HomeSectionHeader.vue'

async function testRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/projects', component: { template: '<div />' } },
    ],
  })
  router.push('/')
  await router.isReady()
  return router
}

describe('HomeSectionHeader.vue', () => {
  it('muestra el título recibido', async () => {
    const wrapper = mount(HomeSectionHeader, {
      props: { title: 'Continuar trabajando', to: '/projects' },
      global: { plugins: [await testRouter()] },
    })

    expect(wrapper.find('.home-section-header__title').text()).toBe('Continuar trabajando')
  })

  it('"Ver todos" enlaza a la ruta recibida en `to`', async () => {
    const wrapper = mount(HomeSectionHeader, {
      props: { title: 'Proyectos recientes', to: '/projects' },
      global: { plugins: [await testRouter()] },
    })

    const link = wrapper.find('.home-section-header__link')
    expect(link.text()).toContain('Ver todos')
    expect(link.attributes('href')).toBe('/projects')
  })
})
