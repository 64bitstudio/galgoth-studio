import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '../auth/sessionStore'

declare module 'vue-router' {
  interface RouteMeta {
    /** Ticket 087 -- ver `beforeEach` más abajo. Ausente/`false` = ruta pública. */
    requiresAuth?: boolean
  }
}

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
 *
 * Ticket 087: `meta.requiresAuth` + `beforeEach` de abajo -- desde el
 * ticket 085 el backend exige `authenticated()` para estas rutas
 * (`home`/`projects-dashboard` llaman `GET /api/projects`, que exige
 * dueño real; `mob-editor`/`export-screen`/`ai-mob-wizard` operan sobre
 * mobs de un proyecto propio), pero el frontend nunca redirigía a nadie
 * -- un visitante sin sesión solo veía errores `401` sin explicación.
 * `project-detail` NO lleva `meta.requiresAuth` a propósito: desde el
 * ticket 085 un proyecto `PUBLIC` es legítimamente visible sin sesión
 * (y el ticket 088, "Explorar", depende de eso) -- el caso "proyecto
 * PRIVATE sin sesión" se resuelve reactivamente dentro de
 * `ProjectDetail.vue` (si la carga falla y no hay sesión, redirige a
 * login en vez de asumir "no existe": el backend nunca distingue los
 * dos casos, ver `ProjectAccessGuard`).
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../projects/HomeView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/projects',
      name: 'projects-dashboard',
      component: () => import('../projects/ProjectsDashboard.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/projects/:id',
      name: 'project-detail',
      component: () => import('../projects/ProjectDetail.vue'),
    },
    {
      // Ticket 088, HU-5: galería pública, sin `meta.requiresAuth` a propósito -- mismo criterio que `project-detail`.
      path: '/explore',
      name: 'explore',
      component: () => import('../explore/ExploreView.vue'),
    },
    {
      // Ticket 088, HU-6: ficha pública de un proyecto ajeno, modo lectura -- sin `meta.requiresAuth`.
      path: '/explore/:id',
      name: 'explore-project-detail',
      component: () => import('../explore/ExploreProjectDetail.vue'),
    },
    {
      // Ticket 027: wizard de generación IA, HU-10 -- pasos "Generación"/"Resultado"
      // son shells visuales sin job real todavía (VoBo del PO, 029/030 los conectan).
      path: '/projects/:projectId/mobs/new-ai',
      name: 'ai-mob-wizard',
      component: () => import('../ai/AiMobWizard.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Ticket 034: primera ruta productiva real del editor manual sobre
      // un mob existente -- antes solo se ejercía vía /dev/viewport-harness.
      path: '/projects/:projectId/mobs/:mobId/edit',
      name: 'mob-editor',
      component: () => import('../editor/MobEditor.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Ticket 032, HU-19, mockup 11: pantalla de exportación (estado FMM
      // + draft sin guardar), alcanzable desde MobEditor.vue.
      path: '/projects/:projectId/mobs/:mobId/export',
      name: 'export-screen',
      component: () => import('../editor/ExportScreen.vue'),
      meta: { requiresAuth: true },
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
      // Ticket 080: pide el reset (email/teléfono) -- dispara
      // POST /api/v1/password-reset/request, sin revelar si la cuenta existe.
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('../auth/ForgotPasswordView.vue'),
    },
    {
      // Ticket 080: ruta FIJA -- auth-core-mc#056 ya construye los links
      // reales de correo como `{origin}/password-reset/confirm?token=...`
      // para clientes con hosts_own_login_ui=true (galgoth-studio lo es
      // desde el ticket 055 de ese repo). Cambiar este path rompería los
      // links que ya están saliendo en producción.
      path: '/password-reset/confirm',
      name: 'password-reset-confirm',
      component: () => import('../auth/ResetPasswordView.vue'),
    },
    {
      // Ticket 093 -- ruta FIJA, mismo motivo que 'password-reset-confirm':
      // auth-core-mc#056 ya construye el link real de "cambiar correo" como
      // `{origin}/change-email/confirm?token=...`.
      path: '/change-email/confirm',
      name: 'change-email-confirm',
      component: () => import('../auth/EmailChangeConfirmView.vue'),
    },
    {
      // Ticket 094 -- ruta FIJA, mismo motivo: auth-core-mc#056 ya construye
      // el link real de "confirma tu correo" (que /register dispara) como
      // `{origin}/verify-email/confirm?token=...`. Hallazgo real de la
      // verificación en vivo del ticket 093 -- faltaba desde siempre.
      path: '/verify-email/confirm',
      name: 'verify-email-confirm',
      component: () => import('../auth/VerifyEmailConfirmView.vue'),
    },
    {
      // Ticket 093 -- pantalla "Usuario" (perfil, seguridad, preferencias,
      // eliminar cuenta). Ruta FIJA: auth-core-mc#063 ya redirige de
      // vuelta acá tras vincular una cuenta social
      // (`{origin}/usuario?linked={provider}` / `?link_error={...}`).
      path: '/usuario',
      name: 'user-profile',
      component: () => import('../account/UserView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Ticket 083: página estática de términos y condiciones -- también
      // el destino real del link en el checkbox de RegisterView.vue.
      path: '/terms',
      name: 'terms',
      component: () => import('../legal/TermsView.vue'),
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

/**
 * Ticket 087 -- `useSessionStore()` se llama DENTRO del callback (nunca
 * en el scope del módulo): este archivo se importa antes de que
 * `main.ts` cree e instale Pinia (`app.use(createPinia())`), así que
 * evaluarlo en top-level rompería con "no active Pinia" -- para cuando
 * el guard realmente se ejecuta (primera navegación real, tras
 * `app.mount`), Pinia ya está activa.
 */
router.beforeEach((to) => {
  if (!to.meta.requiresAuth) {
    return true
  }
  const session = useSessionStore()
  if (session.isAuthenticated) {
    return true
  }
  return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
