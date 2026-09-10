/**
 * Formas del wire SSE de `GET /api/jobs/{jobId}/events` para eventos de
 * generación de TEXTURA (ticket 054, espejo de `TextureGenerationService`/
 * `preview_texture_patch`, Diseño técnico §13 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`). Mismo mecanismo
 * genérico que `generationEvents.ts` (029, geometría) -- el evento en sí
 * (`seq`/`stage`/`message`/`progressPct`/`payload`) es idéntico, solo
 * cambia la forma de `payload`.
 */

export interface TexturePreviewPatchRect {
  x: number
  y: number
  width: number
  height: number
}

/** `encoding: 'base64'` cuando el PNG del parche cabe inline (<=32 KB codificado); `encoding: 'asset_url'` cuando se subió como asset temporal (`GET /api/texture-previews/{jobId}/{fileName}`) -- ver Diseño técnico §13. Nunca toca `textures/{sha256}.png` ni ninguna fila de `mob_drafts`/`mob_revisions`. */
export type TexturePreviewPatchPayload = {
  type: 'preview_texture_patch'
  rect: TexturePreviewPatchRect
} & ({ encoding: 'base64'; data: string } | { encoding: 'asset_url'; url: string })

export interface TextureGenerationEvent {
  seq: number
  stage: string
  message: string | null
  progressPct: number | null
  payload: TexturePreviewPatchPayload | null
}
