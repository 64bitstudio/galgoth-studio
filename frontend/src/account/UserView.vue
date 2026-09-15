<script setup lang="ts">
/**
 * Pantalla "Usuario" (ticket 093, cierre de "Mi Perfil" del lado del
 * frontend) -- cabecera + Información personal + Cuentas conectadas +
 * Seguridad + Preferencias + Zona de peligro, cada sección conectada a
 * su endpoint real: auth-core-mc (`accountApi.ts`, tickets 060-064) y
 * el "perfil de producto" propio de galgoth-studio (`productProfileApi.ts`,
 * ticket 091). Sin datos de relleno -- cada dato que se muestra viene de
 * una llamada real.
 *
 * "Verificación en dos pasos" y "Tema oscuro" se muestran deshabilitados
 * con indicación explícita ("Próximamente"/"Sin tema claro todavía") --
 * 2FA ya existe en auth-core-mc pero esta pantalla no lo gestiona
 * (fuera de alcance, igual que el propio mockup lo marca); la app no
 * tiene tema claro implementado, así que ese toggle no tendría ningún
 * efecto real todavía.
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as accountApi from '../auth/accountApi'
import { requestEmailChange, requestEmailVerification } from '../auth/authApi'
import * as productProfileApi from '../account/productProfileApi'
import type { RegisteredUser } from '../auth/authApi'
import type { SessionSummary, ConnectedProviderSummary } from '../auth/accountApi'
import type { ProductProfile } from '../account/productProfileApi'
import { useSessionStore } from '../auth/sessionStore'
import { ApiError } from '../api/ApiError'
import { avatarUrl } from '../api/apiConfig'
import ConfirmDialog from '../design-system/components/ConfirmDialog.vue'
import GButton from '../design-system/components/GButton.vue'
import GPanel from '../design-system/components/GPanel.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconChevron from '../design-system/icons/IconChevron.vue'
import ChangeEmailModal from './ChangeEmailModal.vue'
import DeleteAccountDialog from './DeleteAccountDialog.vue'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const authProfile = ref<RegisteredUser | null>(null)
const productProfile = ref<ProductProfile | null>(null)
const sessions = ref<SessionSummary[]>([])
const providers = ref<ConnectedProviderSummary[]>([])
const loadError = ref<string | null>(null)
const linkBanner = ref<string | null>(null)

const memberSince = computed(() => {
  if (!authProfile.value) {
    return ''
  }
  const parsed = new Date(authProfile.value.createdAt)
  if (Number.isNaN(parsed.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat('es-MX', { month: 'long', year: 'numeric' }).format(parsed)
})

/** Fallback sin avatar subido -- mismo criterio que `ExploreProjectCard.vue` (ticket 092): inicial del nombre, nunca una imagen rota. */
const avatarInitial = computed(() => (authProfile.value?.nombre ?? '?').trim().charAt(0).toUpperCase())

async function load(): Promise<void> {
  try {
    const [auth, product, sessionList, providerList] = await Promise.all([
      accountApi.getProfile(),
      productProfileApi.getProductProfile(),
      accountApi.listSessions(),
      accountApi.listConnectedProviders(),
    ])
    authProfile.value = auth
    productProfile.value = product
    sessions.value = sessionList
    providers.value = providerList
    loadError.value = null
    syncPersonalForm(auth)
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : 'No se pudo cargar tu perfil.'
  }
}

/** Ticket 063 de auth-core-mc -- al volver de Google/Facebook, aterriza acá con `?linked=`/`?link_error=` en la query. */
function readLinkResultFromQuery(): void {
  const linked = route.query.linked
  const linkErr = route.query.link_error
  if (typeof linked === 'string') {
    linkBanner.value = `Cuenta de ${providerLabel(linked)} vinculada correctamente.`
  } else if (typeof linkErr === 'string') {
    linkBanner.value = linkErrorMessage(linkErr)
  }
  if (linked !== undefined || linkErr !== undefined) {
    router.replace({ path: '/usuario' }) // limpia la query -- un refresh no debe repetir el banner.
  }
}

function providerLabel(provider: string): string {
  return provider.toLowerCase() === 'google' ? 'Google' : provider.toLowerCase() === 'facebook' ? 'Facebook' : provider
}

function linkErrorMessage(code: string): string {
  if (code === 'already_linked') {
    return 'Esa cuenta social ya está vinculada a otro usuario.'
  }
  if (code === 'no_email') {
    return 'No pudimos obtener un correo de esa cuenta social.'
  }
  return 'No se pudo vincular la cuenta. Intenta de nuevo.'
}

