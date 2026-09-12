<script setup lang="ts">
/**
 * Ticket 078, `PROP-GS-AUTH-01` Fig. 02: registro real contra la API
 * directa de auth-core-mc. No entrega sesión -- solo crea la cuenta y
 * auth-core-mc dispara un correo de verificación real (Resend); esta
 * pantalla muestra el mensaje y ofrece ir a iniciar sesión, sin navegar
 * sola (la verificación de email es el ticket 079, pantalla aparte).
 */
import { ref } from 'vue'
import { ApiError } from '../api/ApiError'
import GButton from '../design-system/components/GButton.vue'
import { useSessionStore } from './sessionStore'

const session = useSessionStore()

const email = ref('')
const nombre = ref('')
const apellidos = ref('')
const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)
const registered = ref(false)

async function submit(): Promise<void> {
  if (busy.value) {
    return
  }
  error.value = null
  busy.value = true
  try {
    await session.register({
      email: email.value.trim(),
      nombre: nombre.value.trim(),
      apellidos: apellidos.value.trim(),
      password: password.value,
    })
    registered.value = true
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'No se pudo completar el registro. Intenta de nuevo.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-view">
    <div v-if="registered" class="auth-view__card">
      <h1 class="auth-view__title">Revisa tu correo</h1>
      <p class="auth-view__message">Te enviamos un correo a <strong>{{ email }}</strong> para confirmar tu cuenta.</p>
      <RouterLink class="auth-view__link" to="/login">Ir a iniciar sesión</RouterLink>
    </div>
    <form v-else class="auth-view__card" @submit.prevent="submit">
      <h1 class="auth-view__title">Crear cuenta</h1>
      <label class="auth-view__label">
        Nombre
        <input v-model="nombre" type="text" autocomplete="given-name" required :disabled="busy" class="auth-view__input" aria-label="Nombre" />
      </label>
      <label class="auth-view__label">
        Apellidos
        <input v-model="apellidos" type="text" autocomplete="family-name" required :disabled="busy" class="auth-view__input" aria-label="Apellidos" />
      </label>
      <label class="auth-view__label">
        Email
        <input v-model="email" type="email" autocomplete="email" required :disabled="busy" class="auth-view__input" aria-label="Email" />
      </label>
      <label class="auth-view__label">
        Contraseña
        <input v-model="password" type="password" autocomplete="new-password" required minlength="8" :disabled="busy" class="auth-view__input" aria-label="Contraseña" />
      </label>
      <p v-if="error" class="auth-view__error">{{ error }}</p>
      <GButton type="submit" variant="primary" :disabled="busy">{{ busy ? 'Creando cuenta…' : 'Crear cuenta' }}</GButton>
      <RouterLink class="auth-view__link" to="/login">¿Ya tienes cuenta? Inicia sesión</RouterLink>
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

.auth-view__message {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-base);
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
