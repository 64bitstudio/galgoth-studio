<script setup lang="ts">
/**
 * Ticket 080: primer paso del flujo de reset -- pide un identificador
 * (email o teléfono) y dispara `POST /api/v1/password-reset/request`.
 * auth-core-mc responde `202` siempre, exista o no una cuenta con ese
 * identificador (nunca lo revela -- ver su propio `docs/API.md`), así
 * que esta pantalla muestra el mismo mensaje genérico sin importar el
 * resultado real. Sin diseño de referencia específico (Marco solo aportó
 * el de la pantalla de confirmación) -- mismo tratamiento visual y fondo
 * que `ResetPasswordView.vue`, formulario propio.
 */
import { ref } from 'vue'
import * as authApi from './authApi'
import GButton from '../design-system/components/GButton.vue'
import backgroundUrl from '../assets/auth/auth-reset-background.jpg'
import logoUrl from '../assets/auth/galgoth-logo.png'

const identifier = ref('')
const error = ref<string | null>(null)
const busy = ref(false)
const sent = ref(false)

async function submit(): Promise<void> {
  if (busy.value) {
    return
  }
  error.value = null
  busy.value = true
  try {
    await authApi.requestPasswordReset(identifier.value.trim())
    sent.value = true
  } catch {
    // Nunca debe distinguirse de un éxito -- auth-core-mc ya responde 202
    // siempre; un error de red aquí no debe filtrar información distinta
    // a la de una petición exitosa, así que se trata igual.
    sent.value = true
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-view" :style="{ backgroundImage: `url(${backgroundUrl})` }">
    <section class="auth-view__hero" aria-hidden="true">
      <div class="auth-view__hero-scrim">
        <h2 class="auth-view__hero-title">¿Olvidaste tu<br /><span class="auth-view__hero-accent">contraseña?</span></h2>
        <p class="auth-view__hero-subtitle">No te preocupes -- te ayudamos a recuperar el acceso a tu estudio creativo en un par de pasos.</p>
        <p class="auth-view__hero-tagline">Creatividad sin límites, para mundos extraordinarios.</p>
      </div>
    </section>

    <section class="auth-view__panel">
      <div v-if="sent" class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">Revisa tu correo</h1>
        <p class="auth-view__message">Si existe una cuenta asociada a esos datos, te enviamos instrucciones para restablecer tu contraseña.</p>
        <RouterLink class="auth-view__link" to="/login">Volver a iniciar sesión</RouterLink>
      </div>

      <form v-else class="auth-view__card" @submit.prevent="submit">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <div class="auth-view__intro">
          <h1 class="auth-view__title">Recuperar contraseña</h1>
          <p class="auth-view__subtitle">Ingresa tu correo o teléfono y te enviaremos un enlace para restablecer tu contraseña.</p>
        </div>

        <label class="auth-view__label">
          Correo o teléfono
          <input v-model="identifier" type="text" autocomplete="username" required :disabled="busy" class="auth-view__input" aria-label="Correo o teléfono" />
        </label>

        <p v-if="error" class="auth-view__error">{{ error }}</p>
        <GButton type="submit" variant="primary" :disabled="busy">{{ busy ? 'Enviando…' : 'Enviar instrucciones' }}</GButton>

        <RouterLink class="auth-view__link" to="/login">Volver a iniciar sesión</RouterLink>
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

.auth-view__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.auth-view__link {
  align-self: center;
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
