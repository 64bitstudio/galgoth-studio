import { describe, expect, it } from 'vitest'
import { emptyMobProjectModel } from '../emptyMobProjectModel'

describe('emptyMobProjectModel', () => {
  it('arranca sin bones ni cuboids, con el atlas 128x128 (mismo default que el backend)', () => {
    const model = emptyMobProjectModel('mob-1', 'project-1', 'Carcomido', 'humanoid')

    expect(model.bones).toEqual([])
    expect(model.cuboids).toEqual([])
    expect(model.texture).toEqual({ width: 128, height: 128, storageKey: null })
    expect(model.uv).toEqual({ textureWidth: 128, textureHeight: 128, regions: [], reservations: [] })
  })

  it('conserva mobId/projectId/name/baseType reales', () => {
    const model = emptyMobProjectModel('mob-1', 'project-1', 'Carcomido', 'arachnid')

    expect(model.mobId).toBe('mob-1')
    expect(model.projectId).toBe('project-1')
    expect(model.name).toBe('Carcomido')
    expect(model.baseType).toBe('arachnid')
  })
})
