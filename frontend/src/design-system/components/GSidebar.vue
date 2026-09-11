<script setup lang="ts">
/**
 * Sidebar global compacta. Corregida contra mockups/00_all_views.png /
 * 01_inicio_mis_proyectos.png (fuente de verdad visual, confirmado por
 * el Product Owner el 2026-09-08 -- el texto original del master
 * prompt §3 no coincidía con el mockup real, ver Addendum de
 * docs/definiciones/galgoth-studio-mvp.md): Inicio, Mis proyectos,
 * Explorar, Plantillas arriba; Configuración, Usuario abajo. "Nuevo
 * proyecto" NO es un ítem de sidebar -- es la tarjeta CTA "Crear un
 * mob con IA" dentro del dashboard (mockup 01, ticket 021+).
 *
 * Simplificación consciente de este ticket: "Usuario" se renderiza
 * como un ítem de nav más, sin el tratamiento de tarjeta de perfil
 * (avatar + subtítulo "Creador de mundos") que muestra el mockup --
 * eso depende de datos reales de proyecto/usuario que no existen
 * todavía. Documentado como gap conocido, no una reinterpretación
 * silenciosa.
 *
 * Ticket 068 (feedback del PO sobre el editor de mob -- homologación
 * Modelo/Textura): botón para colapsar el sidebar a solo íconos, libera
 * espacio horizontal para pantallas de trabajo angostas (el editor). El
 * estado persiste en localStorage (clave `gsidebar-collapsed`) -- GSidebar
 * se remonta en cada navegación de ruta (no es un singleton), así que sin
 * persistencia el usuario tendría que volver a colapsarlo en cada pantalla,
 * derrotando el propósito del botón. Es un detalle puramente de
 * presentación (ancho/visibilidad de labels) -- ninguna lógica de
 * navegación cambia, `active`/`select` siguen funcionando igual colapsado.
 */
import { onMounted, ref } from 'vue'
import IconHome from '../icons/IconHome.vue'
import IconProjects from '../icons/IconProjects.vue'
import IconExplore from '../icons/IconExplore.vue'
import IconTemplates from '../icons/IconTemplates.vue'
import IconSettings from '../icons/IconSettings.vue'
import IconUser from '../icons/IconUser.vue'
import IconSidebarToggle from '../icons/IconSidebarToggle.vue'

export type GSidebarKey =
  | 'home'
  | 'projects'
  | 'explore'
  | 'templates'
  | 'settings'
  | 'user'

const items: Array<{ key: GSidebarKey; label: string; icon: unknown }> = [
  { key: 'home', label: 'Inicio', icon: IconHome },
  { key: 'projects', label: 'Mis proyectos', icon: IconProjects },
  { key: 'explore', label: 'Explorar', icon: IconExplore },
  { key: 'templates', label: 'Plantillas', icon: IconTemplates },
]

const bottomItems: Array<{ key: GSidebarKey; label: string; icon: unknown }> = [
  { key: 'settings', label: 'Configuración', icon: IconSettings },
  { key: 'user', label: 'Usuario', icon: IconUser },
]

const COLLAPSED_STORAGE_KEY = 'gsidebar-collapsed'

defineProps<{ active: GSidebarKey }>()
const emit = defineEmits<{ select: [GSidebarKey] }>()

const collapsed = ref(false)

onMounted(() => {
  try {
    collapsed.value = localStorage.getItem(COLLAPSED_STORAGE_KEY) === 'true'
  } catch {
    // localStorage inaccesible (modo privado estricto, etc.) -- arranca expandido, no es un error visible.
  }
})

function toggleCollapsed(): void {
  collapsed.value = !collapsed.value
  try {
    localStorage.setItem(COLLAPSED_STORAGE_KEY, String(collapsed.value))
  } catch {
    // best-effort -- si no persiste, el toggle sigue funcionando en esta sesión.
  }
}
</script>

<template>
  <nav class="g-sidebar" :class="{ 'g-sidebar--collapsed': collapsed }" aria-label="Navegación principal">
    <div class="g-sidebar__head">
      <span v-if="!collapsed" class="g-sidebar__brand">Galgoth Studio</span>
      <button type="button" class="g-sidebar__toggle" :aria-expanded="!collapsed" :aria-label="collapsed ? 'Expandir menú de navegación' : 'Colapsar menú de navegación'" @click="toggleCollapsed"><IconSidebarToggle :size="16" /></button>
    </div>

    <ul class="g-sidebar__list">
      <li v-for="item in items" :key="item.key"><button type="button" class="g-sidebar__item" :class="{ 'g-sidebar__item--active': active === item.key }" :aria-current="active === item.key ? 'page' : undefined" :aria-label="item.label" :title="collapsed ? item.label : undefined" @click="emit('select', item.key)"><component :is="item.icon" :size="18" aria-hidden="true" /><span v-if="!collapsed">{{ item.label }}</span></button></li>
    </ul>

    <div class="g-sidebar__spacer" />

    <ul class="g-sidebar__list">
      <li v-for="item in bottomItems" :key="item.key"><button type="button" class="g-sidebar__item" :class="{ 'g-sidebar__item--active': active === item.key }" :aria-current="active === item.key ? 'page' : undefined" :aria-label="item.label" :title="collapsed ? item.label : undefined" @click="emit('select', item.key)"><component :is="item.icon" :size="18" aria-hidden="true" /><span v-if="!collapsed">{{ item.label }}</span></button></li>
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
  transition: width var(--transition-fast);
}

.g-sidebar--collapsed {
  width: 68px;
}

.g-sidebar__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  margin-bottom: var(--space-5);
}

.g-sidebar--collapsed .g-sidebar__head {
  justify-content: center;
}

.g-sidebar__brand {
  font-weight: 700;
  font-size: var(--text-md);
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
}

.g-sidebar__toggle {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  border: none;
  background: none;
  color: var(--muted);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.g-sidebar__toggle:hover {
  background: var(--surface-2);
  color: var(--text);
}

.g-sidebar--collapsed .g-sidebar__toggle svg {
  transform: scaleX(-1);
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

.g-sidebar--collapsed .g-sidebar__item {
  justify-content: center;
  padding: 0;
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
