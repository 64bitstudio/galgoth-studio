import { createRouter, createWebHistory } from 'vue-router'

/**
 * Ticket 021: primeras rutas productivas reales -- "/" y "/projects"
 * apuntaban al mismo dashboard "Mis proyectos" (simplificación
 * consciente, documentada entonces en la cabecera de
 * `ProjectsDashboard.vue`: el mockup separaba "Inicio" de "Mis
 * proyectos", el AC de ese ticket solo describía el listado completo).
 *
 * Ticket 071 (rediseño de Inicio, fidelidad visual estricta a
 * `rediseno.png`): "/" pasa a apuntar a `HomeView.vue` (saludo + CTAs +
 * "Continuar trabajando" + "Proyectos recientes") -- ya no es un alias de
 * "/projects". `ProjectsDashboard.vue` ("Mis proyectos", listado
 * completo) sigue en "/projects", sin cambios.
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../projects/HomeView.vue'),
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
      // Ticket 032, HU-19, mockup 11: pantalla de exportación (estado FMM
      // + draft sin guardar), alcanzable desde MobEditor.vue.
      path: '/projects/:projectId/mobs/:mobId/export',
      name: 'export-screen',
      component: () => import('../editor/ExportScreen.vue'),
    },
    {
      // Ticket 078: login/registro reales contra la API directa de
      // auth-core-mc (PROP-GS-AUTH-01) -- ver src/auth/.
      path: '/login',
      name: 'login',
      component: () => import('../auth/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../auth/RegisterView.vue'),
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
