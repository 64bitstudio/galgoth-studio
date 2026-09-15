<script setup lang="ts">
/**
 * Tarjeta de proyecto de la galería de Explorar (ticket 088, HU-5).
 * Deliberadamente MÁS simple que `ProjectCard.vue` (Mis proyectos): sin
 * menú ⋮ (Renombrar/Duplicar/Exportar/Eliminar son acciones de dueño,
 * fuera de alcance acá -- ver el propio ticket), agrega en cambio la
 * línea "por {ownerDisplayName}" (dato nuevo del ticket 086 que "Mis
 * proyectos" no necesita mostrar, porque ahí el dueño ya eres tú).
 */
import { avatarUrl, thumbnailUrl } from '../api/apiConfig'
import { formatRelativeDate } from '../domain/relativeDate'
import type { ProjectSummary } from '../projects/projectsApi'

function mobCountLabel(count: number): string {
  return count === 1 ? '1 mob' : `${count} mobs`
}

/** Ticket 092 -- fallback sin avatar subido (ticket 091): inicial del nombre, mismo criterio ya anticipado en el propio ticket ("el frontend ya sabe mostrar un avatar por defecto"). */
function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase()
}

defineProps<{ project: ProjectSummary }>()

const emit = defineEmits<{ open: [string] }>()
</script>

<template>
  <button type="button" class="explore-card" @click="emit('open', project.id)">
    <span class="explore-card__thumbnails">
      <span v-for="thumb in project.mobThumbnails" :key="thumb.mobId" class="explore-card__thumbnail">
        <img v-if="thumb.thumbnailKey" :src="thumbnailUrl(thumb.thumbnailKey)!" alt="" />
        <span v-else class="explore-card__placeholder" aria-hidden="true" />
      </span>
      <span v-if="project.mobThumbnails.length === 0" class="explore-card__thumbnail">
        <span class="explore-card__placeholder" aria-hidden="true" />
      </span>
      <span v-if="project.mobCount > 3" class="explore-card__thumbnail explore-card__more">+{{ project.mobCount - 3 }}</span>
    </span>
    <span class="explore-card__name">{{ project.name }}</span>
    <p v-if="project.description" class="explore-card__description">{{ project.description }}</p>
    <span class="explore-card__meta">{{ mobCountLabel(project.mobCount) }} · {{ formatRelativeDate(project.updatedAt) }}</span>
    <span v-if="project.ownerDisplayName" class="explore-card__owner">
      <span class="explore-card__avatar">
        <img v-if="avatarUrl(project.avatarUrl)" :src="avatarUrl(project.avatarUrl)!" alt="" />
        <span v-else class="explore-card__avatar-initial" aria-hidden="true">{{ initial(project.ownerDisplayName) }}</span>
      </span>
      por {{ project.ownerDisplayName }}
    </span>
  </button>
</template>

<style scoped>
.explore-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-4);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  color: var(--text);
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition:
    border-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    transform 220ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.explore-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.explore-card__thumbnails {
  display: flex;
  gap: var(--space-2);
}

.explore-card__thumbnail {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--surface);
}

.explore-card__thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.explore-card__placeholder {
  display: block;
  width: 100%;
  height: 100%;
  background: var(--surface-2);
}

.explore-card__more {
  background: var(--surface-2);
  color: var(--muted);
  font-weight: 700;
  font-size: var(--text-md);
}

.explore-card__name {
  font-weight: 700;
  font-size: var(--text-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explore-card__description {
  margin: -4px 0 0;
  color: var(--muted);
  font-size: var(--text-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.explore-card__meta,
.explore-card__owner {
  font-size: var(--text-xs);
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explore-card__owner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.explore-card__avatar {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--accent-soft);
}

.explore-card__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.explore-card__avatar-initial {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--accent);
  font-size: 10px;
  font-weight: 700;
}
</style>
