<script setup lang="ts">
/**
 * Paso 3 "Generación" del wizard (ticket 027, mockup 03) -- shell
 * visual, deliberadamente SIN funcionalidad real este ciclo (VoBo
 * explícito del PO): el job de generación (SSE con progreso/preview en
 * vivo) es el ticket 029, que todavía no existe. Las 6 etapas se
 * muestran fieles al mockup pero ninguna se marca como completada
 * (nunca hubo generación real que las haya avanzado) -- el mensaje
 * explícito evita que el shell aparente estar "colgado" o roto.
 *
 * El mob y su imagen de referencia SÍ se crearon de verdad en el paso
 * anterior (024/022) -- por eso el único camino hacia adelante desde
 * acá es volver al proyecto, no queda nada "pendiente" que perder.
 */
const STAGES = ['Analizando imagen…', 'Detectando silueta…', 'Creando esqueleto…', 'Generando cuboides…', 'Preparando UV…', 'Generando textura…']

defineEmits<{ 'back-to-project': [] }>()
</script>

<template>
  <div class="generation-step">
    <h2 class="generation-step__title">Generación</h2>
    <ul class="generation-step__stages">
      <li v-for="stage in STAGES" :key="stage" class="generation-step__stage">
        <span class="generation-step__stage-marker" aria-hidden="true">○</span>
        {{ stage }}
      </li>
    </ul>
    <p class="generation-step__notice">
      La generación automática por IA todavía no está disponible en esta versión (llega en un ticket posterior) -- el mob y la imagen de
      referencia ya quedaron guardados.
    </p>
    <button type="button" class="generation-step__back" @click="$emit('back-to-project')">Ir al proyecto</button>
  </div>
</template>

<style scoped>
.generation-step {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 420px;
}

.generation-step__title {
  margin: 0;
}

.generation-step__stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.generation-step__stage {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--muted);
}

.generation-step__stage-marker {
  font-size: var(--text-base);
}

.generation-step__notice {
  color: var(--muted);
  font-size: var(--text-sm);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}

.generation-step__back {
  align-self: flex-start;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--accent);
  color: var(--accent-ink);
  border: none;
  border-radius: var(--radius-md);
  font-weight: 600;
  cursor: pointer;
}
</style>
