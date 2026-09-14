<script setup lang="ts">
/**
 * Ticket 083: página de términos y condiciones -- destino real del link
 * en el checkbox de `RegisterView.vue` (antes deshabilitado con
 * "Próximamente", ticket 082).
 *
 * CONTENIDO: las secciones 1 ("Uso de la plataforma") y 2.1 ("Registro
 * de cuenta") son el texto real que Marco redactó (visible en su
 * mockup). El resto (2.2 en adelante, 3-7) es un BORRADOR escrito por
 * Claude siguiendo el mismo tono/estructura -- Marco pidió explícitamente
 * "redacta tú el resto, mismo tono" pero esto NO es texto legal
 * definitivo, solo un punto de partida para que lo revise antes de
 * tratarlo como vinculante.
 *
 * "Descargar PDF": deshabilitado con "Próximamente" (decisión de Marco)
 * -- generar un PDF real es trabajo aparte.
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import backgroundUrl from '../assets/auth/auth-terms-background.jpg'
import logoUrl from '../assets/auth/galgoth-logo.png'

interface Subsection {
  heading: string
  intro?: string
  bullets: string[]
}

interface Section {
  title: string
  intro: string
  subsections: Subsection[]
}

const sections: Section[] = [
  {
    title: 'Uso de la plataforma',
    intro:
      'Galgoth Studio es una plataforma en línea que permite a los usuarios crear, editar, personalizar y exportar modelos 3D y mobs para Minecraft mediante herramientas de inteligencia artificial. Al utilizar nuestros servicios, aceptas estos términos y condiciones en su totalidad.',
    subsections: [
      {
        heading: '1.1 Uso permitido',
        bullets: [
          'La plataforma está destinada a uso personal, educativo y creativo.',
          'Puedes crear y compartir contenido original, siempre que no infrinjas los derechos de terceros.',
          'Te comprometes a hacer un uso responsable, ético y conforme a la legislación aplicable.',
        ],
      },
      {
        heading: '1.2 Uso no permitido',
        bullets: [
          'No está permitido utilizar la plataforma para crear contenido ilegal, ofensivo, discriminatorio o que promueva la violencia.',
          'No puedes intentar vulnerar la seguridad de la plataforma, acceder a datos de otros usuarios o realizar ingeniería inversa.',
          'Cualquier uso indebido podrá resultar en la suspensión o eliminación de tu cuenta.',
        ],
      },
    ],
  },
  {
    title: 'Cuentas y acceso',
    intro:
      'Para utilizar ciertas funcionalidades de Galgoth Studio necesitas una cuenta. Eres responsable de mantener la confidencialidad de tus credenciales de acceso y de todas las actividades que se realicen en tu cuenta.',
    subsections: [
      {
        heading: '2.1 Registro de cuenta',
        bullets: ['Debes proporcionar información veraz, actual y completa.'],
      },
      {
        // Borrador (Claude) -- ver nota de la cabecera del archivo.
        heading: '2.2 Seguridad de la cuenta',
        bullets: [
          'Eres responsable de mantener tu contraseña en secreto y de cualquier actividad realizada desde tu cuenta.',
          'Notifícanos de inmediato si sospechas de un acceso no autorizado.',
        ],
      },
    ],
  },
  {
    // Borrador (Claude) -- ver nota de la cabecera del archivo.
    title: 'Contenido generado',
    intro:
      'Nuestras herramientas de IA generan modelos y mobs a partir de las instrucciones e imágenes de referencia que tú proporcionas.',
    subsections: [
      {
        heading: '3.1 Modelos y mobs generados por IA',
        bullets: [
          'Eres responsable del contenido que uses como entrada (imágenes de referencia, descripciones) para generar tus modelos.',
          'El resultado generado puede variar y no garantizamos que coincida exactamente con lo solicitado.',
        ],
      },
      {
        heading: '3.2 Restricciones de contenido',
        bullets: [
          'No se permite generar contenido que infrinja derechos de autor, marcas registradas o cualquier propiedad intelectual de terceros.',
          'Nos reservamos el derecho de eliminar contenido que viole estos términos, sin previo aviso.',
        ],
      },
    ],
  },
  {
    // Borrador (Claude) -- ver nota de la cabecera del archivo.
    title: 'Exportación de modelos',
    intro: 'Los modelos que crees en Galgoth Studio pueden exportarse para usarlos fuera de la plataforma.',
    subsections: [
      {
        heading: '4.1 Formatos soportados',
        bullets: ['Los modelos pueden exportarse en formato .bbmodel, compatible con Blockbench y Minecraft (Java Edition).'],
      },
      {
        heading: '4.2 Uso de los modelos exportados',
        bullets: [
          'Los modelos exportados pueden usarse libremente en tus propios mundos y proyectos de Minecraft.',
          'El uso comercial de modelos exportados es tu responsabilidad; Galgoth Studio no garantiza la ausencia de conflictos de derechos de terceros en contenido generado por IA.',
        ],
      },
    ],
  },
  {
    // Borrador (Claude) -- ver nota de la cabecera del archivo.
    title: 'Propiedad intelectual',
    intro: 'Esta sección distingue entre lo que es propiedad de Galgoth Studio y lo que sigue siendo tuyo.',
    subsections: [
      {
        heading: '5.1 De la plataforma',
        bullets: [
          'El software, diseño, marca y demás elementos de Galgoth Studio son propiedad de sus desarrolladores y están protegidos por las leyes de propiedad intelectual aplicables.',
        ],
      },
      {
        heading: '5.2 De tu contenido',
        bullets: ['Conservas los derechos sobre los modelos y mobs que crees en la plataforma, sujeto a las restricciones de la sección 3.'],
      },
    ],
  },
  {
    // Borrador (Claude) -- ver nota de la cabecera del archivo.
    title: 'Privacidad y datos',
    intro: 'Nos tomamos en serio la privacidad de tus datos.',
    subsections: [
      {
        heading: '6.1 Datos que recopilamos',
        bullets: ['Recopilamos la información necesaria para operar tu cuenta (nombre, correo, contraseña cifrada) y los proyectos/modelos que crees.'],
      },
      {
        heading: '6.2 Uso de tus datos',
        bullets: [
          'Usamos tus datos únicamente para operar y mejorar el servicio; no los vendemos a terceros.',
          'Puedes solicitar la eliminación de tu cuenta y tus datos en cualquier momento.',
        ],
      },
    ],
  },
  {
    // Borrador (Claude) -- ver nota de la cabecera del archivo.
    title: 'Contacto',
    intro: 'Si tienes preguntas sobre estos términos y condiciones, puedes escribirnos a soporte@galgoth.64bitstudio.com.',
    subsections: [],
  },
]

const activeIndex = ref(0)
const accepted = ref(false)
const router = useRouter()

function goBack(): void {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push('/register')
  }
}

function acceptAndContinue(): void {
  if (!accepted.value) {
    return
  }
  goBack()
}
</script>

<template>
  <div class="auth-view" :style="{ backgroundImage: `url(${backgroundUrl})` }">
    <section class="auth-view__hero" aria-hidden="true">
      <div class="auth-view__hero-scrim">
        <img :src="logoUrl" alt="" class="auth-view__hero-logo" />
        <h2 class="auth-view__hero-title">Ideas que<br /><span class="auth-view__hero-accent">construyen mundos</span></h2>
        <p class="auth-view__hero-subtitle">
          Lee con calma nuestras políticas antes de continuar. Queremos que tu experiencia en Galgoth Studio sea segura, transparente y
          extraordinaria.
        </p>
        <p class="auth-view__hero-tagline">Creatividad sin límites, para mundos extraordinarios.</p>
      </div>
    </section>

    <section class="auth-view__panel">
      <div class="auth-view__card">
        <header class="terms__header">
          <div>
            <h1 class="terms__title">Términos y condiciones</h1>
            <p class="terms__subtitle">Por favor, revisa cuidadosamente nuestros términos y condiciones antes de usar Galgoth Studio.</p>
            <p class="terms__updated">Última actualización: <strong>14 de septiembre de 2026</strong></p>
          </div>
          <button type="button" class="terms__pdf-btn" disabled title="Todavía no disponible en galgoth-studio">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M12 3v12m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" /></svg>
            Descargar PDF <em>Próximamente</em>
          </button>
        </header>

        <div class="terms__body">
          <nav class="terms__nav" aria-label="Secciones de los términos">
            <button
              v-for="(section, index) in sections"
              :key="section.title"
              type="button"
              class="terms__nav-item"
              :class="{ 'terms__nav-item--active': index === activeIndex }"
              :aria-current="index === activeIndex ? 'true' : undefined"
              @click="activeIndex = index"
            >
              <span class="terms__nav-index">{{ index + 1 }}</span>
              {{ section.title }}
            </button>
          </nav>

          <div class="terms__content">
            <article v-for="(section, index) in sections" v-show="index === activeIndex" :key="section.title">
              <h2>{{ index + 1 }}. {{ section.title }}</h2>
              <p>{{ section.intro }}</p>
              <section v-for="sub in section.subsections" :key="sub.heading">
                <h3>{{ sub.heading }}</h3>
                <p v-if="sub.intro">{{ sub.intro }}</p>
                <ul>
                  <li v-for="bullet in sub.bullets" :key="bullet">{{ bullet }}</li>
                </ul>
              </section>
            </article>
          </div>
        </div>

        <footer class="terms__footer">
          <label class="terms__accept">
            <input v-model="accepted" type="checkbox" />
            He leído y acepto los términos y condiciones
          </label>
          <div class="terms__actions">
            <button type="button" class="terms__back-btn" @click="goBack">Volver</button>
            <button type="button" class="terms__accept-btn" :disabled="!accepted" @click="acceptAndContinue">Aceptar y continuar →</button>
          </div>
        </footer>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-view {
  position: relative;
  display: flex;
  min-height: 100vh;
  background-color: var(--bg);
  background-size: cover;
  background-position: center;
}

.auth-view::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(6, 10, 14, 0.4);
}

.auth-view__hero,
.auth-view__panel {
  position: relative;
}

.auth-view__hero {
  flex: 1 1 32%;
  display: flex;
  align-items: flex-end;
}

.auth-view__hero-scrim {
  width: 100%;
  padding: var(--space-8) clamp(var(--space-5), 5vw, 56px);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  color: var(--text);
  background: linear-gradient(180deg, transparent, rgba(6, 10, 14, 0.55) 40%, rgba(6, 10, 14, 0.92));
}

.auth-view__hero-logo {
  width: 56px;
  height: 56px;
  object-fit: contain;
}

.auth-view__hero-title {
  margin: 0;
  font-size: clamp(24px, 2.6vw, 36px);
  line-height: 1.15;
}

.auth-view__hero-accent {
  color: var(--accent);
}

.auth-view__hero-subtitle {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-base);
}

.auth-view__hero-tagline {
  margin: var(--space-2) 0 0;
  padding-top: var(--space-4);
  border-top: var(--border-width) solid var(--border);
  color: var(--muted);
  font-size: var(--text-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.auth-view__panel {
  flex: 1 1 68%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}

.auth-view__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 100%;
  max-width: 920px;
  max-height: calc(100vh - var(--space-8));
  padding: var(--space-6);
  background-color: rgba(17, 24, 32, 0.72);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: var(--border-width) solid rgba(72, 229, 160, 0.35);
  border-radius: var(--radius-lg);
  box-shadow:
    var(--shadow-md),
    0 0 40px -12px rgba(72, 229, 160, 0.35);
}

.terms__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.terms__title {
  margin: 0;
  font-size: var(--text-2xl);
  color: var(--text);
}

.terms__subtitle {
  margin: var(--space-1) 0 0;
  max-width: 48em;
  color: var(--muted);
  font-size: var(--text-sm);
}

.terms__updated {
  margin: var(--space-2) 0 0;
  color: var(--muted);
  font-size: var(--text-xs);
}

.terms__updated strong {
  color: var(--accent);
}

.terms__pdf-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-sm);
  cursor: not-allowed;
  opacity: 0.6;
}

.terms__pdf-btn em {
  margin-left: var(--space-1);
  color: var(--warning);
  font-style: normal;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.terms__body {
  display: flex;
  gap: var(--space-4);
  min-height: 0;
  flex: 1;
}

.terms__nav {
  flex: 0 0 200px;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.terms__nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--muted);
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}

.terms__nav-item--active {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--text);
}

.terms__nav-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--muted);
  font-size: var(--text-xs);
}

.terms__nav-item--active .terms__nav-index {
  background: var(--accent);
  color: var(--accent-ink);
}

.terms__content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding-right: var(--space-2);
  color: var(--muted);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.terms__content h2 {
  margin: 0 0 var(--space-2);
  color: var(--text);
  font-size: var(--text-lg);
}

.terms__content h3 {
  margin: var(--space-4) 0 var(--space-1);
  color: var(--text);
  font-size: var(--text-base);
}

.terms__content p {
  margin: 0 0 var(--space-2);
}

.terms__content ul {
  margin: 0;
  padding-left: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.terms__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: var(--border-width) solid var(--border);
}

.terms__accept {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--muted);
  font-size: var(--text-sm);
}

.terms__actions {
  display: flex;
  gap: var(--space-3);
}

.terms__back-btn {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-5);
  background: transparent;
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
  cursor: pointer;
}

.terms__accept-btn {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-5);
  background: var(--accent);
  border: var(--border-width) solid transparent;
  border-radius: var(--radius-md);
  color: var(--accent-ink);
  font-size: var(--text-base);
  font-weight: 500;
  cursor: pointer;
}

.terms__accept-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

@media (max-width: 980px) {
  .auth-view__hero {
    display: none;
  }

  .auth-view__panel {
    flex: 1 1 100%;
  }

  .terms__body {
    flex-direction: column;
  }

  .terms__nav {
    flex: 0 0 auto;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .terms__nav-item {
    flex: 1 1 auto;
  }
}
</style>
