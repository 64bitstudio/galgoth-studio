<script setup lang="ts">
/**
 * Ticket 078, `PROP-GS-AUTH-01` Fig. 02: registro real contra la API
 * directa de auth-core-mc. No entrega sesión -- solo crea la cuenta y
 * auth-core-mc dispara un correo de verificación real (Resend); esta
 * pantalla muestra el mensaje y ofrece ir a iniciar sesión, sin navegar
 * sola (la verificación de email es el ticket 079, pantalla aparte).
 *
 * Ticket 082: rediseño visual (fondo + referencia aportados por Marco),
 * mismo patrón split-screen ya corregido en `LoginView.vue`. El único
 * cambio de comportamiento real es "Confirmar contraseña" (validación de
 * cliente, nunca llega a `session.register`) -- el contrato con
 * `sessionStore`/`authApi` no cambia. Login social, el checkbox de
 * términos y el de newsletter se muestran fieles a la referencia pero
 * `disabled` con "Próximamente": ninguno tiene una función real hoy
 * (login social nunca se cableó aquí; no existe página de términos; el
 * backend no soporta preferencia de newsletter) -- nunca aparentar una
 * función que no existe.
 */
import { ref } from 'vue'
import { ApiError } from '../api/ApiError'
import GButton from '../design-system/components/GButton.vue'
import backgroundUrl from '../assets/auth/auth-register-background.jpg'
import logoUrl from '../assets/auth/galgoth-logo.png'
import { useSessionStore } from './sessionStore'

const session = useSessionStore()

const email = ref('')
const nombre = ref('')
const apellidos = ref('')
const password = ref('')
const confirmPassword = ref('')
const passwordVisible = ref(false)
const confirmPasswordVisible = ref(false)
const error = ref<string | null>(null)
const busy = ref(false)
const registered = ref(false)

