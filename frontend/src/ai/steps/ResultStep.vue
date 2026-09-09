<script setup lang="ts">
/**
 * Paso 4 "Resultado" del wizard (ticket 027, mockup 04) -- shell visual,
 * deliberadamente SIN funcionalidad real este ciclo (VoBo explícito del
 * PO): no hay ninguna propuesta real que mostrar todavía (depende de
 * 028/029), así que este componente NO es parte del flujo real
 * navegable del wizard -- no hay forma honesta de llegar acá con datos
 * reales todavía. Se monta solo desde `/dev/wizard-result-harness`
 * (mismo patrón que `/dev/design-system`/`/dev/viewport-harness`) con
 * datos de ejemplo, para verificar la fidelidad visual contra el
 * mockup 04 antes de que el ticket 030 la conecte de verdad.
 *
 * Las 3 acciones (HU-12) están deshabilitadas con su razón visible --
 * mismo criterio de accesibilidad ya establecido en el resto del
 * proyecto (nunca solo un tooltip).
 */
withDefaults(
  defineProps<{
    mobName?: string
    cuboidCount?: number
    boneCount?: number
    textureResolution?: string
  }>(),
  { mobName: 'Carcomido', cuboidCount: 27, boneCount: 7, textureResolution: '128×128' },
)
</script>

<template>
  <div class="result-step">
    <p class="result-step__example-notice">Vista previa con datos de ejemplo -- 030 conecta esta pantalla con una propuesta real.</p>
    <h2 class="result-step__ready">¡Tu modelo está listo!</h2>
    <h3 class="result-step__name">{{ mobName }}</h3>

    <dl class="result-step__stats">
      <div class="result-step__stat">
        <dt>Cuboides</dt>
        <dd>{{ cuboidCount }}</dd>
      </div>
      <div class="result-step__stat">
        <dt>Bones</dt>
        <dd>{{ boneCount }}</dd>
      </div>
      <div class="result-step__stat">
        <dt>Textura</dt>
        <dd>{{ textureResolution }}</dd>
      </div>
      <div class="result-step__stat">
        <dt>Compatibilidad FMM</dt>
        <dd>Sin verificar todavía</dd>
      </div>
    </dl>

    <div class="result-step__actions">
      <button type="button" class="result-step__action result-step__action--danger" disabled>Descartar</button>
      <button type="button" class="result-step__action" disabled>Regenerar</button>
      <button type="button" class="result-step__action result-step__action--primary" disabled>Usar este modelo</button>
    </div>
    <p class="result-step__disabled-reason">
      Estas acciones se habilitan cuando el resultado de una generación real esté disponible (tickets 029/030).
    </p>
  </div>
</template>

<style scoped>
.result-step {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: 360px;
}

.result-step__example-notice {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--muted);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-2);
}

.result-step__ready {
  margin: 0;
  color: var(--accent);
}

.result-step__name {
  margin: 0;
}

.result-step__stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
  margin: 0;
  padding: var(--space-4);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
}

.result-step__stat dt {
  font-size: var(--text-xs);
  color: var(--muted);
}

.result-step__stat dd {
  margin: 0;
  font-weight: 600;
}

.result-step__actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.result-step__action {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  border-radius: var(--radius-md);
  border: var(--border-width) solid var(--border);
  background: var(--surface);
  color: var(--text);
  font-weight: 600;
}

.result-step__action--primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
}

.result-step__action--danger {
  border-color: var(--danger);
  color: var(--danger);
  background: transparent;
}

.result-step__action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.result-step__disabled-reason {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--muted);
}
</style>
