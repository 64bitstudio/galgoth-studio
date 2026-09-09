/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** URL base del backend REST (ticket 021, primera integración real frontend->backend). Sin CORS/proxy de Vite -- ver docs/definiciones/galgoth-studio-mvp.md §9. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
