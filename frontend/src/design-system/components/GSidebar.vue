<script setup lang="ts">
/**
 * Sidebar global compacta (Visual Contract punto 6 / master prompt §3):
 * Nuevo proyecto, Mis proyectos, Recientes, Configuración. Sin ítem
 * "Volver" -- a propósito, no es un descuido.
 *
 * Presentacional: quien la usa controla la navegación real (router)
 * pasando `active` y escuchando `select`.
 */
import IconNewProject from '../icons/IconNewProject.vue'
import IconProjects from '../icons/IconProjects.vue'
import IconRecent from '../icons/IconRecent.vue'
import IconSettings from '../icons/IconSettings.vue'

export type GSidebarKey = 'new-project' | 'projects' | 'recent' | 'settings'

const items: Array<{ key: GSidebarKey; label: string; icon: unknown }> = [
  { key: 'new-project', label: 'Nuevo proyecto', icon: IconNewProject },
  { key: 'projects', label: 'Mis proyectos', icon: IconProjects },
  { key: 'recent', label: 'Recientes', icon: IconRecent },
]

const bottomItem = {
  key: 'settings' as const,
  label: 'Configuración',
  icon: IconSettings,
}

defineProps<{ active: GSidebarKey }>()
const emit = defineEmits<{ select: [GSidebarKey] }>()
</script>

<template>
  <nav class="g-sidebar" aria-label="Navegación principal">
    <div class="g-sidebar__brand">Galgoth Studio</div>

    <ul class="g-sidebar__list">
      <li v-for="item in items" :key="item.key">
        <button
          type="button"
          class="g-sidebar__item"
          :class="{ 'g-sidebar__item--active': active === item.key }"
          :aria-current="active === item.key ? 'page' : undefined"
          @click="emit('select', item.key)"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </button>
      </li>
    </ul>

    <div class="g-sidebar__spacer" />

    <ul class="g-sidebar__list">
      <li>
        <button
          type="button"
          class="g-sidebar__item"
          :class="{ 'g-sidebar__item--active': active === bottomItem.key }"
          :aria-current="active === bottomItem.key ? 'page' : undefined"
          @click="emit('select', bottomItem.key)"
        >
          <component :is="bottomItem.icon" :size="18" />
          <span>{{ bottomItem.label }}</span>
        </button>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.g-sidebar {
  display: flex;
  flex-direction: column;
  width: 220px;
  height: 100%;
  background: var(--panel);
  border-right: var(--border-width) solid var(--border);
  padding: var(--space-4) var(--space-2);
}

.g-sidebar__brand {
  padding: 0 var(--space-2);
  margin-bottom: var(--space-5);
  font-weight: 700;
  font-size: var(--text-md);
  letter-spacing: 0.01em;
}

.g-sidebar__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.g-sidebar__spacer {
  flex: 1;
}

.g-sidebar__item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  border: none;
  border-left: 2px solid transparent;
  background: none;
  color: var(--muted);
  font-size: var(--text-base);
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--transition-fast),
    color var(--transition-fast);
}

.g-sidebar__item:hover {
  background: var(--surface-2);
  color: var(--text);
}

/* Estado activo: color + borde izquierdo + peso de fuente -- nunca solo
   color (Visual Contract punto 10). */
.g-sidebar__item--active {
  background: var(--accent-soft);
  border-left-color: var(--accent);
  color: var(--accent);
  font-weight: 700;
}
</style>
