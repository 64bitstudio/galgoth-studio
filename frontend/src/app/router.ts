import { createRouter, createWebHistory } from 'vue-router'

/**
 * Ticket 021: primeras rutas productivas reales -- "/" y "/projects"
 * apuntan al mismo dashboard "Mis proyectos" (simplificación consciente,
 * ver el comentario de cabecera de `ProjectsDashboard.vue`: el mockup
 * separa "Inicio" de "Mis proyectos", el AC de este ticket solo describe
 * el listado completo). "/projects/:id" es un detalle MÍNIMO -- el grid
 * completo de mobs llega en el ticket 022, que reemplaza ese componente.
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
      component: () => import('../projects/ProjectDetailPlaceholder.vue'),
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
  ],
})

export default router