onMounted(() => {
  readLinkResultFromQuery()
  load()
})

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home') {
    router.push('/')
  } else if (key === 'projects') {
    router.push('/projects')
  } else if (key === 'explore') {
    router.push('/explore')
  } else if (key === 'user') {
    router.push('/usuario')
  }
}

// -- Avatar -----------------------------------------------------------
const avatarInput = ref<HTMLInputElement>()
const avatarBusy = ref(false)
const avatarError = ref<string | null>(null)

function openAvatarPicker(): void {
  avatarInput.value?.click()
}

async function handleAvatarSelected(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || avatarBusy.value) {
    return
  }
  avatarBusy.value = true
  avatarError.value = null
  try {
    const newAvatarUrl = await productProfileApi.uploadAvatar(file)
    if (productProfile.value) {
      productProfile.value = { ...productProfile.value, avatarUrl: newAvatarUrl }
    }
  } catch (error) {
    avatarError.value = error instanceof ApiError ? error.message : 'No se pudo subir la foto.'
  } finally {
    avatarBusy.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

// -- Información personal ----------------------------------------------
const personalForm = ref({ nombre: '', apellidos: '', country: '', username: '' })
const personalBusy = ref(false)
const personalError = ref<string | null>(null)
const personalSaved = ref(false)

function syncPersonalForm(user: RegisteredUser): void {
  personalForm.value = { nombre: user.nombre, apellidos: user.apellidos, country: user.country ?? '', username: user.username ?? '' }
}

async function savePersonalInfo(): Promise<void> {
  if (personalBusy.value) {
    return
  }
  personalBusy.value = true
  personalError.value = null
  personalSaved.value = false
  try {
    const updated = await accountApi.updateProfile(
      personalForm.value.nombre.trim(),
      personalForm.value.apellidos.trim(),
      personalForm.value.country.trim() || null,
      personalForm.value.username.trim() || null,
    )
    authProfile.value = updated
    session.user = updated
    personalSaved.value = true
  } catch (error) {
    personalError.value = error instanceof ApiError ? error.message : 'No se pudo guardar la información.'
  } finally {
    personalBusy.value = false
  }
}

// -- Cambiar correo -----------------------------------------------------
const showChangeEmail = ref(false)
const changeEmailBusy = ref(false)
const changeEmailError = ref<string | null>(null)
const changeEmailSent = ref<string | null>(null)

async function confirmChangeEmail(newEmail: string): Promise<void> {
  if (!authProfile.value || changeEmailBusy.value) {
    return
  }
  changeEmailBusy.value = true
  changeEmailError.value = null
  try {
    await requestEmailChange(authProfile.value.id, newEmail)
    showChangeEmail.value = false
    changeEmailSent.value = newEmail
  } catch (error) {
    changeEmailError.value = error instanceof ApiError ? error.message : 'No se pudo enviar el link de confirmación.'
  } finally {
    changeEmailBusy.value = false
  }
}

// -- Reenviar verificación de correo (ticket 079) --------------------------
// A diferencia del resto de esta pantalla, `emailVerified` viene ya
// poblado en `authProfile` (RegisteredUser) -- no hace falta un fetch
// aparte, solo mostrarlo y ofrecer reenviar si sigue en false.
const resendVerificationBusy = ref(false)
const resendVerificationSent = ref(false)
const resendVerificationError = ref<string | null>(null)

async function resendVerificationEmail(): Promise<void> {
  if (!authProfile.value || resendVerificationBusy.value) {
    return
  }
  resendVerificationBusy.value = true
  resendVerificationError.value = null
  try {
    await requestEmailVerification(authProfile.value.id)
    resendVerificationSent.value = true
  } catch (error) {
    resendVerificationError.value = error instanceof ApiError ? error.message : 'No se pudo reenviar el correo de verificación.'
  } finally {
    resendVerificationBusy.value = false
  }
}

// -- Contraseña -----------------------------------------------------------
const currentPassword = ref('')
const newPassword = ref('')
const passwordBusy = ref(false)
const passwordError = ref<string | null>(null)
const passwordSaved = ref(false)

async function savePassword(): Promise<void> {
  if (passwordBusy.value || !authProfile.value) {
    return
  }
  passwordBusy.value = true
  passwordError.value = null
  passwordSaved.value = false
  try {
    const updated = authProfile.value.hasPassword
      ? await accountApi.changePassword(currentPassword.value, newPassword.value)
      : await accountApi.setPassword(newPassword.value)
    authProfile.value = updated
    currentPassword.value = ''
    newPassword.value = ''
    passwordSaved.value = true
  } catch (error) {
    passwordError.value = error instanceof ApiError ? error.message : 'No se pudo actualizar la contraseña.'
  } finally {
    passwordBusy.value = false
  }
}

// -- Cuentas conectadas ---------------------------------------------------
const linkingProvider = ref<string | null>(null)
const linkError = ref<string | null>(null)

async function connectProvider(provider: 'google' | 'facebook'): Promise<void> {
  if (linkingProvider.value) {
    return
  }
  linkingProvider.value = provider
  linkError.value = null
  try {
    const redirectUrl = await accountApi.linkProvider(provider)
    window.location.href = redirectUrl // navegación real del navegador -- nunca XHR (ticket 063).
  } catch (error) {
    linkError.value = error instanceof ApiError ? error.message : 'No se pudo iniciar la vinculación.'
    linkingProvider.value = null
  }
}

// -- Sesiones activas ---------------------------------------------------
const revokingSessionId = ref<string | null>(null)
const sessionsError = ref<string | null>(null)

async function revokeSession(sessionId: string): Promise<void> {
  if (revokingSessionId.value) {
    return
  }
  revokingSessionId.value = sessionId
  sessionsError.value = null
  try {
    await accountApi.revokeSession(sessionId)
    sessions.value = sessions.value.filter((s) => s.id !== sessionId)
  } catch (error) {
    sessionsError.value = error instanceof ApiError ? error.message : 'No se pudo cerrar esa sesión.'
  } finally {
    revokingSessionId.value = null
  }
}

// -- Preferencias -----------------------------------------------------
const preferencesBusy = ref(false)
const preferencesError = ref<string | null>(null)

async function togglePreference(key: 'notifyEmail' | 'notifyProductNews' | 'notifySaveReminders'): Promise<void> {
  if (!productProfile.value || preferencesBusy.value) {
    return
  }
  const next = {
    notifyEmail: productProfile.value.notifyEmail,
    notifyProductNews: productProfile.value.notifyProductNews,
    notifySaveReminders: productProfile.value.notifySaveReminders,
    [key]: !productProfile.value[key],
  }
  preferencesBusy.value = true
  preferencesError.value = null
  try {
    productProfile.value = await productProfileApi.updatePreferences(next.notifyEmail, next.notifyProductNews, next.notifySaveReminders)
  } catch (error) {
    preferencesError.value = error instanceof ApiError ? error.message : 'No se pudo guardar la preferencia.'
  } finally {
    preferencesBusy.value = false
  }
}

// -- Zona de peligro ---------------------------------------------------
const pendingLogoutEverywhere = ref(false)
const logoutEverywhereBusy = ref(false)
const logoutEverywhereError = ref<string | null>(null)

async function confirmLogoutEverywhere(): Promise<void> {
  if (logoutEverywhereBusy.value) {
    return
  }
  logoutEverywhereBusy.value = true
  logoutEverywhereError.value = null
  try {
    await accountApi.revokeAllSessions()
    session.logout()
    await router.push('/login')
  } catch (error) {
    logoutEverywhereError.value = error instanceof ApiError ? error.message : 'No se pudo cerrar sesión en todos los dispositivos.'
  } finally {
    logoutEverywhereBusy.value = false
    pendingLogoutEverywhere.value = false
  }
}

const pendingDeleteAccount = ref(false)
const deleteAccountBusy = ref(false)
const deleteAccountError = ref<string | null>(null)

const ownIdentifier = computed(() => authProfile.value?.email ?? authProfile.value?.phone ?? '')

async function confirmDeleteAccount(): Promise<void> {
  if (deleteAccountBusy.value) {
    return
  }
  deleteAccountBusy.value = true
  deleteAccountError.value = null
  try {
    await accountApi.deleteAccount(ownIdentifier.value)
    session.logout()
    await router.push('/login')
  } catch (error) {
    deleteAccountError.value = error instanceof ApiError ? error.message : 'No se pudo eliminar la cuenta.'
  } finally {
    deleteAccountBusy.value = false
  }
}
</script>

<template>
  <div class="user-view-shell">
    <GSidebar active="user" @select="handleSidebarSelect" />
    <main class="user-view app-scroll">
      <nav class="user-view__breadcrumb" aria-label="Ruta de navegación">
        <router-link to="/">Galgoth Studio</router-link>
        <IconChevron :size="12" />
        <span>Usuario</span>
      </nav>

      <p v-if="loadError" class="user-view__error">{{ loadError }}</p>

      <template v-else-if="authProfile">
        <p v-if="linkBanner" class="user-view__banner">{{ linkBanner }}</p>
        <p v-if="changeEmailSent" class="user-view__banner">Te mandamos un link de confirmación a {{ changeEmailSent }}. Revisa esa bandeja para completar el cambio.</p>

        <div class="user-view__header">
          <button type="button" class="user-view__avatar" aria-label="Cambiar foto de perfil" :disabled="avatarBusy" @click="openAvatarPicker">
            <img v-if="avatarUrl(productProfile?.avatarUrl ?? null)" :src="avatarUrl(productProfile?.avatarUrl ?? null)!" alt="" />
            <span v-else class="user-view__avatar-initial" aria-hidden="true">{{ avatarInitial }}</span>
          </button>
          <input ref="avatarInput" type="file" accept="image/png,image/jpeg" class="user-view__avatar-input" aria-label="Subir foto de perfil" @change="handleAvatarSelected" />
          <div class="user-view__header-info">
            <div class="user-view__header-name">
              <h1 class="user-view__title">{{ authProfile.nombre }} {{ authProfile.apellidos }}</h1>
              <span class="user-view__badge">Creador</span>
            </div>
            <p class="user-view__meta">{{ authProfile.email ?? authProfile.phone }}</p>
            <p v-if="memberSince" class="user-view__meta">Miembro desde {{ memberSince }}</p>
          </div>
        </div>
        <p v-if="avatarError" class="user-view__error">{{ avatarError }}</p>

        <!-- Información personal -->
        <GPanel class="user-view__section">
          <h2 class="user-view__section-title">Información personal</h2>
          <div class="user-view__grid">
            <label class="user-view__field">
              Nombre
              <input v-model="personalForm.nombre" type="text" class="user-view__input" aria-label="Nombre" />
            </label>
            <label class="user-view__field">
              Apellidos
              <input v-model="personalForm.apellidos" type="text" class="user-view__input" aria-label="Apellidos" />
            </label>
            <label class="user-view__field">
              País / Región
              <input v-model="personalForm.country" type="text" class="user-view__input" aria-label="País o región" />
            </label>
            <label class="user-view__field">
              Nombre de usuario
              <input v-model="personalForm.username" type="text" class="user-view__input" aria-label="Nombre de usuario" />
            </label>
          </div>
          <div class="user-view__field user-view__field--email">
            <span class="user-view__field-label">Correo</span>
            <div class="user-view__email-row">
              <span class="user-view__email-value">
                <span>{{ authProfile.email ?? 'Sin correo' }}</span>
                <span
                  v-if="authProfile.email"
                  class="user-view__badge"
                  :class="authProfile.emailVerified ? 'user-view__badge--verified' : 'user-view__badge--unverified'"
                >
                  {{ authProfile.emailVerified ? 'Verificado' : 'Sin verificar' }}
                </span>
              </span>
              <GButton variant="ghost" @click="showChangeEmail = true">Cambiar</GButton>
            </div>
            <!-- Ticket 079: solo tiene sentido reenviar si hay un correo real y todavía no está verificado. -->
            <div v-if="authProfile.email && !authProfile.emailVerified" class="user-view__verify-row">
              <p v-if="resendVerificationSent" class="user-view__success">Te enviamos un correo nuevo de verificación.</p>
              <template v-else>
                <GButton variant="ghost" :disabled="resendVerificationBusy" @click="resendVerificationEmail">
                  {{ resendVerificationBusy ? 'Enviando…' : 'Reenviar correo de verificación' }}
                </GButton>
                <p v-if="resendVerificationError" class="user-view__error">{{ resendVerificationError }}</p>
              </template>
            </div>
          </div>
          <p v-if="personalError" class="user-view__error">{{ personalError }}</p>
          <p v-if="personalSaved" class="user-view__success">Información guardada.</p>
          <GButton variant="primary" :disabled="personalBusy" @click="savePersonalInfo">{{ personalBusy ? 'Guardando…' : 'Guardar cambios' }}</GButton>
        </GPanel>

        <!-- Cuentas conectadas -->
        <GPanel class="user-view__section">
          <h2 class="user-view__section-title">Cuentas conectadas</h2>
          <p v-if="linkError" class="user-view__error">{{ linkError }}</p>
          <ul class="user-view__list">
            <li v-for="provider in providers" :key="provider.provider" class="user-view__row">
              <span>{{ providerLabel(provider.provider) }}</span>
              <span v-if="provider.linked" class="user-view__pill user-view__pill--linked">Conectada</span>
              <GButton v-else variant="secondary" :disabled="linkingProvider !== null" @click="connectProvider(provider.provider.toLowerCase() as 'google' | 'facebook')">
                {{ linkingProvider === provider.provider.toLowerCase() ? 'Redirigiendo…' : 'Conectar' }}
              </GButton>
            </li>
          </ul>
        </GPanel>

        <!-- Seguridad -->
        <GPanel class="user-view__section">
          <h2 class="user-view__section-title">Seguridad</h2>

          <h3 class="user-view__subsection-title">{{ authProfile.hasPassword ? 'Cambiar contraseña' : 'Establecer contraseña' }}</h3>
          <div class="user-view__grid">
            <label v-if="authProfile.hasPassword" class="user-view__field">
              Contraseña actual
              <input v-model="currentPassword" type="password" class="user-view__input" autocomplete="current-password" aria-label="Contraseña actual" />
            </label>
            <label class="user-view__field">
              Nueva contraseña
              <input v-model="newPassword" type="password" class="user-view__input" autocomplete="new-password" minlength="8" aria-label="Nueva contraseña" />
            </label>
          </div>
          <p v-if="passwordError" class="user-view__error">{{ passwordError }}</p>
          <p v-if="passwordSaved" class="user-view__success">Contraseña actualizada.</p>
          <GButton variant="secondary" :disabled="passwordBusy" @click="savePassword">{{ passwordBusy ? 'Guardando…' : 'Actualizar contraseña' }}</GButton>

          <div class="user-view__row user-view__row--disabled">
            <span>Verificación en dos pasos</span>
            <span class="user-view__pill">Próximamente</span>
          </div>

          <h3 class="user-view__subsection-title">Sesiones activas</h3>
          <p v-if="sessionsError" class="user-view__error">{{ sessionsError }}</p>
          <ul class="user-view__list">
            <li v-for="s in sessions" :key="s.id" class="user-view__row">
              <span>{{ s.browser }} · {{ s.os }}<span v-if="s.current" class="user-view__pill user-view__pill--linked">Actual</span></span>
              <GButton v-if="!s.current" variant="ghost" :disabled="revokingSessionId === s.id" @click="revokeSession(s.id)">
                {{ revokingSessionId === s.id ? 'Cerrando…' : 'Cerrar sesión' }}
              </GButton>
            </li>
          </ul>
        </GPanel>

        <!-- Preferencias -->
        <GPanel class="user-view__section">
          <h2 class="user-view__section-title">Preferencias</h2>
          <p v-if="preferencesError" class="user-view__error">{{ preferencesError }}</p>
          <div v-if="productProfile" class="user-view__row">
            <span>Notificaciones por correo</span>
            <button
              type="button"
              aria-label="Notificaciones por correo"
              class="user-view__switch"
              :class="{ 'user-view__switch--on': productProfile.notifyEmail }"
              role="switch"
              :aria-checked="productProfile.notifyEmail"
              :disabled="preferencesBusy"
              @click="togglePreference('notifyEmail')"
            ></button>
          </div>
          <div v-if="productProfile" class="user-view__row">
            <span>Novedades del producto</span>
            <button
              type="button"
              aria-label="Novedades del producto"
              class="user-view__switch"
              :class="{ 'user-view__switch--on': productProfile.notifyProductNews }"
              role="switch"
              :aria-checked="productProfile.notifyProductNews"
              :disabled="preferencesBusy"
              @click="togglePreference('notifyProductNews')"
            ></button>
          </div>
          <div v-if="productProfile" class="user-view__row">
            <span>Recordatorios de guardado</span>
            <button
              type="button"
              aria-label="Recordatorios de guardado"
              class="user-view__switch"
              :class="{ 'user-view__switch--on': productProfile.notifySaveReminders }"
              role="switch"
              :aria-checked="productProfile.notifySaveReminders"
              :disabled="preferencesBusy"
              @click="togglePreference('notifySaveReminders')"
            ></button>
          </div>
          <div class="user-view__row user-view__row--disabled">
            <span>Tema oscuro</span>
            <span class="user-view__pill">Sin tema claro todavía</span>
          </div>
        </GPanel>

        <!-- Zona de peligro -->
        <GPanel class="user-view__section user-view__section--danger">
          <h2 class="user-view__section-title">Zona de peligro</h2>
          <div class="user-view__row">
            <span>Cerrar sesión en todos los dispositivos</span>
            <GButton variant="danger" @click="pendingLogoutEverywhere = true">Cerrar todas</GButton>
          </div>
          <div class="user-view__row">
            <span>Eliminar cuenta</span>
            <GButton variant="danger" @click="pendingDeleteAccount = true">Eliminar cuenta</GButton>
          </div>
        </GPanel>
      </template>
    </main>

    <Transition name="app-dialog">
      <ChangeEmailModal v-if="showChangeEmail" :busy="changeEmailBusy" :error="changeEmailError" @confirm="confirmChangeEmail" @cancel="showChangeEmail = false" />
    </Transition>

    <Transition name="app-dialog">
      <ConfirmDialog
        v-if="pendingLogoutEverywhere"
        title="Cerrar sesión en todos los dispositivos"
        message="Se cerrará tu sesión en este y en todos los demás dispositivos. Tendrás que volver a iniciar sesión."
        confirm-label="Cerrar todas"
        danger
        :busy="logoutEverywhereBusy"
        :error="logoutEverywhereError"
        @confirm="confirmLogoutEverywhere"
        @cancel="pendingLogoutEverywhere = false"
      />
    </Transition>

    <Transition name="app-dialog">
      <DeleteAccountDialog
        v-if="pendingDeleteAccount"
        :identifier="ownIdentifier"
        :busy="deleteAccountBusy"
        :error="deleteAccountError"
        @confirm="confirmDeleteAccount"
        @cancel="pendingDeleteAccount = false"
      />
    </Transition>
  </div>
</template>

<style scoped>
.user-view-shell {
  display: flex;
  height: 100vh;
}

.user-view {
  flex: 1;
  padding: var(--space-6) var(--space-8);
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 720px;
}

.user-view__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--muted);
}

