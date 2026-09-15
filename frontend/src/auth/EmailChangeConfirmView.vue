<script setup lang="ts">
/**
 * Ticket 093: confirma el cambio de correo (HU de "Mi Perfil") -- lee
 * `token` de la query string (viene del link real que auth-core-mc manda
 * al correo NUEVO, ver auth-core-mc#056: `{origin}/change-email/confirm?token=...`
 * para clientes con `hosts_own_login_ui=true`, como galgoth-studio) y
 * llama `POST /api/v1/change-email/confirm`. Mismo patrón exacto que
 * `ResetPasswordView.vue` (ticket 080), simplificado -- acá no hay
 * ningún campo que llenar, solo confirmar el token.
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '../api/ApiError'
import * as authApi from './authApi'
import backgroundUrl from '../assets/auth/auth-reset-background.jpg'
import logoUrl from '../assets/auth/galgoth-logo.png'

const route = useRoute()
const token = computed(() => {
  const raw = route.query.token
  return typeof raw === 'string' ? raw : null
})

const busy = ref(true)
const done = ref(false)
const error = ref<string | null>(null)

async function confirm(): Promise<void> {
  if (!token.value) {
    busy.value = false
    return
  }
  try {
    await authApi.confirmEmailChange(token.value)
    done.value = true
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'No se pudo confirmar el cambio de correo. Intenta de nuevo.'
  } finally {
    busy.value = false
  }
}

onMounted(confirm)
</script>

<template>
  <div class="auth-view" :style="{ backgroundImage: `url(${backgroundUrl})` }">
    <section class="auth-view__hero" aria-hidden="true">
      <div class="auth-view__hero-scrim">
        <h2 class="auth-view__hero-title">Tu creatividad<br /><span class="auth-view__hero-accent">nunca se detiene</span></h2>
        <p class="auth-view__hero-subtitle">Confirma tu nuevo correo y sigue construyendo grandes ideas en Minecraft con IA.</p>
        <p class="auth-view__hero-tagline">Creatividad sin límites, para mundos extraordinarios.</p>
      </div>
    </section>

    <section class="auth-view__panel">
      <div v-if="!token" class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">Link inválido</h1>
        <p class="auth-view__message">Este link de confirmación no es válido. Solicita el cambio de correo de nuevo desde tu perfil.</p>
        <RouterLink class="auth-view__link" to="/usuario">Ir a mi perfil</RouterLink>
      </div>

      <div v-else-if="busy" class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">Confirmando…</h1>
      </div>

      <div v-else-if="done" class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">Correo actualizado</h1>
        <p class="auth-view__message">Tu correo se confirmó correctamente. Ya puedes usarlo para iniciar sesión.</p>
        <RouterLink class="auth-view__link" to="/login">Ir a iniciar sesión</RouterLink>
      </div>

      <div v-else class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">No se pudo confirmar</h1>
        <p class="auth-view__message">{{ error }}</p>
        <RouterLink class="auth-view__link" to="/usuario">Ir a mi perfil</RouterLink>
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

.auth-view__hero-subtitle {
  margin: 0;
  max-width: 32em;
  color: var(--muted);
  font-size: var(--text-md);
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
