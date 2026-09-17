<script setup lang="ts">
/**
 * Pantalla "Usuario" (ticket 093, cierre de "Mi Perfil" del lado del
 * frontend; ticket 095, rediseño para apegarse al mockup original) --
 * cabecera + Información personal + Cuentas conectadas + Seguridad +
 * Preferencias + Zona de peligro, cada sección conectada a su endpoint
 * real: auth-core-mc (`accountApi.ts`, tickets 060-064, 069, 070) y el
 * "perfil de producto" propio de galgoth-studio (`productProfileApi.ts`,
 * ticket 091). Sin datos de relleno -- cada dato que se muestra viene de
 * una llamada real.
 *
 * Ticket 095 -- 3 cambios de interacción sobre el 093, decididos por
 * Marco vía `AskUserQuestion` (nunca asumidos): (1) "Cambiar contraseña"
 * y "Sesiones activas" pasan de formularios/listas inline a modales
 * aparte (`ChangePasswordModal.vue`/`SessionsModal.vue`), layout de 2
 * columnas; (2) Nombre/Apellidos siguen siendo 2 campos separados (no se
 * fusionan, evita tocar el modelo de datos de auth-core-mc); (3) cada
 * proveedor conectado gana un menú "···" (`GMenu.vue`) con "Desvincular"
 * (backend nuevo de auth-core-mc#069).
 *
 * "Verificación en dos pasos" y "Tema oscuro" se muestran deshabilitados
 * con indicación explícita ("Próximamente"/"Sin tema claro todavía") --
 * 2FA ya existe en auth-core-mc pero esta pantalla no lo gestiona
 * (fuera de alcance, igual que el propio mockup lo marca); la app no
 * tiene tema claro implementado, así que ese toggle no tendría ningún
 * efecto real todavía.
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as accountApi from '../auth/accountApi'
import { requestEmailVerification } from '../auth/authApi'
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
import GMenu, { type GMenuItem } from '../design-system/components/GMenu.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconCalendar from '../design-system/icons/IconCalendar.vue'
import IconChevron from '../design-system/icons/IconChevron.vue'
import IconDevice from '../design-system/icons/IconDevice.vue'
import IconFacebook from '../design-system/icons/IconFacebook.vue'
import IconGoogle from '../design-system/icons/IconGoogle.vue'
import IconLink from '../design-system/icons/IconLink.vue'
import IconLock from '../design-system/icons/IconLock.vue'
import IconShield from '../design-system/icons/IconShield.vue'
import IconTrash from '../design-system/icons/IconTrash.vue'
import ChangeEmailModal from './ChangeEmailModal.vue'
import ChangePasswordModal from './ChangePasswordModal.vue'
import SessionsModal from './SessionsModal.vue'
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

/** Ver `IconGoogle.vue`/`IconFacebook.vue` -- son los únicos proveedores soportados hoy (`ExternalIdentityLinkService.isSupported`). */
const PROVIDERS: Record<string, { label: string; icon: unknown }> = {
  google: { label: 'Google', icon: IconGoogle },
  facebook: { label: 'Facebook', icon: IconFacebook },
}

function providerLabel(provider: string): string {
  return PROVIDERS[provider.toLowerCase()]?.label ?? provider
}

