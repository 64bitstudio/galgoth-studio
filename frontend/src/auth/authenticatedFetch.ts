/**
 * Ticket 078, `PROP-GS-AUTH-01` Fig. 06: adjunta `Authorization: Bearer`
 * a las llamadas a la API PROPIA de galgoth-studio (nunca a auth-core-mc
 * -- `authApi.ts` no pasa por acá) y, ante un `401`, intenta refrescar
 * la sesión UNA vez y reintenta la request original. No es solo un
 * temporizador de "ya casi expira": auth-core-mc regenera su llave RSA
 * de firma en cada reinicio (limitación conocida de ese proyecto), así
 * que un `accessToken` vigente puede invalidarse al instante sin previo
 * aviso -- reaccionar al `401` real es lo único confiable.
 *
 * Ninguna ruta de este backend exige autenticación todavía (ticket 077,
 * decisión explícita de Marco) -- este wrapper no tiene ningún efecto
 * observable hoy, queda listo para cuando la primera ruta protegida
 * exista.
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
