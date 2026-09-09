<script setup lang="ts">
/**
 * Header del editor de modelo (ticket 036, pasada de fidelidad visual --
 * mockup 05: breadcrumb "Galgoth Studio > {mob}" + tabs de workspace
 * Modelo/Textura/Animación). Las tabs Textura/Animación existen
 * visualmente pero deshabilitadas (Visual Contract punto 8, mismo
 * criterio ya establecido en `GTabs.vue`) -- Fase 3/4, fuera de alcance
 * de este ticket (ticket explícitamente prohíbe avanzar funcionalidad
 * nueva).
 */
import GTabs, { type GTabItem } from '../design-system/components/GTabs.vue'

defineProps<{ mobName: string }>()

const TABS: GTabItem[] = [
  { key: 'modelo', label: 'Modelo' },
  { key: 'textura', label: 'Textura', disabled: true, disabledReason: 'Próximamente' },
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
    <GTabs class="editor-header__tabs" :items="TABS" model-value="modelo" />
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