.user-view__breadcrumb a {
  color: var(--muted);
  text-decoration: none;
}

.user-view__breadcrumb a:hover {
  color: var(--text);
}

.user-view__breadcrumb span {
  color: var(--text);
  font-weight: 600;
}

.user-view__banner {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  background: var(--accent-soft);
  color: var(--accent);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
}

.user-view__header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.user-view__avatar {
  position: relative;
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--accent-soft);
  border: none;
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: var(--text-xl);
  font-weight: 700;
}

.user-view__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-view__avatar-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.user-view__header-name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user-view__title {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 800;
}

.user-view__badge {
  padding: 2px var(--space-2);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: var(--text-xs);
  font-weight: 700;
}

.user-view__meta {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: var(--text-sm);
}

.user-view__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.user-view__section--danger {
  border-color: var(--danger);
}

.user-view__section-title {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 700;
}

.user-view__subsection-title {
  margin: var(--space-2) 0 0;
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--muted);
}

.user-view__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-3);
}

.user-view__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.user-view__field-label {
  font-size: var(--text-sm);
  color: var(--muted);
}

.user-view__field--email {
  margin-top: var(--space-2);
}

.user-view__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.user-view__email-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  background: var(--surface);
  border-radius: var(--radius-md);
  color: var(--text);
}

