<script setup lang="ts">
import GSidebar from '../design-system/components/GSidebar.vue'
import GPanel from '../design-system/components/GPanel.vue'
</script>

<template>
  <div class="shell">
    <GSidebar active="projects" @select="() => {}" />
    <main class="shell__content">
      <GPanel>
        <p class="placeholder">
          Dashboard "Mis proyectos" — pendiente (ticket
          <code class="mono">021-crud-proyectos-dashboard</code>). Ver
          <router-link to="/dev/design-system"
            >vitrina de componentes</router-link
          >
          para el sistema de diseño base ya implementado.
        </p>
      </GPanel>
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100vh;
}
.shell__content {
  flex: 1;
  padding: var(--space-6);
  overflow: auto;
}
.placeholder {
  color: var(--muted);
  max-width: 60ch;
}
.placeholder a {
  color: var(--accent);
}
</style>
