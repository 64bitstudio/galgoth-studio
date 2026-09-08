import { createRouter, createWebHistory } from 'vue-router'

/**
 * Router mínimo de este ticket (002 — sistema de diseño). Las rutas
 * productivas reales (/projects, /projects/:id, editor de mob, etc. —
 * ver docs/definiciones/galgoth-studio-mvp.md §3) aterrizan en los
 * tickets 021+. Por ahora solo existe la ruta raíz (placeholder) y la
 * vitrina de componentes usada para QA visual contra los mockups.
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('./HomePlaceholder.vue'),
    },
    {
      path: '/dev/design-system',
      name: 'design-system-showcase',
      component: () => import('../design-system/Showcase.vue'),
    },
  ],
})

export default router
