/**
 * Modelo vacío (sin bones/cuboids) desde el que arranca cualquier flujo
 * que necesite un `MobProjectModel` de partida coherente -- espejo
 * TypeScript de `MobGenerationService.emptyModelFor` (backend, 028/029).
 * Compartido entre el pipeline de generación IA (`ai/generationEvents.ts`,
 * preview antes de la primera operación real) y el editor manual real
 * (`editor/MobEditor.vue`, 034: un mob sin ningún draft/revisión todavía
 * arranca acá, mismo criterio de "Estado inicial de un mob" del diseño
 * técnico).
 */
import type { BaseType, MobProjectModel } from './MobProjectModel'

const TEXTURE_SIZE = 128

export function emptyMobProjectModel(mobId: string, projectId: string, name: string, baseType: BaseType): MobProjectModel {
  return {
    mobId,
    projectId,
    name,
    baseType,
    units: 'minecraft_pixels',
    bones: [],
    cuboids: [],
    texture: { width: TEXTURE_SIZE, height: TEXTURE_SIZE, storageKey: null },
    uv: { textureWidth: TEXTURE_SIZE, textureHeight: TEXTURE_SIZE, regions: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}