async function submit(): Promise<void> {
  if (busy.value) {
    return
  }
  error.value = null
  if (password.value !== confirmPassword.value) {
    error.value = 'Las contraseñas no coinciden.'
    return
  }
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
  <div class="auth-view" :style="{ backgroundImage: `url(${backgroundUrl})` }">
    <section class="auth-view__hero" aria-hidden="true">
      <div class="auth-view__hero-scrim">
        <h2 class="auth-view__hero-title">Comienza<br /><span class="auth-view__hero-accent">tu aventura</span></h2>
        <p class="auth-view__hero-subtitle">Crea, edita y exporta mobs para Minecraft desde tu estudio creativo impulsado por IA.</p>
        <ul class="auth-view__hero-features">
          <li>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 2 3 7v6c0 5 4 8 9 9 5-1 9-4 9-9V7l-9-5Z" /></svg>
            <div><strong>Generación con IA</strong><span>Convierte tus ideas en mobs únicos</span></div>
          </li>
          <li>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M4 21v-6M4 9V3M12 21v-9M12 8V3M20 21v-4M20 13V3M1 15h6M9 8h6M17 13h6" /></svg>
            <div><strong>Edición de modelos</strong><span>Ajusta cada detalle a tu estilo</span></div>
          </li>
          <li>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 3v12m0-12 4 4m-4-4-4 4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" /></svg>
            <div><strong>Exportación .bbmodel</strong><span>Llévalos a tu mundo de Minecraft</span></div>
          </li>
        </ul>
        <p class="auth-view__hero-tagline">Creatividad sin límites, para mundos extraordinarios.</p>
      </div>
    </section>

    <section class="auth-view__panel">
      <div v-if="registered" class="auth-view__card">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <h1 class="auth-view__title">Revisa tu correo</h1>
        <p class="auth-view__message">Te enviamos un correo a <strong>{{ email }}</strong> para confirmar tu cuenta.</p>
        <RouterLink class="auth-view__link" to="/login">Ir a iniciar sesión</RouterLink>
      </div>

      <form v-else class="auth-view__card" @submit.prevent="submit">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <div class="auth-view__intro">
          <h1 class="auth-view__title">Crear cuenta</h1>
          <p class="auth-view__subtitle">Únete a Galgoth Studio para empezar a crear tus mobs.</p>
        </div>

        <div class="auth-view__row-fields">
          <label class="auth-view__label">
            Nombre
            <input v-model="nombre" type="text" autocomplete="given-name" required :disabled="busy" class="auth-view__input" aria-label="Nombre" />
          </label>
          <label class="auth-view__label">
            Apellidos
            <input v-model="apellidos" type="text" autocomplete="family-name" required :disabled="busy" class="auth-view__input" aria-label="Apellidos" />
          </label>
        </div>

        <label class="auth-view__label">
          Correo electrónico
          <input v-model="email" type="email" autocomplete="email" required :disabled="busy" class="auth-view__input" aria-label="Correo electrónico" />
        </label>

        <label class="auth-view__label">
          Contraseña
          <span class="auth-view__input-group">
            <input v-model="password" :type="passwordVisible ? 'text' : 'password'" autocomplete="new-password" required minlength="8" :disabled="busy" class="auth-view__input" aria-label="Contraseña" />
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

        <div class="auth-view__checks">
          <label class="auth-view__checkbox auth-view__checkbox--soon" title="Todavía no disponible en galgoth-studio">
            <input type="checkbox" disabled />
            Acepto <span class="auth-view__terms-link">términos y condiciones</span> <em>Próximamente</em>
          </label>
          <label class="auth-view__checkbox auth-view__checkbox--soon" title="Todavía no disponible en galgoth-studio">
            <input type="checkbox" disabled />
            Quiero recibir novedades del proyecto <em>Próximamente</em>
          </label>
        </div>

        <p v-if="error" class="auth-view__error">{{ error }}</p>
        <GButton type="submit" variant="primary" :disabled="busy">{{ busy ? 'Creando cuenta…' : 'Crear cuenta' }}</GButton>

        <div class="auth-view__divider"><span>o regístrate con</span></div>

        <div class="auth-view__social">
          <button type="button" class="auth-view__social-btn" disabled title="Todavía no disponible en galgoth-studio">
            <svg viewBox="0 0 24 24" width="18" height="18">
              <path fill="#EA4335" d="M12 10.2v3.9h5.5c-.24 1.4-1.7 4.1-5.5 4.1-3.3 0-6-2.7-6-6.1s2.7-6.1 6-6.1c1.9 0 3.1.8 3.9 1.5l2.6-2.5C16.8 3.3 14.6 2.3 12 2.3 6.9 2.3 2.7 6.5 2.7 11.6S6.9 21 12 21c6.9 0 8.9-4.9 8.9-7.4 0-.5-.1-.9-.1-1.3H12Z" />
            </svg>
            Google <em>Próximamente</em>
          </button>
          <button type="button" class="auth-view__social-btn" disabled title="Todavía no disponible en galgoth-studio">
            <svg viewBox="0 0 24 24" width="18" height="18">
              <path fill="#1877F2" d="M22 12.06C22 6.5 17.5 2 12 2S2 6.5 2 12.06c0 5 3.66 9.17 8.44 9.94v-7.03H7.9v-2.9h2.54V9.85c0-2.5 1.49-3.9 3.77-3.9 1.09 0 2.23.2 2.23.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56v1.88h2.78l-.44 2.9h-2.34V22c4.78-.77 8.44-4.94 8.44-9.94Z" />
            </svg>
            Facebook <em>Próximamente</em>
          </button>
        </div>

        <RouterLink class="auth-view__link" to="/login">¿Ya tienes cuenta? Iniciar sesión</RouterLink>
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

.auth-view__hero-features {
  list-style: none;
  margin: var(--space-2) 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.auth-view__hero-features li {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.auth-view__hero-features svg {
  flex-shrink: 0;
  padding: var(--space-2);
  box-sizing: content-box;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-md);
}

.auth-view__hero-features strong {
  display: block;
  font-size: var(--text-base);
}

.auth-view__hero-features span {
  display: block;
  color: var(--muted);
  font-size: var(--text-sm);
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
}

.auth-view__message {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-base);
  text-align: center;
}

.auth-view__row-fields {
  display: flex;
  gap: var(--space-3);
}

.auth-view__row-fields .auth-view__label {
  flex: 1 1 0;
  min-width: 0;
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

.auth-view__checks {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.auth-view__checkbox {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--muted);
  font-size: var(--text-xs);
}

.auth-view__checkbox--soon {
  cursor: not-allowed;
}

.auth-view__terms-link {
  color: var(--accent);
  text-decoration: underline;
}

.auth-view__checkbox em {
  margin-left: var(--space-1);
  color: var(--warning);
  font-style: normal;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.auth-view__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.auth-view__divider {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--muted);
  font-size: var(--text-xs);
}

.auth-view__divider::before,
.auth-view__divider::after {
  content: '';
  flex: 1;
  height: var(--border-width);
  background: var(--border);
}

.auth-view__social {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.auth-view__social-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
  cursor: not-allowed;
  opacity: 0.6;
}

.auth-view__social-btn em {
  margin-left: var(--space-1);
  color: var(--warning);
  font-style: normal;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
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
