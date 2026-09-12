<script setup lang="ts">
/**
 * Header de sección de Inicio (ticket 071, mockup rediseno.png) -- título
 * + link "Ver todos →". Se repite en "Continuar trabajando" y "Proyectos
 * recientes", de ahí la extracción (mismo criterio de "repetición ->
 * componente" que ya usa el resto del design system).
 */
import IconChevron from '../design-system/icons/IconChevron.vue'

defineProps<{ title: string; to: string }>()
</script>

<template>
  <div class="home-section-header">
    <h2 class="home-section-header__title">{{ title }}</h2>
    <router-link :to="to" class="home-section-header__link">
      Ver todos
      <IconChevron :size="14" />
    </router-link>
  </div>
</template>

<style scoped>
.home-section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.home-section-header__title {
  font-size: var(--text-lg);
  margin: 0;
}

.home-section-header__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
  transition: background-color 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.home-section-header__link:hover {
  background: var(--accent-soft);
}

.home-section-header__link:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}
</style>
