<script setup lang="ts">
/**
 * Pantalla de exportación (ticket 032, HU-19, mockup 11) --
 * `/projects/:projectId/mobs/:mobId/export`. Ruta propia, mismo patrón
 * que `MobEditor.vue` (034): route-level component, dueño de su propio
 * fetch (sin orquestador externo).
 *
 * AC #1: el archivo exportado SIEMPRE sale de `mob_revisions` (nunca el
 * draft en curso) -- "Guardar y exportar" NO llama a un endpoint
 * compuesto, orquesta dos llamadas ya existentes en secuencia: `saveRevision`
 * (020/023, "Guardar") y RECIÉN DESPUÉS `downloadBbmodel` (032). El panel
 * de compatibilidad FMM se calcula siempre contra la última revisión
 * guardada (nunca el draft), decisión confirmada explícitamente con el
 * PO -- describe siempre un artefacto real ya exportable.
 *
 * "Validar modelo" del mockup no tiene un botón propio acá: el panel de
 * compatibilidad ya se calcula en vivo y muestra cada error específico
 * (AC #4) apenas se carga la pantalla -- un botón separado solo
 * repetiría la misma llamada sin agregar nada real.
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMob, type MobSummary } from '../projects/mobsApi'
import { getDraft, saveRevision } from './draftPersistenceApi'
import { downloadBbmodel, getExportStatus, type ExportStatus } from './mobExportApi'
import { thumbnailUrl } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'
import GButton from '../design-system/components/GButton.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import type { MobProjectModel } from '../domain/MobProjectModel'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string
const mobId = route.params.mobId as string

const mob = ref<MobSummary | null>(null)
const status = ref<ExportStatus | null>(null)
const draftModel = ref<MobProjectModel | null>(null)
const loadError = ref<string | null>(null)
const notFound = ref(false)
const busy = ref(false)
const actionError = ref<string | null>(null)
const confirmingSaveAndExport = ref(false)
const lastExportedFilename = ref<string | null>(null)

onMounted(load)

async function load(): Promise<void> {
  try {
    const [mobSummary, exportStatus] = await Promise.all([getMob(mobId), getExportStatus(mobId)])
    mob.value = mobSummary
    status.value = exportStatus
    if (exportStatus.hasUnsavedChanges) {
      draftModel.value = (await getDraft(mobId)).model
    }
  } catch (error) {
    if (error instanceof ApiError && error.code === 'MOB_NOT_FOUND') {
      notFound.value = true
      return
    }
    loadError.value = error instanceof ApiError ? error.message : 'No se pudo cargar la pantalla de exportación.'
  }
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

async function exportLatestRevision(): Promise<void> {
  busy.value = true
  actionError.value = null
  try {
    const { filename, blob } = await downloadBbmodel(mobId)
    triggerDownload(blob, filename)
    lastExportedFilename.value = filename
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : 'No se pudo exportar el modelo.'
  } finally {
    busy.value = false
  }
}

function requestSaveAndExport(): void {
  confirmingSaveAndExport.value = true
}

function cancelSaveAndExport(): void {
  confirmingSaveAndExport.value = false
}

async function confirmSaveAndExport(): Promise<void> {
  if (!draftModel.value) {
    return
  }
  confirmingSaveAndExport.value = false
  busy.value = true
  actionError.value = null
  try {
    await saveRevision(mobId, draftModel.value)
    const { filename, blob } = await downloadBbmodel(mobId)
    triggerDownload(blob, filename)
    lastExportedFilename.value = filename
    status.value = await getExportStatus(mobId)
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : 'No se pudo guardar y exportar el modelo.'
  } finally {
    busy.value = false
  }
}

function backToEditor(): void {
  router.push(`/projects/${projectId}/mobs/${mobId}/edit`)
}

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home' || key === 'projects') {
    router.push('/projects')
  }
}
</script>

<template>
  <div class="export-screen-shell">
    <GSidebar active="projects" @select="handleSidebarSelect" />
    <main class="export-screen app-scroll">
      <p v-if="notFound" class="export-screen__error">
        Este mob no existe. <button type="button" class="export-screen__link-button" @click="router.push(`/projects/${projectId}`)">Volver al proyecto</button>
      </p>
      <p v-else-if="loadError" class="export-screen__error">{{ loadError }}</p>
      <template v-else-if="mob && status">
        <button type="button" class="export-screen__back" @click="backToEditor">← Exportar modelo</button>

        <div class="export-screen__body">
          <div class="export-screen__preview">
            <img v-if="thumbnailUrl(mob.thumbnailKey)" :src="thumbnailUrl(mob.thumbnailKey)!" :alt="mob.name" />
            <div v-else class="export-screen__preview-placeholder">{{ mob.name }}</div>
          </div>

          <div class="export-screen__panel">
            <h2 class="export-screen__title">{{ mob.name }}</h2>

            <template v-if="!status.hasSavedRevision && !status.hasUnsavedChanges">
              <p class="export-screen__empty">Este mob todavía no tiene ningún contenido para exportar -- edítalo primero.</p>
              <GButton variant="secondary" @click="backToEditor">Volver al editor</GButton>
            </template>

            <template v-else>
              <div v-if="status.hasSavedRevision" class="export-screen__destination">
                <p class="export-screen__destination-title">Destino: FreeMinecraftModels</p>
                <p class="export-screen__fmm-status" :class="status.fmmCompatible ? 'export-screen__fmm-status--ok' : 'export-screen__fmm-status--error'">
                  {{ status.fmmCompatible ? 'Modelo listo para usar en tu servidor' : 'Compatibilidad: con problemas' }}
                </p>
                <ul v-if="status.fmmIssues.length > 0" class="export-screen__fmm-issues">
                  <li v-for="(issue, index) in status.fmmIssues" :key="index" class="export-screen__fmm-issue">
                    <strong>{{ issue.severity === 'ERROR' ? 'Error' : 'Aviso' }}</strong> ({{ issue.element }}): {{ issue.message }}
                  </li>
                </ul>
              </div>

              <p v-if="lastExportedFilename" class="export-screen__success">Exportado: {{ lastExportedFilename }}</p>
              <p v-if="actionError" class="export-screen__error-inline">{{ actionError }}</p>

              <template v-if="confirmingSaveAndExport">
                <p class="export-screen__confirm-copy">Esto crea una nueva revisión guardada con el estado actual del draft. ¿Confirmar?</p>
                <div class="export-screen__actions">
                  <GButton variant="primary" :disabled="busy" @click="confirmSaveAndExport">Confirmar</GButton>
                  <GButton variant="secondary" :disabled="busy" @click="cancelSaveAndExport">Cancelar</GButton>
                </div>
              </template>
              <div v-else class="export-screen__actions">
                <template v-if="status.hasUnsavedChanges">
                  <GButton variant="primary" :disabled="busy" @click="requestSaveAndExport">Guardar y exportar</GButton>
                  <GButton v-if="status.hasSavedRevision" variant="secondary" :disabled="busy" @click="exportLatestRevision">
                    Exportar última versión guardada
                  </GButton>
                  <GButton variant="ghost" :disabled="busy" @click="backToEditor">Cancelar</GButton>
                </template>
                <template v-else>
                  <GButton variant="primary" :disabled="busy" @click="exportLatestRevision">Exportar .bbmodel</GButton>
                </template>
              </div>
            </template>
          </div>
        </div>
      </template>
      <p v-else class="export-screen__loading">Cargando…</p>
    </main>
  </div>
</template>

<style scoped>
.export-screen-shell {
  display: flex;
  height: 100vh;
}

.export-screen {
  flex: 1;
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
  overflow: auto;
}

.export-screen__back {
  align-self: flex-start;
  background: none;
  border: none;
  color: var(--text);
  font-size: var(--text-lg);
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.export-screen__body {
  display: flex;
  gap: var(--space-4);
  flex: 1;
  min-height: 0;
}

.export-screen__preview {
  width: 320px;
  flex-shrink: 0;
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--surface);
  display: flex;
  align-items: center;
  justify-content: center;
}

.export-screen__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.export-screen__preview-placeholder {
  color: var(--muted);
  padding: var(--space-4);
  text-align: center;
}

.export-screen__panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: 480px;
}

.export-screen__title {
  margin: 0;
}

.export-screen__destination {
  padding: var(--space-3);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
}

.export-screen__destination-title {
  margin: 0 0 var(--space-2);
  font-weight: 600;
}

.export-screen__fmm-status {
  margin: 0;
  font-weight: 600;
}

.export-screen__fmm-status--ok {
  color: var(--accent);
}

.export-screen__fmm-status--error {
  color: var(--danger);
}

.export-screen__fmm-issues {
  margin: var(--space-2) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-sm);
}

.export-screen__fmm-issue {
  color: var(--muted);
}

.export-screen__actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.export-screen__confirm-copy {
  margin: 0;
  color: var(--muted);
}

.export-screen__success {
  margin: 0;
  color: var(--accent);
  font-size: var(--text-sm);
}

.export-screen__error-inline {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.export-screen__empty,
.export-screen__loading,
.export-screen__error {
  color: var(--muted);
}

.export-screen__link-button {
  background: none;
  border: none;
  color: var(--accent);
  text-decoration: underline;
  cursor: pointer;
  padding: 0;
  font: inherit;
}
</style>
