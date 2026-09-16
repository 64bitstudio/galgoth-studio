<script setup lang="ts">
/**
 * Ticket 072 de auth-core-mc -- ruta FIJA: es el `redirect_uri` real ya
 * registrado para `galgoth-studio` en Google/Facebook y en
 * `identity_client.redirect_uris` (los 3 ambientes, verificado en la
 * base de datos real) -- cambiar este path rompería el login social en
 * producción, mismo criterio que `/password-reset/confirm`/`/change-email/confirm`.
 *
 * auth-core-mc aterriza acá con `?code=...` (éxito, canjear vía
 * `authApi.exchangeSocialCode`) o `?error=social_login_cancelled`
 * (consentimiento denegado en el proveedor, ver
 * `SocialLoginFailureHandler`) -- cualquier otro caso (sesión
 * expirada/callback manipulado) auth-core-mc lo manda a su propia página
 * genérica (`/ui/social-login-error`), nunca aquí (Decisión 4 del
 * ticket 037: ese bucket nunca resuelve tenant, así que no puede saber
 * de qué cliente es).
 *
 * El caso 2FA se trata igual que `LoginView.vue`: galgoth-studio todavía
 * no tiene pantalla para completarlo (`PROP-GS-AUTH-01` sección 08).
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/ApiError'
import backgroundUrl from '../assets/auth/auth-hero-background.jpg'
import logoUrl from '../assets/auth/galgoth-logo.png'
import { useSessionStore } from './sessionStore'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const code = computed(() => {
  const raw = route.query.code
  return typeof raw === 'string' ? raw : null
})

const busy = ref(true)
const error = ref<string | null>(null)

function messageFor(errorParam: string | null): string {
  if (errorParam === 'social_login_cancelled') {
    return 'Cancelaste el inicio de sesión en la pantalla del proveedor.'
  }
  return 'No se pudo completar el inicio de sesión. Intenta de nuevo.'
}

/** Ticket 072 -- mismo destino que guardó `LoginView.vue` antes de navegar fuera, si había uno. */
function redirectAfterLogin(): void {
  let redirect: string | null = null
  try {
    redirect = sessionStorage.getItem('galgoth-studio.postLoginRedirect')
    sessionStorage.removeItem('galgoth-studio.postLoginRedirect')
  } catch {
    // sessionStorage inaccesible -- se degrada a "/", mismo criterio que sessionStore.ts.
  }
  router.push(redirect && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/')
}

async function completeLogin(): Promise<void> {
  const errorParam = typeof route.query.error === 'string' ? route.query.error : null
  if (errorParam) {
    error.value = messageFor(errorParam)
    busy.value = false
    return
  }
  if (!code.value) {
    error.value = messageFor(null)
    busy.value = false
    return
  }
  try {
    const result = await session.loginWithSocialCode(code.value)
    if (result === 'two-factor-required') {
      error.value = 'Esta cuenta tiene verificación en dos pasos activa -- galgoth-studio todavía no soporta ese flujo.'
      busy.value = false
      return
    }
    redirectAfterLogin()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : messageFor(null)
    busy.value = false
  }
}

onMounted(completeLogin)
</script>

<template>
  <div class="auth-view" :style="{ backgroundImage: `url(${backgroundUrl})` }">
    <section class="auth-view__hero" aria-hidden="true">
      <div class="auth-view__hero-scrim">
        <h2 class="auth-view__hero-title">Da vida a<br /><span class="auth-view__hero-accent">tus ideas</span></h2>
        <p class="auth-view__hero-tagline">Creatividad sin límites, para mundos extraordinarios.</p>
      </div>
    </section>

    <section class="auth-view__panel">
      <div v-if="busy" class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">Iniciando sesión…</h1>
      </div>

      <div v-else class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">No se pudo iniciar sesión</h1>
        <p class="auth-view__message">{{ error }}</p>
        <RouterLink class="auth-view__link" to="/login">Volver a intentar</RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-view {
  position: relative;
  display: flex;
  min-height: 100vh;
  background-color: var(--bg);
  background-size: cover;
  background-position: center;
}

.auth-view::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(6, 10, 14, 0.4);
}

.auth-view__hero,
.auth-view__panel {
  position: relative;
}

.auth-view__hero {
  flex: 1 1 55%;
  display: flex;
  align-items: flex-end;
}

.auth-view__hero-scrim {
  width: 100%;
  padding: var(--space-8) clamp(var(--space-6), 6vw, 72px);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  color: var(--text);
  background: linear-gradient(180deg, transparent, rgba(6, 10, 14, 0.55) 40%, rgba(6, 10, 14, 0.92));
}

.auth-view__hero-title {
  margin: 0;
  font-size: clamp(28px, 3.4vw, 44px);
  line-height: 1.1;
}

.auth-view__hero-accent {
  color: var(--accent);
}

.auth-view__hero-tagline {
  margin: var(--space-2) 0 0;
  padding-top: var(--space-4);
  border-top: var(--border-width) solid var(--border);
  color: var(--muted);
  font-size: var(--text-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.auth-view__panel {
  flex: 1 1 45%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}

.auth-view__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
  width: 100%;
  max-width: 420px;
  padding: var(--space-6);
  text-align: center;
  background-color: rgba(17, 24, 32, 0.72);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: var(--border-width) solid rgba(72, 229, 160, 0.35);
  border-radius: var(--radius-lg);
  box-shadow:
    var(--shadow-md),
    0 0 40px -12px rgba(72, 229, 160, 0.35);
}

.auth-view__logo {
  width: 72px;
  height: 72px;
  object-fit: contain;
}

.auth-view__title {
  margin: 0;
  font-size: var(--text-xl);
  color: var(--text);
}

.auth-view__message {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
}

.auth-view__link {
  color: var(--accent);
  font-size: var(--text-sm);
}

@media (max-width: 980px) {
  .auth-view__hero {
    display: none;
  }

  .auth-view__panel {
    flex: 1 1 100%;
  }
}
</style>
