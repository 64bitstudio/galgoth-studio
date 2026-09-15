/**
 * Ticket 078, `PROP-GS-AUTH-01` Fig. 06: adjunta `Authorization: Bearer`
 * a cualquier request y, ante un `401`, intenta refrescar la sesión UNA
 * vez y reintenta la request original. No es solo un temporizador de "ya
 * casi expira": auth-core-mc regenera su llave RSA de firma en cada
 * reinicio (limitación conocida de ese proyecto), así que un
 * `accessToken` vigente puede invalidarse al instante sin previo aviso
 * -- reaccionar al `401` real es lo único confiable.
 *
 * Hasta el ticket 085 solo se usaba para la API PROPIA de galgoth-studio
 * (`authApi.ts`, contra auth-core-mc, nunca pasaba por acá). Ticket 093
 * -- `accountApi.ts` reutiliza este mismo wrapper para las rutas
 * `/api/v1/account/**` de auth-core-mc: es agnóstico al origen (recibe
 * la URL completa), y el refresh-on-401 funciona igual sin importar cuál
 * de los dos backends devolvió el 401 (el token nuevo sirve para ambos).
 */
import { useSessionStore } from './sessionStore'

export async function authenticatedFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const session = useSessionStore()

  const response = await fetch(input, withAuthHeader(init, session.accessToken))
  if (response.status !== 401 || !session.refreshToken) {
    return response
  }

  const refreshed = await session.refresh()
  if (!refreshed) {
    return response
  }
  return fetch(input, withAuthHeader(init, session.accessToken))
}

function withAuthHeader(init: RequestInit, accessToken: string | null): RequestInit {
  if (!accessToken) {
    return init
  }
  return { ...init, headers: { ...init.headers, Authorization: `Bearer ${accessToken}` } }
}
