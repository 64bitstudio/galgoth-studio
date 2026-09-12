<script setup lang="ts">
/**
 * Header del editor de mob (ticket 036, pasada de fidelidad visual --
 * mockup 05: breadcrumb "Galgoth Studio > {mob}" + tabs de workspace
 * Modelo/Textura/Animación).
 *
 * Ticket 050 (HU-41): el tab "Textura" deja de estar deshabilitado --
 * pasa a ser una tab funcional. `activeTab`/`update:active-tab` siguen
 * el mismo contrato v-model que ya expone `GTabs.vue` (nada nuevo acá,
 * solo se deja de fijar `model-value="modelo"` a fuego); `MobEditor.vue`
 * es quien decide qué se monta en el body según la tab activa. "Animación"
 * sigue deshabilitada -- Fase 4, fuera de alcance de esta fase (mismo
 * criterio/mismo mecanismo de `GTabs.vue` ya establecido desde 036).
 *
 * Post-073 (pedido explícito del PO): el breadcrumb pasa del texto
 * estático "Galgoth Studio > {mob}" (sin links, separador "›" a mano) al
 * mismo diseño Y funcionalidad ya implementados en `ProjectDetail.vue`/
 * `ProjectsDashboard.vue` -- `IconChevron` como separador, cada nivel
 * intermedio es un link real, y completa la jerarquía real de navegación
 * (antes saltaba directo de la marca al mob, sin pasar por el proyecto):
 * "Galgoth Studio > Mis proyectos > {proyecto} > {mob}".
 */
import GTabs, { type GTabItem } from '../design-system/components/GTabs.vue'
import IconChevron from '../design-system/icons/IconChevron.vue'

const props = defineProps<{ projectId: string; projectName: string; mobName: string; activeTab: string }>()
const emit = defineEmits<{ 'update:activeTab': [string] }>()

const TABS: GTabItem[] = [
  { key: 'modelo', label: 'Modelo' },
  { key: 'textura', label: 'Textura' },
  { key: 'animacion', label: 'Animación', disabled: true, disabledReason: 'Próximamente' },
]
</script>

<template>
  <div class="editor-header">
    <nav class="editor-header__breadcrumb" aria-label="Ruta de navegación">
      <router-link to="/">Galgoth Studio</router-link>
      <IconChevron :size="12" />
      <router-link to="/projects">Mis proyectos</router-link>
      <IconChevron :size="12" />
      <router-link :to="`/projects/${projectId}`">{{ projectName }}</router-link>
      <IconChevron :size="12" />
      <span>{{ mobName }}</span>
    </nav>
    <GTabs
      class="editor-header__tabs"
      :items="TABS"
      :model-value="props.activeTab"
      @update:model-value="emit('update:activeTab', $event)"
    />
  </div>
</template>

<style scoped>
.editor-header {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: 0 var(--space-2);
  flex-shrink: 0;
}

.editor-header__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: var(--hit-target-min);
  font-size: var(--text-sm);
  color: var(--muted);
  white-space: nowrap;
}

.editor-header__breadcrumb a {
  color: var(--muted);
  text-decoration: none;
  transition: color 160ms cubic-bezier(0.16, 1, 0.3, 1);
}

.editor-header__breadcrumb a:hover {
  color: var(--text);
}

.editor-header__breadcrumb span {
  color: var(--text);
  font-weight: 600;
}

.editor-header__tabs {
  border-bottom: none;
}
</style>