function providerIcon(provider: string): unknown {
  return PROVIDERS[provider.toLowerCase()]?.icon ?? null
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
const nombreInputEl = ref<HTMLInputElement>()

function syncPersonalForm(user: RegisteredUser): void {
  personalForm.value = { nombre: user.nombre, apellidos: user.apellidos, country: user.country ?? '', username: user.username ?? '' }
}

/** Botón "Editar perfil" de la cabecera -- la edición ya vive inline en "Información personal", esto solo lleva el foco ahí (sin duplicar el formulario en otro lado). */
function focusPersonalInfo(): void {
  // `scrollIntoView` no existe en jsdom (entorno de tests) -- nunca debe romper el foco real en el navegador.
  nombreInputEl.value?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  nextTick(() => nombreInputEl.value?.focus())
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
    await accountApi.requestEmailChange(newEmail)
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

// -- Contraseña (ticket 095: modal aparte en vez de formulario inline) ----
const showPasswordModal = ref(false)
const passwordBusy = ref(false)
const passwordError = ref<string | null>(null)
const passwordSaved = ref(false)

async function savePassword(payload: { currentPassword: string; newPassword: string }): Promise<void> {
  if (passwordBusy.value || !authProfile.value) {
    return
  }
  passwordBusy.value = true
  passwordError.value = null
  try {
    const updated = authProfile.value.hasPassword
      ? await accountApi.changePassword(payload.currentPassword, payload.newPassword)
      : await accountApi.setPassword(payload.newPassword)
    authProfile.value = updated
    showPasswordModal.value = false
    passwordSaved.value = true
  } catch (error) {
    passwordError.value = error instanceof ApiError ? error.message : 'No se pudo actualizar la contraseña.'
  } finally {
    passwordBusy.value = false
  }
}

// -- Cuentas conectadas ---------------------------------------------------
const linkingProvider = ref<string | null>(null)
const unlinkingProvider = ref<string | null>(null)
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

/** Ticket 095 / auth-core-mc#069 -- "Desvincular" del menú "···" de cada proveedor ya vinculado. */
async function unlinkProviderAction(provider: string): Promise<void> {
  if (unlinkingProvider.value) {
    return
  }
  unlinkingProvider.value = provider
  linkError.value = null
  try {
    await accountApi.unlinkProvider(provider)
    providers.value = providers.value.map((p) => (p.provider === provider ? { ...p, linked: false } : p))
  } catch (error) {
    linkError.value = error instanceof ApiError ? error.message : 'No se pudo desvincular la cuenta.'
  } finally {
    unlinkingProvider.value = null
  }
}

function providerMenuItems(): GMenuItem[] {
  return [{ key: 'unlink', label: 'Desvincular', icon: IconTrash, danger: true }]
}

function handleProviderMenuSelect(provider: string, key: string): void {
  if (key === 'unlink') {
    unlinkProviderAction(provider)
  }
}

// -- Sesiones activas (ticket 095: modal aparte en vez de sub-lista inline) --
const showSessionsModal = ref(false)
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
        <p v-if="passwordSaved" class="user-view__banner">Contraseña actualizada.</p>

        <GPanel elevated class="user-view__header">
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
            <p v-if="memberSince" class="user-view__meta user-view__meta--icon"><IconCalendar :size="14" />Miembro desde {{ memberSince }}</p>
          </div>
          <div class="user-view__header-actions">
            <GButton variant="secondary" @click="focusPersonalInfo">Editar perfil</GButton>
            <GButton variant="primary" :disabled="avatarBusy" @click="openAvatarPicker">{{ avatarBusy ? 'Subiendo…' : 'Cambiar foto' }}</GButton>
          </div>
        </GPanel>
        <p v-if="avatarError" class="user-view__error">{{ avatarError }}</p>

        <div class="user-view__columns">
          <div class="user-view__column">
            <!-- Información personal -->
            <GPanel class="user-view__section">
              <h2 class="user-view__section-title">Información personal</h2>
              <div class="user-view__grid">
                <label class="user-view__field">
                  Nombre
                  <input ref="nombreInputEl" v-model="personalForm.nombre" type="text" class="user-view__input" aria-label="Nombre" />
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
              <h2 class="user-view__section-title"><IconLink :size="16" />Cuentas conectadas</h2>
              <p v-if="linkError" class="user-view__error">{{ linkError }}</p>
              <ul class="user-view__list">
                <li v-for="provider in providers" :key="provider.provider" class="user-view__row">
                  <component :is="providerIcon(provider.provider)" :size="20" class="user-view__row-icon" />
                  <span class="user-view__row-label">{{ providerLabel(provider.provider) }}</span>
                  <template v-if="provider.linked">
                    <span v-if="unlinkingProvider === provider.provider" class="user-view__pending">Desvinculando…</span>
                    <div v-else class="user-view__row-actions">
                      <span class="user-view__pill user-view__pill--linked">Vinculada</span>
                      <GMenu :items="providerMenuItems()" :label="`Acciones de ${providerLabel(provider.provider)}`" @select="(key) => handleProviderMenuSelect(provider.provider, key)" />
                    </div>
                  </template>
                  <GButton v-else variant="secondary" :disabled="linkingProvider !== null" @click="connectProvider(provider.provider.toLowerCase() as 'google' | 'facebook')">
                    {{ linkingProvider === provider.provider.toLowerCase() ? 'Redirigiendo…' : 'Conectar' }}
                  </GButton>
                </li>
              </ul>
            </GPanel>
          </div>

          <div class="user-view__column">
            <!-- Seguridad -->
            <GPanel class="user-view__section">
              <h2 class="user-view__section-title">Seguridad</h2>

              <button type="button" class="user-view__action-row" @click="showPasswordModal = true">
                <IconLock :size="18" class="user-view__row-icon" />
                <span class="user-view__action-row-text">
                  <span>{{ authProfile.hasPassword ? 'Cambiar contraseña' : 'Establecer contraseña' }}</span>
                  <span class="user-view__action-row-subtitle">Actualiza tu contraseña de acceso</span>
                </span>
                <span class="user-view__action-row-cta">Cambiar →</span>
              </button>

              <div class="user-view__row user-view__row--disabled user-view__row--padded">
                <IconShield :size="18" class="user-view__row-icon" />
                <span class="user-view__row-label">Verificación en dos pasos</span>
                <span class="user-view__pill">Próximamente</span>
              </div>

              <button type="button" class="user-view__action-row" @click="showSessionsModal = true">
                <IconDevice :size="18" class="user-view__row-icon" />
                <span class="user-view__action-row-text">
                  <span>Sesiones activas</span>
                  <span class="user-view__action-row-subtitle">{{ sessions.length }} {{ sessions.length === 1 ? 'sesión' : 'sesiones' }}</span>
                </span>
                <span class="user-view__action-row-cta">Ver sesiones →</span>
              </button>
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
          </div>
        </div>

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
      <ChangePasswordModal
        v-if="showPasswordModal"
        :has-password="authProfile?.hasPassword ?? false"
        :busy="passwordBusy"
        :error="passwordError"
        @confirm="savePassword"
        @cancel="showPasswordModal = false"
      />
    </Transition>

    <Transition name="app-dialog">
      <SessionsModal
        v-if="showSessionsModal"
        :sessions="sessions"
        :revoking-session-id="revokingSessionId"
        :error="sessionsError"
        @revoke="revokeSession"
        @cancel="showSessionsModal = false"
      />
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
  flex-wrap: wrap;
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

.user-view__header-info {
  flex: 1;
  min-width: 200px;
}

.user-view__header-name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user-view__header-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

.user-view__title {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 800;
}

/*
 * Regla única del badge. Antes estaba declarada dos veces (acá y más
 * abajo, junto a los modificadores): la segunda ganaba por orden y bajaba
 * el `font-weight` de 700 a 600 sin que se notara. Fusionadas conservando
 * lo que realmente se renderizaba (600), no lo que la primera declaraba.
 *
 * Mismos tokens de tono que GStatusPill (accent/warning + su -soft), sin
 * reutilizar el componente en sí -- ese es específico de estado de mob
 * (Listo/En progreso/Draft) y esto es verificación de cuenta. Los
 * modificadores --verified/--unverified se declaran después y pisan
 * `color`/`background` cuando corresponde.
 */
.user-view__badge {
  flex-shrink: 0;
  padding: 2px var(--space-2);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: var(--text-xs);
  font-weight: 600;
}

.user-view__meta {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: var(--text-sm);
}

.user-view__meta--icon {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-view__columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
  align-items: start;
}

.user-view__column {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

@media (max-width: 800px) {
  .user-view__columns {
    grid-template-columns: 1fr;
  }
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
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-md);
  font-weight: 700;
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

.user-view__row-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user-view__row-icon {
  flex-shrink: 0;
  color: var(--muted);
}

.user-view__row-label {
  flex: 1;
  min-width: 0;
}

.user-view__row--disabled {
  color: var(--muted);
}

/* Ticket 095 (hallazgo real reportado en vivo): alinea el ícono/texto de la fila deshabilitada de 2FA con el mismo padding horizontal que ganaron las filas-botón de abajo, para que las 3 filas de Seguridad queden a la misma altura. */
.user-view__row--padded {
  padding: var(--space-2) var(--space-3);
  margin: 0 calc(-1 * var(--space-3));
}

/* Ticket 095 -- filas de Seguridad que abren un modal (mockup: "Cambiar contraseña"/"Sesiones activas"), mismo tratamiento visual que `.user-view__row` pero como <button> completo (área de clic más grande) con subtítulo + CTA a la derecha.
   Hallazgo real reportado en vivo: sin padding horizontal, el ícono y el CTA quedaban pegados al borde del fondo que aparece en :hover -- se veía "recortado". `var(--space-3)` a los lados es el mismo valor que usan filas equivalentes del design system (`.g-menu__item`/`.sessions-modal__row`). */
.user-view__action-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: var(--hit-target-min);
  padding: var(--space-2) var(--space-3);
  margin: 0 calc(-1 * var(--space-3));
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}

.user-view__action-row:hover {
  background: var(--surface-2);
}

.user-view__action-row-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-view__action-row-subtitle {
  color: var(--muted);
  font-size: var(--text-xs);
}

.user-view__action-row-cta {
  flex-shrink: 0;
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 600;
}

.user-view__pending {
  color: var(--muted);
  font-size: var(--text-xs);
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
