/**
 * Construcción pura del árbol bone->cuboids->sub-bones a partir de un
 * MobProjectModel -- separado de HierarchyPanel.vue para poder testearlo
 * sin montar un componente Vue (ticket 017, AC #1).
 */
import type { Bone, Cuboid, MobProjectModel } from '../domain/MobProjectModel'

export interface BoneNode {
  bone: Bone
  cuboids: Cuboid[]
  children: BoneNode[]
}

export function buildHierarchyTree(model: MobProjectModel): BoneNode[] {
  const cuboidsByBone = new Map<string, Cuboid[]>()
  for (const cuboid of model.cuboids) {
    const list = cuboidsByBone.get(cuboid.boneId) ?? []
    list.push(cuboid)
    cuboidsByBone.set(cuboid.boneId, list)
  }

  const childrenByParent = new Map<string | null, Bone[]>()
  for (const bone of model.bones) {
    const list = childrenByParent.get(bone.parentId) ?? []
    list.push(bone)
    childrenByParent.set(bone.parentId, list)
  }

  function buildLevel(parentId: string | null): BoneNode[] {
    return (childrenByParent.get(parentId) ?? []).map((bone) => ({
      bone,
      cuboids: cuboidsByBone.get(bone.id) ?? [],
      children: buildLevel(bone.id),
    }))
  }

  return buildLevel(null)
}
