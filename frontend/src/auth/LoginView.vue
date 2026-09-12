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
 *
 * Ticket 081: rediseño visual (logo + fondo aportados por Marco, layout
 * split-screen de la referencia) -- la lógica de arriba no cambia en
 * absoluto, solo el markup/estilos de abajo. Los botones de login social
 * y el link de recuperación de contraseña se muestran fieles a la
 * referencia pero `disabled`, con la etiqueta "Próximamente": ninguno de
 * los dos está cableado del lado de galgoth-studio hoy (login social
 * nunca se implementó aquí -- ver hallazgo del ticket 052/053; reset de
 * password es el ticket 080, sin arrancar) -- nunca aparentar una función
 * que no existe.
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api/ApiError'
import GButton from '../design-system/components/GButton.vue'
import backgroundUrl from '../assets/auth/auth-hero-background.jpg'
import logoUrl from '../assets/auth/galgoth-logo.png'
import { useSessionStore } from './sessionStore'

const router = useRouter()
const session = useSessionStore()

const identifier = ref('')
const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)
const passwordVisible = ref(false)

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
    <section class="auth-view__hero" :style="{ backgroundImage: `url(${backgroundUrl})` }" aria-hidden="true">
      <div class="auth-view__hero-scrim">
        <h2 class="auth-view__hero-title">Da vida a<br /><span class="auth-view__hero-accent">tus ideas</span></h2>
        <p class="auth-view__hero-subtitle">Crea, personaliza y comparte mobs para Minecraft con el poder de la IA.</p>
        <ul class="auth-view__hero-features">
          <li>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 2 3 7v6c0 5 4 8 9 9 5-1 9-4 9-9V7l-9-5Z" /></svg>
            <div><strong>Crea con IA</strong><span>Convierte ideas en mobs únicos</span></div>
          </li>
          <li>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M4 21v-6M4 9V3M12 21v-9M12 8V3M20 21v-4M20 13V3M1 15h6M9 8h6M17 13h6" /></svg>
            <div><strong>Edita y personaliza</strong><span>Ajusta cada detalle a tu gusto</span></div>
          </li>
          <li>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 3v12m0-12 4 4m-4-4-4 4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" /></svg>
            <div><strong>Exporta y comparte</strong><span>Llévalos a tu mundo de Minecraft</span></div>
          </li>
        </ul>
        <p class="auth-view__hero-tagline">Creatividad sin límites, para mundos extraordinarios.</p>
      </div>
    </section>

    <section class="auth-view__panel">
      <form class="auth-view__card" @submit.prevent="submit">
        <img :src="logoUrl" alt="Galgoth Studio" class="auth-view__logo" />
        <div class="auth-view__intro">
          <h1 class="auth-view__title">Iniciar sesión</h1>
          <p class="auth-view__subtitle">Accede a tu estudio para crear, editar y exportar mobs.</p>
        </div>

        <label class="auth-view__label">
          Email o teléfono
          <input v-model="identifier" type="text" autocomplete="username" required :disabled="busy" class="auth-view__input" aria-label="Email o teléfono" />
        </label>
        <label class="auth-view__label">
          Contraseña
          <span class="auth-view__input-group">
            <input v-model="password" :type="passwordVisible ? 'text' : 'password'" autocomplete="current-password" required :disabled="busy" class="auth-view__input" aria-label="Contraseña" />
            <button type="button" class="auth-view__input-toggle" :aria-label="passwordVisible ? 'Ocultar contraseña' : 'Mostrar contraseña'" :aria-pressed="passwordVisible" @click="passwordVisible = !passwordVisible"><svg v-if="passwordVisible" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" /><circle cx="12" cy="12" r="3" /></svg><svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 3l18 18M10.6 10.7a3 3 0 0 0 4.2 4.2M6.6 6.7C4 8.4 2 12 2 12s3.5 7 10 7c1.8 0 3.4-.4 4.7-1.1M17.5 17.6C20 15.9 22 12 22 12s-1.6-3.2-4.7-5.2a12.6 12.6 0 0 0-2.5-1.3" /></svg></button>
          </span>
        </label>

        <div class="auth-view__row">
          <label class="auth-view__checkbox">
            <input type="checkbox" checked disabled />
            Recordarme
          </label>
          <span class="auth-view__soon" title="Todavía no disponible en galgoth-studio">¿Olvidaste tu contraseña? <em>Próximamente</em></span>
        </div>

        <p v-if="error" class="auth-view__error">{{ error }}</p>
        <GButton type="submit" variant="primary" :disabled="busy">{{ busy ? 'Ingresando…' : 'Iniciar sesión' }}</GButton>

        <div class="auth-view__divider"><span>o continúa con</span></div>

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

        <RouterLink class="auth-view__link" to="/register">¿No tienes cuenta? Regístrate</RouterLink>
      </form>
    </section>
  </div>
</template>

<style scoped>
.auth-view {
  display: flex;
  min-height: 100vh;
  background: var(--bg);
}

.auth-view__hero {
  flex: 1 1 55%;
  display: flex;
  align-items: flex-end;
  background-color: var(--bg);
  background-size: cover;
  background-position: center;
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
  max-width: 400px;
  padding: var(--space-6);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
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
}

.auth-view__subtitle {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
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

.auth-view__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-2);
  font-size: var(--text-xs);
}

.auth-view__checkbox {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--muted);
}

.auth-view__soon {
  color: var(--muted);
  cursor: not-allowed;
}

.auth-view__soon em,
.auth-view__social-btn em {
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
