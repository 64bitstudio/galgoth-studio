# 081 — Rediseño visual de la pantalla de login

## Objetivo
`LoginView.vue` (ticket 078) es hoy una tarjeta genérica centrada, sin
marca. Marco aportó el logo real de Galgoth Studio, un fondo ilustrado
(escena nocturna estilo Minecraft) y una referencia de layout completa
(split-screen: panel de marca a la izquierda, formulario a la derecha).
Este ticket es puramente visual — ningún endpoint ni lógica de
`sessionStore`/`authApi` cambia.

## Alcance
- **Sí incluye:** rediseño de `LoginView.vue` siguiendo la referencia
  (logo + fondo aportados, layout split-screen, textos de marca del lado
  izquierdo), usando los tokens ya existentes de
  `design-system/tokens/tokens.css` (el acento `--accent: #48e5a0` ya es
  prácticamente el verde de la referencia — no se agregan colores nuevos
  fuera del contrato visual).
- **No incluye:** `RegisterView.vue` (queda con el look anterior por
  ahora, se puede pedir aparte); login social real con Google/Facebook
  (nunca se cableó del lado de galgoth-studio — ver hallazgo del ticket
  052/053); pantalla de reset de password (ticket 080, sin arrancar).

## Decisión de Marco (elementos de la referencia sin funcionalidad real)
Los botones "Continuar con Google/Facebook" y el link "¿Olvidaste tu
contraseña?" se **incluyen visualmente pero deshabilitados**, con una
etiqueta "Próximamente" — fieles a la referencia, pero sin aparentar una
función que hoy no existe (nunca botones decorativos que parezcan
funcionar y no hagan nada).

## Criterios de aceptación
- El formulario sigue siendo 100% funcional: mismo `sessionStore.login`,
  mismos campos (`identifier`/`password`), mismo manejo de error y del
  caso 2FA, mismo link a `/register`.
- Logo y fondo aportados por Marco, usados como assets reales del
  proyecto (`frontend/src/assets/auth/`), no linkeados externamente.
- Los 3 botones/link no funcionales están visualmente presentes pero
  `disabled`/no interactivos, con indicación clara "Próximamente".
- Responsive: layout de una sola columna (sin el panel izquierdo) por
  debajo de 980px (mismo breakpoint ya usado en `HomeView.vue`, no uno
  nuevo), mismo criterio de accesibilidad del resto del design system
  (hit targets ≥ 40px, foco visible, sin `<input>`/`<button>` multilínea
  por el hook `ui-accessibility-guard`).
- Tests de `LoginView.spec.ts` actualizados/verdes — la lógica de submit
  no cambia, solo se ajustan los selectores si el markup se reestructura.
- Verificación visual en vivo (dev local contra el backend real, y tras
  desplegar, contra `studio-dev.galgoth.64bitstudio.com`).

## Hecho

Implementado como se describe arriba: layout split-screen (panel de marca
a la izquierda con el fondo real, headline, 3 features y tagline; tarjeta
de login a la derecha con el logo real), usando tokens existentes
(`--accent`, `--panel`, `--surface`, etc. de `tokens.css`), sin agregar
paleta nueva. Assets optimizados antes de subirlos: logo redimensionado a
512px (era 1254px) y fondo reconvertido a JPEG q82 (era PNG sin comprimir)
— de ~3MB combinados a ~440KB.

**Elementos sin funcionalidad real** (decisión de Marco): "Continuar con
Google/Facebook" y "¿Olvidaste tu contraseña?" quedan visibles, fieles a
la referencia, pero `disabled` con etiqueta "Próximamente" — ninguno de
los dos está cableado del lado de galgoth-studio hoy.

**Agregado real, no decorativo**: toggle de mostrar/ocultar contraseña
(`aria-label`/`aria-pressed` dinámicos), verificado funcionando (texto
plano visible al activarlo, vuelve a ocultarse al desactivarlo).

**Tests**: `LoginView.spec.ts` sin cambios de código — la lógica de
submit no se tocó, los selectores por `type` de input siguen encontrando
los campos correctos. Suite completa del frontend: **749/749 tests**,
lint (`eslint . --max-warnings 0`) y `vue-tsc -b` limpios.

**Verificación visual en vivo, dos rondas**:
1. Dev server local (`npm run dev`): el entorno de captura de pantalla
   de esta sesión quedó fijo en un viewport de 960px (por debajo del
   breakpoint de 980px), así que para confirmar el layout desktop se
   inyectó temporalmente un `<style>` forzando `display:flex` en el panel
   izquierdo (removido antes de continuar) — nunca un cambio al código,
   solo una forma de ver el layout ancho en un viewport angosto.
   Confirmado: coincide con la referencia (fondo, headline con acento
   verde en "tus ideas", 3 features con iconos, tagline). El toggle de
   contraseña se probó tecleando una contraseña real y alternando
   mostrar/ocultar.
2. **Contra `https://studio-dev.galgoth.64bitstudio.com/login` real**,
   tras el deploy automático (Jenkins build #103 de `dev`, SUCCESS): el
   viewport real (1300px) sí muestra el split-screen completo sin ningún
   workaround. Además, **login real de punta a punta**: se inició sesión
   con una cuenta real de galgoth-studio (creada durante la verificación
   del ticket 056 de auth-core-mc) y la app navegó correctamente a
   `/` mostrando el home autenticado (proyectos reales del usuario) — el
   rediseño no rompió el flujo de autenticación real.

## Pendiente (fuera de alcance de este ticket)
- Mismo rediseño para `RegisterView.vue`, si Marco lo pide — queda con el
  look anterior por ahora.
