<script setup lang="ts">
/**
 * Ticket 078, `PROP-GS-AUTH-01` Fig. 03: login real contra la API
 * directa de auth-core-mc -- `sessionStore.login` hace el `POST
 * /api/v1/login` y guarda los tokens si tiene éxito.
 *
 * El caso `202` (2FA activo) se muestra como error explícito en vez de
 * fallar en silencio -- decisión ya tomada en `PROP-GS-AUTH-01` sección
 * 08: esta pantalla no lo maneja en v1 (nadie puede activarlo desde
 * galgoth-studio todavía).
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api/ApiError'
import GButton from '../design-system/components/GButton.vue'
import { useSessionStore } from './sessionStore'

const router = useRouter()
const session = useSessionStore()

const identifier = ref('')
const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)

async function submit(): Promise<void> {
  if (busy.value) {
    return
  }
  error.value = null
  busy.value = true
  try {
    const result = await session.login(identifier.value.trim(), password.value)
    if (result === 'two-factor-required') {
      error.value = 'Esta cuenta tiene verificación en dos pasos activa -- galgoth-studio todavía no soporta ese flujo.'
      return
    }
    router.push('/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'No se pudo iniciar sesión. Intenta de nuevo.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-view">
    <form class="auth-view__card" @submit.prevent="submit">
      <h1 class="auth-view__title">Iniciar sesión</h1>
      <label class="auth-view__label">
        Email o teléfono
        <input v-model="identifier" type="text" autocomplete="username" required :disabled="busy" class="auth-view__input" aria-label="Email o teléfono" />
      </label>
      <label class="auth-view__label">
        Contraseña
        <input v-model="password" type="password" autocomplete="current-password" required :disabled="busy" class="auth-view__input" aria-label="Contraseña" />
      </label>
      <p v-if="error" class="auth-view__error">{{ error }}</p>
      <GButton type="submit" variant="primary" :disabled="busy">{{ busy ? 'Ingresando…' : 'Iniciar sesión' }}</GButton>
      <RouterLink class="auth-view__link" to="/register">¿No tienes cuenta? Regístrate</RouterLink>
    </form>
  </div>
</template>

<style scoped>
.auth-view {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--space-4);
  background: var(--bg);
}

.auth-view__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 100%;
  max-width: 360px;
  padding: var(--space-6);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
}

.auth-view__title {
  margin: 0;
  font-size: var(--text-xl);
  color: var(--text);
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
</style>
