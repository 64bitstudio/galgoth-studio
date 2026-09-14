<script setup lang="ts">
/**
 * Ticket 080: pantalla de confirmación del reset -- lee `token` de la
 * query string (viene del link real que auth-core-mc manda por correo,
 * ver auth-core-mc#056: `{origin}/password-reset/confirm?token=...` para
 * clientes con `hosts_own_login_ui=true`, como galgoth-studio) y llama
 * `POST /api/v1/password-reset/confirm`.
 *
 * La regla real de fuerza de contraseña vive enteramente en
 * `PasswordPolicy` del lado de auth-core-mc (8+ caracteres, una letra y
 * un número) -- esta pantalla no la duplica ni la re-valida, solo
 * bloquea del lado del cliente el caso que SÍ le compete (que "Nueva
 * contraseña" y "Confirmar contraseña" coincidan, mismo patrón del
 * ticket 082) y muestra el error real del backend si la contraseña es
 * débil o el token es inválido/expiró.
 */
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '../api/ApiError'
import * as authApi from './authApi'
import GButton from '../design-system/components/GButton.vue'
import backgroundUrl from '../assets/auth/auth-reset-background.jpg'
import logoUrl from '../assets/auth/galgoth-logo.png'

const route = useRoute()
const token = computed(() => {
  const raw = route.query.token
  return typeof raw === 'string' ? raw : null
})

const newPassword = ref('')
const confirmPassword = ref('')
const passwordVisible = ref(false)
const confirmPasswordVisible = ref(false)
const error = ref<string | null>(null)
const busy = ref(false)
const done = ref(false)

