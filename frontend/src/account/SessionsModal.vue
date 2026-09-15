<script setup lang="ts">
/**
 * Ticket 095 -- extraído de la sub-lista inline "Sesiones activas" de
 * `UserView.vue` (093) a un modal aparte, para apegarse al mockup
 * original (decisión confirmada por Marco: "filas que abren un modal/
 * vista aparte"). Ciudad/país vienen de `SessionSummary.city`/`country`
 * (ticket 070 de auth-core-mc, GeoLite2) -- pueden venir `null`
 * independientemente uno del otro, y ambos ausentes es el caso normal
 * mientras el sidecar `geoipupdate` no tenga credenciales de MaxMind
 * configuradas (degradación explícita, no un error).
 *
 * "Cerrar sesión" vive en un `GMenu.vue` por fila (mismo componente que
 * el mockup usa para el "···" de cada sesión) en vez del botón inline
 * que tenía el 093 -- la sesión actual muestra el ítem deshabilitado con
 * su razón visible, nunca lo oculta del todo (mismo criterio de
 * accesibilidad ya establecido en GMenu/GTabs).
 */
import GButton from '../design-system/components/GButton.vue'
import AppDialog from '../design-system/components/AppDialog.vue'
import GMenu, { type GMenuItem } from '../design-system/components/GMenu.vue'
import IconTrash from '../design-system/icons/IconTrash.vue'
import type { SessionSummary } from '../auth/accountApi'

defineProps<{
  sessions: SessionSummary[]
  revokingSessionId: string | null
  error?: string | null
}>()

const emit = defineEmits<{ revoke: [string]; cancel: [] }>()

/** Por debajo de este umbral se muestra "Activa ahora" en vez de la fecha -- mismo criterio que "en línea" en apps de chat, sin dato de presencia real en el backend. */
const ACTIVE_NOW_THRESHOLD_MS = 5 * 60 * 1000

function lastUsedLabel(session: SessionSummary): string {
  const parsed = new Date(session.lastUsedAt)
  if (Number.isNaN(parsed.getTime())) {
    return ''
  }
  if (Date.now() - parsed.getTime() < ACTIVE_NOW_THRESHOLD_MS) {
    return 'Activa ahora'
  }
  return new Intl.DateTimeFormat('es-MX', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }).format(parsed)
}

function locationLabel(session: SessionSummary): string | null {
  if (session.city && session.country) {
    return `${session.city}, ${session.country}`
  }
  return session.city ?? session.country ?? null
}

function menuItems(session: SessionSummary): GMenuItem[] {
  return [
    {
      key: 'revoke',
      label: 'Cerrar sesión',
      icon: IconTrash,
      danger: true,
      disabled: session.current,
      disabledReason: session.current ? 'No puedes cerrar la sesión que estás usando ahora.' : undefined,
    },
  ]
}

function handleMenuSelect(sessionId: string, key: string): void {
  if (key === 'revoke') {
    emit('revoke', sessionId)
  }
}
</script>

<template>
  <AppDialog title="Sesiones activas" aria-label="Sesiones activas" @cancel="emit('cancel')">
    <p v-if="error" class="sessions-modal__error">{{ error }}</p>
    <ul class="sessions-modal__list">
      <li v-for="s in sessions" :key="s.id" class="sessions-modal__row">
        <div class="sessions-modal__info">
          <div class="sessions-modal__device">
            <span>{{ s.browser }} · {{ s.os }}</span>
            <span v-if="s.current" class="sessions-modal__pill sessions-modal__pill--current">Actual</span>
          </div>
          <p class="sessions-modal__meta">
            <span v-if="locationLabel(s)">{{ locationLabel(s) }} · </span>
            <span>{{ lastUsedLabel(s) }}</span>
          </p>
        </div>
        <span v-if="revokingSessionId === s.id" class="sessions-modal__pending">Cerrando…</span>
        <GMenu v-else :items="menuItems(s)" :label="`Acciones de la sesión en ${s.browser}`" @select="(key) => handleMenuSelect(s.id, key)" />
      </li>
    </ul>
    <template #actions>
      <GButton variant="ghost" @click="emit('cancel')">Cerrar</GButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.sessions-modal__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.sessions-modal__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.sessions-modal__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) 0;
  border-bottom: var(--border-width) solid var(--border);
}

.sessions-modal__row:last-child {
  border-bottom: none;
}

.sessions-modal__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.sessions-modal__device {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text);
  font-size: var(--text-sm);
}

.sessions-modal__meta {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-xs);
}

.sessions-modal__pill {
  padding: 2px var(--space-2);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 600;
}

.sessions-modal__pill--current {
  background: var(--accent-soft);
  color: var(--accent);
}

.sessions-modal__pending {
  color: var(--muted);
  font-size: var(--text-xs);
  flex-shrink: 0;
}
</style>
