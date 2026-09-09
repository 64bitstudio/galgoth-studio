/**
 * URL base del backend REST, compartida por todos los clientes HTTP del
 * frontend (`projects/projectsApi.ts`, `projects/mobsApi.ts`, ticket
 * 021/022; `editor/draftPersistenceApi.ts`/`editor/thumbnailApi.ts`,
 * ticket 023). Sin proxy de Vite -- CORS es la estrategia elegida para
 * el origen local de desarrollo, ver `docs/definiciones/galgoth-studio-mvp.md` §9.
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * `mobs.thumbnail_key` (ticket 023) llega desde el backend como una ruta
 * relativa servible (`/api/mobs/{id}/thumbnail`), no una URL completa --
 * ver diseño en `ThumbnailService` del backend. Los componentes que
 * renderizan `<img>` (`ProjectCard.vue`, `MobCard.vue`) usan este helper
 * en vez de usar la clave cruda como `src` directamente.
 */
export function thumbnailUrl(key: string | null): string | null {
  return key ? `${API_BASE_URL}${key}` : null
}
