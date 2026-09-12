/**
 * Ticket 078: a diferencia de `src/api/apiConfig.ts` (mismo origen que
 * este backend, ruta relativa), auth-core-mc vive en un origen DISTINTO
 * por ambiente (`auth[.-qa][-dev].64bitstudio.com`).
 *
 * Hallazgo real (ver ticket 077, `AUTH_CORE_MC_ISSUER`, mismo problema
 * del lado del backend): el frontend de galgoth-studio se construye UNA
 * sola vez (`VITE_API_BASE_URL=` vacío a propósito, ver `Jenkinsfile`) y
 * esa MISMA imagen se promueve dev→qa→prod sin rebuild (`Deploy a PROD
 * (sin rebuild)`). Un `VITE_AUTH_CORE_MC_URL` horneado en build-time
 * sería el valor de UN SOLO ambiente, incorrecto en los otros dos. Este
 * helper resuelve el host real de auth-core-mc en tiempo de ejecución,
 * a partir de qué dominio sirvió la página (`window.location.hostname`)
 * — nunca en build-time.
 */
const HOST_MAP: Readonly<Record<string, string>> = {
  'studio.galgoth.64bitstudio.com': 'https://auth.64bitstudio.com',
  'studio-qa.galgoth.64bitstudio.com': 'https://auth-qa.64bitstudio.com',
  'studio-dev.galgoth.64bitstudio.com': 'https://auth-dev.64bitstudio.com',
}

/** Mismo `client_id` en los 3 ambientes (auth-core-mc#052) — nunca varía por ambiente. */
export const AUTH_CLIENT_ID = 'galgoth-studio'

/**
 * `hostname` es parametrizable solo para tests — en producción siempre
 * se resuelve del `window.location.hostname` real de la pestaña.
 */
export function authCoreMcUrl(hostname: string = window.location.hostname): string {
  return HOST_MAP[hostname] ?? import.meta.env.VITE_AUTH_CORE_MC_URL ?? 'http://localhost:8080'
}
