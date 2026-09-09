import { createRouter, createWebHistory } from 'vue-router'

/**
 * Ticket 021: primeras rutas productivas reales -- "/" y "/projects"
 * apuntan al mismo dashboard "Mis proyectos" (simplificación consciente,
 * ver el comentario de cabecera de `ProjectsDashboard.vue`: el mockup
 * separa "Inicio" de "Mis proyectos", el AC de este ticket solo describe
 * el listado completo). "/projects/:id" apunta a `ProjectDetail.vue`
 * (ticket 022, HU-04) -- reemplaza el placeholder mínimo del 021.
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../projects/ProjectsDashboard.vue'),
    },
    {
      path: '/projects',
      name: 'projects-dashboard',
      component: () => import('../projects/ProjectsDashboard.vue'),
    },
    {
      path: '/projects/:id',
      name: 'project-detail',
      component: () => import('../projects/ProjectDetail.vue'),
    },
    {
      // Ticket 027: wizard de generación IA, HU-10 -- pasos "Generación"/"Resultado"
      // son shells visuales sin job real todavía (VoBo del PO, 029/030 los conectan).
      path: '/projects/:projectId/mobs/new-ai',
      name: 'ai-mob-wizard',
      component: () => import('../ai/AiMobWizard.vue'),
    },
    {
      // Ticket 034: primera ruta productiva real del editor manual sobre
      // un mob existente -- antes solo se ejercía vía /dev/viewport-harness.
      path: '/projects/:projectId/mobs/:mobId/edit',
      name: 'mob-editor',
      component: () => import('../editor/MobEditor.vue'),
    },
    {
      path: '/dev/design-system',
      name: 'design-system-showcase',
      component: () => import('../design-system/Showcase.vue'),
    },
    {
      path: '/dev/viewport-harness',
      name: 'viewport-harness',
      component: () => import('../viewport/ViewportHarness.vue'),
    },
    {
      // Ticket 027: "Resultado" (mockup 04) no es parte del flujo real navegable
      // todavía (sin propuesta real hasta 029/030) -- solo verificable visualmente acá.
      path: '/dev/wizard-result-harness',
      name: 'wizard-result-harness',
      component: () => import('../ai/steps/ResultStep.vue'),
    },
  ],
})

export default router
