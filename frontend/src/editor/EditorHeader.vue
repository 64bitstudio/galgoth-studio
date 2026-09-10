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
 */
import GTabs, { type GTabItem } from '../design-system/components/GTabs.vue'

const props = defineProps<{ mobName: string; activeTab: string }>()
const emit = defineEmits<{ 'update:activeTab': [string] }>()

const TABS: GTabItem[] = [
  { key: 'modelo', label: 'Modelo' },
  { key: 'textura', label: 'Textura' },
  { key: 'animacion', label: 'Animación', disabled: true, disabledReason: 'Próximamente' },
]
</script>

<template>
  <div class="editor-header">
    <div class="editor-header__breadcrumb">
      <span class="editor-header__brand">Galgoth Studio</span>
      <span class="editor-header__separator" aria-hidden="true">›</span>
      <span class="editor-header__mob-name">{{ mobName }}</span>
    </div>
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
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  font-size: var(--text-base);
  white-space: nowrap;
}

.editor-header__brand {
  color: var(--muted);
  font-weight: 600;
}

.editor-header__separator {
  color: var(--muted);
}

.editor-header__mob-name {
  color: var(--text);
  font-weight: 700;
}

.editor-header__tabs {
  border-bottom: none;
}
</style>
