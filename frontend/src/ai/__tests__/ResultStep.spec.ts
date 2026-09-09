import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ResultStep from '../steps/ResultStep.vue'

describe('ResultStep.vue', () => {
  it('muestra los datos de ejemplo por defecto y un aviso de que son de ejemplo', () => {
    const wrapper = mount(ResultStep)

    expect(wrapper.text()).toContain('Carcomido')
    expect(wrapper.text()).toContain('27')
    expect(wrapper.text()).toContain('datos de ejemplo')
  })

  it('acepta props para mostrar datos distintos', () => {
    const wrapper = mount(ResultStep, { props: { mobName: 'Tejedora', cuboidCount: 12, boneCount: 5, textureResolution: '64×64' } })

    expect(wrapper.text()).toContain('Tejedora')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('64×64')
  })

  it('las 3 acciones de HU-12 están presentes pero deshabilitadas, con la razón visible', () => {
    const wrapper = mount(ResultStep)

    const actions = wrapper.findAll('.result-step__action')
    expect(actions).toHaveLength(3)
    expect(actions.map((a) => a.text())).toEqual(['Descartar', 'Regenerar', 'Usar este modelo'])
    for (const action of actions) {
      expect(action.attributes('disabled')).toBeDefined()
    }
    expect(wrapper.text()).toContain('se habilitan cuando el resultado de una generación real esté disponible')
  })
})
