/**
 * Ticket 078: única fuente de verdad de la sesión del usuario frente a
 * auth-core-mc. Persiste en `sessionStorage` -- mismo mecanismo que ya
 * usa la propia UI hospedada de auth-core-mc (`AuthCoreUi.saveSession`,
 * ver `PROP-GS-AUTH-01`), nunca una cookie (auth-core-mc no las usa,
 * ver el hallazgo de CSRF del ticket 077).
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from './authApi'

const STORAGE_KEY = 'galgoth-studio.session'

interface StoredSession {
  accessToken: string
  refreshToken: string
  user: authApi.RegisteredUser
}

/**
 * `sessionStorage` inaccesible (ventana privada, cuotas, storage
 * bloqueado) o JSON corrupto arrancan sin sesión en vez de romper el
 * montaje de la app entera -- nunca silencioso, ambos casos quedan en
 * consola para diagnosticar (mismo criterio que `draftModelStore`).
 */
function loadFromStorage(): StoredSession | null {
  let raw: string | null = null
  try {
    raw = sessionStorage.getItem(STORAGE_KEY)
  } catch (error) {
    console.warn('[sessionStore] sessionStorage inaccesible, arrancando sin sesión:', error)
  }
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as StoredSession
  } catch (error) {
    console.warn('[sessionStore] sesión persistida corrupta, ignorándola:', error)
  }
  return null
}

export const useSessionStore = defineStore('session', () => {
  const initial = loadFromStorage()
  const accessToken = ref<string | null>(initial?.accessToken ?? null)
  const refreshToken = ref<string | null>(initial?.refreshToken ?? null)
  const user = ref<authApi.RegisteredUser | null>(initial?.user ?? null)

  const isAuthenticated = computed(() => accessToken.value !== null)

  function persist(): void {
    if (accessToken.value && refreshToken.value && user.value) {
      const toStore: StoredSession = { accessToken: accessToken.value, refreshToken: refreshToken.value, user: user.value }
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(toStore))
    }
  }

  function setSession(tokens: authApi.TokenPair, sessionUser: authApi.RegisteredUser): void {
    accessToken.value = tokens.accessToken
    refreshToken.value = tokens.refreshToken
    user.value = sessionUser
    persist()
  }

  /** No entrega sesión -- el registro solo crea la cuenta (PROP-GS-AUTH-01 Fig. 02). */
  function register(request: authApi.RegisterRequest): Promise<authApi.RegisteredUser> {
    return authApi.register(request)
  }

  async function login(identifier: string, password: string): Promise<'ok' | 'two-factor-required'> {
    const result = await authApi.login(identifier, password)
    if (authApi.isTwoFactorRequired(result)) {
      return 'two-factor-required'
    }
    setSession(result.tokens, result.user)
    return 'ok'
  }

  /**
   * Ticket 078, Fig. 06: se llama ante CUALQUIER 401 de la API propia
   * (ver `authenticatedFetch.ts`), no solo por temporizador -- si
   * auth-core-mc se reinició (rota su llave RSA de firma en cada
   * arranque, limitación conocida de ese proyecto), el `accessToken`
   * vigente deja de validar al instante, pero el `refreshToken` opaco
   * sigue sirviendo para pedir uno nuevo.
   */
  async function refresh(): Promise<boolean> {
    if (!refreshToken.value) {
      return false
    }
    try {
      const tokens = await authApi.refreshAccessToken(refreshToken.value)
      accessToken.value = tokens.accessToken
      refreshToken.value = tokens.refreshToken
      persist()
      return true
    } catch (error) {
      console.warn('[sessionStore] refresh rechazado, cerrando sesión:', error)
      logout()
      return false
    }
  }

  function logout(): void {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    sessionStorage.removeItem(STORAGE_KEY)
  }

  return { accessToken, refreshToken, user, isAuthenticated, register, login, refresh, logout }
})
