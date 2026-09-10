/**
 * Ticket 056 -- cierra el gap real detectado en el checkpoint del PO: el
 * editor manual de textura (046 `textureEditorStore`/047 `TextureCanvas`)
 * pinta el atlas en memoria pero nunca lo subía al backend, así que
 * "Guardar" nunca reflejaba textura pintada a mano en la `mob_revision`
 * -- solo el camino de generación por IA vía "Aplicar" (ticket 054)
 * persistía algo real. Mismo espíritu que HU-30/HU-31 (Diseño técnico §6
 * de `docs/definiciones/galgoth-studio-fase3-textura.md`): "flush
 * obligatorio antes de crear una Revision" -- el guardado real debe
 * esperar (await) el `storageKey` oficial del `PUT /texture` pendiente
 * ANTES de invocar el endpoint que crea la `mob_revision`; una Revision
 * jamás debe apuntar a un `storageKey` no confirmado (el backend ya
 * defiende esto en profundidad, `DraftPersistenceService.saveRevision`,
 * ticket 045 -- este módulo es el lado cliente de esa misma garantía).
 *
 * Deliberadamente NO vive dentro de `textureEditorStore.ts` (ese store
 * documenta explícitamente en su cabecera que es 100% independiente de
 * `draftModelStore.ts` -- "Este store NO importa nada de
 * draftModelStore.ts") ni dentro de `draftModelStore.ts` (que tampoco
 * conoce nada de textura). Este módulo es el ÚNICO punto que conecta
 * ambos mundos, invocado por el flujo real de guardado
 * (`EditorToolbar.handleSave`) -- ninguno de los dos stores importa al
 * otro, ambos siguen siendo independientes entre sí.
 */
import type { MobProjectModel } from '../../domain/MobProjectModel'
import { uploadTexture } from '../../api/textureUploadApi'
import { encodeAtlasToPngBlob } from './textureAtlasEncode'
import { useTextureEditorStore } from './textureEditorStore'

/**
 * Si hay un atlas de textura cargado en memoria (el tab Textura ya se
 * montó al menos una vez para este mob en esta sesión de edición), lo
 * codifica a PNG, lo sube vía `PUT /texture`, y devuelve `model` con
 * `texture.storageKey` actualizado al oficial que el backend calculó --
 * nunca uno propuesto por el cliente (Diseño técnico §6). Si no hay
 * ningún atlas cargado (el tab Textura nunca se abrió en esta sesión),
 * es un no-op que devuelve `model` intacto: no hay ningún bitmap en
 * memoria que subir, y `model.texture.storageKey` ya refleja el último
 * estado persistido (o `null`, un mob todavía sin textura real).
 */
export async function flushPaintedTexture(model: MobProjectModel): Promise<MobProjectModel> {
  const textureEditorStore = useTextureEditorStore()
  const atlas = textureEditorStore.atlas
  if (!atlas) {
    return model
  }

  const png = await encodeAtlasToPngBlob(atlas)
  const { storageKey } = await uploadTexture(model.mobId, png)
  return { ...model, texture: { ...model.texture, storageKey } }
}
