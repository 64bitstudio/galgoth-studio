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