async function submit(): Promise<void> {
  if (busy.value || !token.value) {
    return
  }
  error.value = null
  if (newPassword.value !== confirmPassword.value) {
    error.value = 'Las contraseñas no coinciden.'
    return
  }
  busy.value = true
  try {
    await authApi.confirmPasswordReset(token.value, newPassword.value)
    done.value = true
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'No se pudo actualizar la contraseña. Intenta de nuevo.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-view" :style="{ backgroundImage: `url(${backgroundUrl})` }">
    <section class="auth-view__hero" aria-hidden="true">
      <div class="auth-view__hero-scrim">
        <h2 class="auth-view__hero-title">Tu creatividad<br /><span class="auth-view__hero-accent">nunca se detiene</span></h2>
        <p class="auth-view__hero-subtitle">Recupera el acceso a tu estudio y sigue construyendo grandes ideas en Minecraft con IA.</p>
        <p class="auth-view__hero-tagline">Creatividad sin límites, para mundos extraordinarios.</p>
      </div>
    </section>

    <section class="auth-view__panel">
      <div v-if="!token" class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">Link inválido</h1>
        <p class="auth-view__message">Este link de recuperación no es válido. Solicita uno nuevo para restablecer tu contraseña.</p>
        <RouterLink class="auth-view__link" to="/forgot-password">Solicitar un nuevo link</RouterLink>
      </div>

      <div v-else-if="done" class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">Contraseña actualizada</h1>
        <p class="auth-view__message">Tu contraseña se actualizó correctamente. Ya puedes iniciar sesión con ella.</p>
        <RouterLink class="auth-view__link" to="/login">Ir a iniciar sesión</RouterLink>
      </div>

      <form v-else class="auth-view__card" @submit.prevent="submit">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <div class="auth-view__intro">
          <h1 class="auth-view__title">Restablecer contraseña</h1>
          <p class="auth-view__subtitle">Ingresa una nueva contraseña para recuperar el acceso a tu estudio creativo.</p>
        </div>

        <label class="auth-view__label">
          Nueva contraseña
          <span class="auth-view__input-group">
            <input v-model="newPassword" :type="passwordVisible ? 'text' : 'password'" autocomplete="new-password" required minlength="8" :disabled="busy" class="auth-view__input" aria-label="Nueva contraseña" />
            <button type="button" class="auth-view__input-toggle" :aria-label="passwordVisible ? 'Ocultar contraseña' : 'Mostrar contraseña'" :aria-pressed="passwordVisible" @click="passwordVisible = !passwordVisible"><svg v-if="passwordVisible" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" /><circle cx="12" cy="12" r="3" /></svg><svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 3l18 18M10.6 10.7a3 3 0 0 0 4.2 4.2M6.6 6.7C4 8.4 2 12 2 12s3.5 7 10 7c1.8 0 3.4-.4 4.7-1.1M17.5 17.6C20 15.9 22 12 22 12s-1.6-3.2-4.7-5.2a12.6 12.6 0 0 0-2.5-1.3" /></svg></button>
          </span>
        </label>

        <label class="auth-view__label">
          Confirmar contraseña
          <span class="auth-view__input-group">
            <input v-model="confirmPassword" :type="confirmPasswordVisible ? 'text' : 'password'" autocomplete="new-password" required minlength="8" :disabled="busy" class="auth-view__input" aria-label="Confirmar contraseña" />
            <button type="button" class="auth-view__input-toggle" :aria-label="confirmPasswordVisible ? 'Ocultar contraseña' : 'Mostrar contraseña'" :aria-pressed="confirmPasswordVisible" @click="confirmPasswordVisible = !confirmPasswordVisible"><svg v-if="confirmPasswordVisible" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" /><circle cx="12" cy="12" r="3" /></svg><svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 3l18 18M10.6 10.7a3 3 0 0 0 4.2 4.2M6.6 6.7C4 8.4 2 12 2 12s3.5 7 10 7c1.8 0 3.4-.4 4.7-1.1M17.5 17.6C20 15.9 22 12 22 12s-1.6-3.2-4.7-5.2a12.6 12.6 0 0 0-2.5-1.3" /></svg></button>
          </span>
        </label>

        <p class="auth-view__hint">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M12 2 4 5v6c0 5 3.5 9 8 11 4.5-2 8-6 8-11V5l-8-3Z" /></svg>
          Tu contraseña debe tener al menos 8 caracteres, una letra y un número.
        </p>

        <p v-if="error" class="auth-view__error">{{ error }}</p>
        <GButton type="submit" variant="primary" :disabled="busy">{{ busy ? 'Actualizando…' : 'Actualizar contraseña' }}</GButton>

        <div class="auth-view__divider"><span>&nbsp;</span></div>

        <RouterLink class="auth-view__link" to="/login">Volver a <strong>iniciar sesión</strong></RouterLink>
      </form>
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
  gap: var(--space-4);
  width: 100%;
  max-width: 420px;
  padding: var(--space-6);
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
  align-self: center;
  object-fit: contain;
}

.auth-view__intro {
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.auth-view__title {
  margin: 0;
  font-size: var(--text-xl);
  color: var(--text);
  text-align: center;
}

.auth-view__subtitle {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
  text-align: center;
}

.auth-view__message {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-base);
  text-align: center;
}

.auth-view__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.auth-view__input {
  min-height: var(--hit-target-min);
  width: 100%;
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.auth-view__input-group {
  position: relative;
  display: flex;
}

.auth-view__input-group .auth-view__input {
  padding-right: calc(var(--hit-target-min) + var(--space-1));
}

.auth-view__input-toggle {
  position: absolute;
  top: 0;
  right: 0;
  width: var(--hit-target-min);
  height: var(--hit-target-min);
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--muted);
  cursor: pointer;
}

.auth-view__input-toggle:hover {
  color: var(--text);
}

.auth-view__hint {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-3);
  background: var(--accent-soft);
  border: var(--border-width) solid rgba(72, 229, 160, 0.35);
  border-radius: var(--radius-md);
  color: var(--muted);
  font-size: var(--text-xs);
}

.auth-view__hint svg {
  flex-shrink: 0;
  color: var(--accent);
  margin-top: 1px;
}

.auth-view__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.auth-view__divider {
  display: flex;
  align-items: center;
}

.auth-view__divider span {
  flex: 1;
  height: var(--border-width);
  background: var(--border);
  font-size: 0;
}

.auth-view__link {
  align-self: center;
  color: var(--muted);
  font-size: var(--text-sm);
}

.auth-view__link strong {
  color: var(--accent);
  font-weight: 600;
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
