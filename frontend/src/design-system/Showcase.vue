<script setup lang="ts">
/**
 * Vitrina interna de componentes -- ruta de desarrollo, no productiva
 * (mismo espíritu que el development harness del ticket 008). Existe
 * para contrastar el sistema de diseño contra los mockups antes de
 * darlo por terminado (Visual Contract). No se linkea desde la
 * navegación real del producto.
 */
import { ref } from 'vue'
import GButton from './components/GButton.vue'
import GStatusPill from './components/GStatusPill.vue'
import GPanel from './components/GPanel.vue'
import GTabs, { type GTabItem } from './components/GTabs.vue'
import GSidebar, { type GSidebarKey } from './components/GSidebar.vue'

const sidebarActive = ref<GSidebarKey>('projects')

const tabItems: GTabItem[] = [
  { key: 'modelo', label: 'Modelo' },
  { key: 'textura', label: 'Textura', disabled: true, disabledReason: 'próximamente' },
  { key: 'animacion', label: 'Animación', disabled: true, disabledReason: 'próximamente' },
]
const activeTab = ref('modelo')

const swatches = [
  ['--bg', 'Fondo'],
  ['--panel', 'Panel (sidebar)'],
  ['--surface', 'Surface'],
  ['--surface-2', 'Surface 2'],
  ['--border', 'Border'],
  ['--accent', 'Accent (verde menta)'],
  ['--danger', 'Danger'],
  ['--warning', 'Warning'],
] as const
</script>

<template>
  <div class="showcase">
    <aside class="showcase__sidebar-demo">
      <GSidebar :active="sidebarActive" @select="(k) => (sidebarActive = k)" />
    </aside>

    <main class="showcase__content">
      <header class="showcase__header">
        <h1>Sistema de diseño — Galgoth Studio</h1>
        <p>
          Vitrina de componentes base (ticket
          <code class="mono">002-sistema-diseno-base-visual-contract</code>).
          Contrastar contra <code class="mono">mockups/00_all_views.png</code>.
        </p>
      </header>

      <section class="showcase__section">
        <h2>Color</h2>
        <div class="swatch-grid">
          <div v-for="[token, label] in swatches" :key="token" class="swatch">
            <div class="swatch__chip" :style="{ background: `var(${token})` }" />
            <div>
              <div class="swatch__token mono">{{ token }}</div>
              <div class="swatch__label">{{ label }}</div>
            </div>
          </div>
        </div>
      </section>

      <section class="showcase__section">
        <h2>Tipografía</h2>
        <p style="font-size: var(--text-xl)">Texto XL — títulos de sección</p>
        <p style="font-size: var(--text-lg)">Texto LG — subtítulos</p>
        <p style="font-size: var(--text-md)">Texto MD — cuerpo destacado</p>
        <p style="font-size: var(--text-base)">Texto base — cuerpo</p>
        <p style="font-size: var(--text-sm); color: var(--muted)">Texto SM — metadata</p>
        <p class="mono">from: [-4, 20, -4]  to: [4, 28, 4]  uuid: 3f2a9c1e-...</p>
      </section>

      <section class="showcase__section">
        <h2>Botones</h2>
        <div class="row">
          <GButton variant="primary">Exportar ahora</GButton>
          <GButton variant="secondary">Editar modelo</GButton>
          <GButton variant="danger">Eliminar</GButton>
          <GButton variant="ghost">Cancelar</GButton>
          <GButton variant="primary" disabled>Deshabilitado</GButton>
        </div>
      </section>

      <section class="showcase__section">
        <h2>Estado (Status Pill)</h2>
        <p class="hint">Nunca solo color -- cada estado trae su propio ícono.</p>
        <div class="row">
          <GStatusPill status="ready" />
          <GStatusPill status="in-progress" />
          <GStatusPill status="draft" />
        </div>
      </section>

      <section class="showcase__section">
        <h2>Tabs de workspace</h2>
        <GTabs :items="tabItems" v-model="activeTab" />
      </section>

      <section class="showcase__section">
        <h2>Panel / superficie</h2>
        <p class="hint">Un borde por nivel real de agrupación -- no anidar por reflejo.</p>
        <div class="row">
          <GPanel surface="panel">panel</GPanel>
          <GPanel surface="surface">surface</GPanel>
          <GPanel surface="surface-2">surface-2</GPanel>
          <GPanel elevated>elevated</GPanel>
        </div>
      </section>

      <section class="showcase__section">
        <h2>Foco por teclado</h2>
        <p class="hint">Tab a través de estos elementos -- el anillo de foco debe ser visible.</p>
        <div class="row">
          <GButton variant="secondary">Foco 1</GButton>
          <GButton variant="secondary">Foco 2</GButton>
          <a href="#" class="mono">enlace de prueba</a>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.showcase {
  display: flex;
  min-height: 100vh;
}
.showcase__sidebar-demo {
  flex-shrink: 0;
}
.showcase__content {
  flex: 1;
  padding: var(--space-6) var(--space-8);
  max-width: 900px;
}
.showcase__header p {
  color: var(--muted);
}
.showcase__section {
  margin: var(--space-8) 0;
  padding-top: var(--space-6);
  border-top: var(--border-width) solid var(--border);
}
.showcase__section h2 {
  font-size: var(--text-lg);
  margin-bottom: var(--space-3);
}
.hint {
  color: var(--muted);
  font-size: var(--text-sm);
  margin-top: -4px;
  margin-bottom: var(--space-3);
}
.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
}
.swatch-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-3);
}
.swatch {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.swatch__chip {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  border: var(--border-width) solid var(--border);
  flex-shrink: 0;
}
.swatch__token {
  font-size: var(--text-xs);
}
.swatch__label {
  font-size: var(--text-xs);
  color: var(--muted);
}
</style>
