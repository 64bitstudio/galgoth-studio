<script setup lang="ts">
/**
 * Selector de proyecto para "Crear un mob con IA" desde Inicio (ticket
 * 039, punto 1) -- el wizard de generación (`AiMobWizard.vue`) crea el
 * mob vía `POST /api/projects/{projectId}/mobs`, así que necesita un
 * proyecto real ANTES de abrir "Referencia". Sin rediseñar el wizard ni
 * la arquitectura (decisión explícita del PO): este diálogo resuelve el
 * `projectId` -- proyecto existente ya elegido, o uno nuevo creado acá
 * mismo -- y solo DESPUÉS se navega a `/projects/{projectId}/mobs/new-ai`.
 *
 * Sin proyectos todavía: arranca directo en modo "nuevo proyecto" (no
 * tiene sentido ofrecer un selector vacío).
 */
import { computed, ref } from 'vue'
import AppDialog from '../design-system/components/AppDialog.vue'
import GButton from '../design-system/components/GButton.vue'
import GSelect, { type GSelectOption } from '../design-system/components/GSelect.vue'
import type { ProjectSummary } from './projectsApi'

const props = defineProps<{
  projects: ProjectSummary[]
  busy?: boolean
  error?: string | null
}>()

const emit = defineEmits<{ confirm: [{ projectId: string } | { newProjectName: string }]; cancel: [] }>()

const NEW_PROJECT_VALUE = '__new__'
const selectedProjectId = ref(props.projects.length > 0 ? props.projects[0]!.id : NEW_PROJECT_VALUE)
const newProjectName = ref('')
const validationError = ref<string | null>(null)

/** Ticket 071 -- reemplaza el `<select>` nativo (visualmente inconsistente entre navegadores/SO) por `GSelect.vue`, ya establecido para esto desde el ticket 058. */
const projectOptions = computed<GSelectOption[]>(() => [
  ...props.projects.map((project) => ({ value: project.id, label: project.name })),
  { value: NEW_PROJECT_VALUE, label: '+ Nuevo proyecto' },
])

function confirm(): void {
  if (props.busy) {
    return
  }
  if (selectedProjectId.value === NEW_PROJECT_VALUE) {
    const trimmed = newProjectName.value.trim()
    if (!trimmed) {
      validationError.value = 'El nombre no puede estar vacío.'
      return
    }
    validationError.value = null
    emit('confirm', { newProjectName: trimmed })
    return
  }
  validationError.value = null
  emit('confirm', { projectId: selectedProjectId.value })
}
</script>

<template>
  <AppDialog title="¿En qué proyecto?" @cancel="emit('cancel')">
    <div v-if="projects.length > 0" class="ai-project-picker__field">
      <p class="ai-project-picker__label">Proyecto</p>
      <GSelect v-model="selectedProjectId" :options="projectOptions" label="Proyecto" :disabled="busy" />
    </div>
    <label v-if="selectedProjectId === NEW_PROJECT_VALUE" class="ai-project-picker__label">
      Nombre del proyecto nuevo
      <input v-model="newProjectName" type="text" class="ai-project-picker__input" aria-label="Nombre del proyecto nuevo" :disabled="busy" @keyup.enter="confirm" />
    </label>
    <p v-if="validationError" class="ai-project-picker__error">{{ validationError }}</p>
    <p v-if="error" class="ai-project-picker__error">{{ error }}</p>
    <template #actions>
      <GButton variant="ghost" :disabled="busy" @click="emit('cancel')">Cancelar</GButton>
      <GButton variant="primary" :disabled="busy" @click="confirm">{{ busy ? 'Creando…' : 'Continuar' }}</GButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.ai-project-picker__field,
.ai-project-picker__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.ai-project-picker__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.ai-project-picker__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