.user-view__email-value {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

/* Mismos tokens de tono que GStatusPill (accent/warning + su -soft), sin reutilizar el componente en sí -- ese es específico de estado de mob (Listo/En progreso/Draft), esto es verificación de cuenta. */
.user-view__badge {
  flex-shrink: 0;
  padding: 2px var(--space-2);
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 600;
}

.user-view__badge--verified {
  color: var(--accent);
  background: var(--accent-soft);
}

.user-view__badge--unverified {
  color: var(--warning);
  background: var(--warning-soft);
}

.user-view__verify-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.user-view__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.user-view__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) 0;
  color: var(--text);
  font-size: var(--text-sm);
}

.user-view__row--disabled {
  color: var(--muted);
}

.user-view__pill {
  margin-left: var(--space-2);
  padding: 2px var(--space-2);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 600;
}

.user-view__pill--linked {
  background: var(--accent-soft);
  color: var(--accent);
}

.user-view__switch {
  position: relative;
  width: 40px;
  height: 22px;
  border-radius: 999px;
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.user-view__switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--text);
  transition: transform var(--transition-fast);
}

.user-view__switch--on {
  background: var(--accent);
  border-color: var(--accent);
}

.user-view__switch--on::after {
  transform: translateX(18px);
  background: var(--accent-ink);
}

.user-view__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.user-view__success {
  margin: 0;
  color: var(--accent);
  font-size: var(--text-sm);
}
</style>
