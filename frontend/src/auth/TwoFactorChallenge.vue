<script setup lang="ts">
/**
 * Ticket 108 -- paso compartido entre `LoginView.vue` (login con
 * contraseña) y `AuthCallbackView.vue` (login social) para completar un
 * login que quedó pendiente de 2FA (`sessionStore.login`/
 * `loginWithSocialCode` devuelven `{ status: 'two-factor-required',
 * pendingToken, method }` en vez de la sesión). Un solo componente en
 * vez de duplicar el formulario en ambas vistas -- mismo `pendingToken`/
 * contrato para las dos, ver `authApi.verifyTwoFactorLogin`.
 *
 * "Reenviar código" solo tiene sentido para `OTP_EMAIL`/`OTP_SMS` -- el
 * código de `TOTP` vive en la app autenticadora del usuario, nunca lo
 * manda auth-core-mc a ningún canal.
 */
import { ref } from 'vue'
import { ApiError } from '../api/ApiError'
import GButton from '../design-system/components/GButton.vue'
import * as authApi from './authApi'
import { useSessionStore } from './sessionStore'

const props = defineProps<{ pendingToken: string; method: string }>()
const emit = defineEmits<{ success: []; cancel: [] }>()

const session = useSessionStore()

const code = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
const resending = ref(false)
const resent = ref(false)

const canResend = props.method === 'OTP_EMAIL' || props.method === 'OTP_SMS'

async function submit(): Promise<void> {
  if (busy.value) {
    return
  }
  error.value = null
  busy.value = true
  try {
    await session.completeTwoFactorLogin(props.pendingToken, code.value.trim())
    emit('success')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'No se pudo verificar el código. Intenta de nuevo.'
  } finally {
    busy.value = false
  }
}

async function resend(): Promise<void> {
  if (resending.value) {
    return
  }
  error.value = null
  resent.value = false
  resending.value = true
  try {
    await authApi.resendTwoFactorCode(props.pendingToken)
    resent.value = true
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'No se pudo reenviar el código. Intenta de nuevo.'
  } finally {
    resending.value = false
  }
}
</script>

<template>
  <form class="two-factor" @submit.prevent="submit">
    <div class="two-factor__intro">
      <h1 class="two-factor__title">Verificación en dos pasos</h1>
      <p class="two-factor__subtitle">Ingresa el código de tu app autenticadora o el que te enviamos.</p>
    </div>

    <label class="two-factor__label">
      Código
      <input v-model="code" type="text" inputmode="numeric" autocomplete="one-time-code" placeholder="000000" required :disabled="busy" class="two-factor__input" aria-label="Código de verificación" />
    </label>

    <p v-if="error" class="two-factor__error">{{ error }}</p>
    <p v-if="resent" class="two-factor__hint">Código reenviado.</p>

    <GButton type="submit" variant="primary" :disabled="busy">{{ busy ? 'Verificando…' : 'Verificar' }}</GButton>

    <button v-if="canResend" type="button" class="two-factor__link" :disabled="resending" @click="resend">
      {{ resending ? 'Reenviando…' : 'Reenviar código' }}
    </button>

    <button type="button" class="two-factor__link" @click="emit('cancel')">Volver</button>
  </form>
</template>

<style scoped>
.two-factor {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 100%;
}

.two-factor__intro {
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.two-factor__title {
  margin: 0;
  font-size: var(--text-xl);
  color: var(--text);
}

.two-factor__subtitle {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
}

.two-factor__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.two-factor__input {
  min-height: var(--hit-target-min);
  width: 100%;
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
  letter-spacing: 0.1em;
  text-align: center;
}

.two-factor__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.two-factor__hint {
  margin: 0;
  color: var(--accent);
  font-size: var(--text-sm);
}

.two-factor__link {
  align-self: center;
  background: none;
  border: none;
  color: var(--accent);
  font-size: var(--text-sm);
  cursor: pointer;
}

.two-factor__link:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
