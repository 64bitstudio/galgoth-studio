# 083 — Pantalla de términos y condiciones

## Objetivo
Marco aportó diseño (fondo + referencia) para una página de términos y
condiciones — hoy no existe ninguna en galgoth-studio. Con esta pantalla
lista, el checkbox "Acepto términos y condiciones" de `RegisterView.vue`
(ticket 082, hasta ahora deshabilitado con "Próximamente" porque la
página no existía) pasa a ser real y obligatorio.

## Decisiones de Marco
- **Contenido**: se transcribe tal cual el texto de las 7 secciones que
  Marco ya redactó en el mockup (Uso de la plataforma, Cuentas y acceso,
  Contenido generado, Exportación de modelos, Propiedad intelectual,
  Privacidad y datos, Contacto) — no es texto placeholder, es el
  contenido real de la página.
- **Checkbox de Register**: se activa como real — requerido para poder
  crear la cuenta (validación de cliente), con link funcional a `/terms`.
- **"Descargar PDF"**: se muestra fiel a la referencia pero deshabilitado
  con "Próximamente" — generar un PDF real es trabajo aparte (librería,
  formato, mantenerlo sincronizado con el texto).

## Alcance
- **Sí incluye:** página estática `/terms` con navegación por secciones
  (tabs/lista lateral, como en la referencia), fondo real aportado por
  Marco, mismo tratamiento visual split-screen del resto de auth (login,
  registro, reset). Checkbox "He leído y acepto..." + botones "Volver" /
  "Aceptar y continuar" — "Aceptar y continuar" navega de regreso a
  `/register` (o a donde el usuario haya venido) solo si el checkbox está
  marcado.
- **Activación del checkbox real en `RegisterView.vue`**: dejar de estar
  `disabled`, se vuelve requerido para el submit (mismo patrón de
  validación de cliente que "confirmar contraseña", ticket 082).
- **No incluye:** versionado de términos (aceptar una versión específica
  guardada contra el usuario), aceptación registrada en el backend
  (auth-core-mc no tiene ningún campo para esto — puramente un gate de
  UI del lado de galgoth-studio, como ya lo era el checkbox mismo);
  generación real de PDF.

## Criterios de aceptación
- `/terms` renderiza las 7 secciones completas, navegación funcional
  entre ellas.
- El checkbox de Register bloquea el submit con un error explícito si no
  está marcado (igual que el mismatch de contraseñas).
- El link "términos y condiciones" en Register navega a `/terms` de
  verdad.
- Responsive/accesibilidad: mismo criterio que 081/082.
- Verificación visual en vivo contra `studio-dev.galgoth.64bitstudio.com`.

## Hecho

Implementado como se describe arriba: `TermsView.vue` (/terms), 7
secciones navegables (1 y 2.1 texto real de Marco; 2.2-7 borrador de
Claude, marcado explícitamente en el código con comentarios). "Descargar
PDF" deshabilitado con "Próximamente". `RegisterView.vue`: checkbox de
términos activado como real y obligatorio (bloquea el submit con error
explícito si no está marcado), link a `/terms` en pestaña nueva.

**Tests**: 5 tests nuevos de `TermsView.spec.ts` + `RegisterView.spec.ts`
actualizado (el helper `fillForm` ahora marca el checkbox por defecto,
más un test nuevo para el caso "no aceptado"). Suite completa: 765/765,
lint y type-check limpios.

**PR #111** (junto con el ticket 080) mergeado a `dev`, deploy automático
confirmado (Jenkins build #110, SUCCESS).

**Verificación en vivo contra DEV real**: `/terms` carga correctamente en
`https://studio-dev.galgoth.64bitstudio.com/terms` — navegación entre
secciones, checkbox y botones funcionando. Verificado también que el
link desde `RegisterView.vue` abre `/terms` en pestaña nueva sin perder
el formulario ya llenado.

## Pendiente (fuera de alcance de este ticket)
- **Revisión de Marco del contenido borrador** (secciones 2.2 en
  adelante, 3-7) antes de tratarlo como texto legal definitivo — nunca se
  presentó como contenido revisado/aprobado, solo un punto de partida.
- Mismo hallazgo ya señalado en 081/082: el shell split-screen sigue
  duplicado entre `LoginView.vue`, `RegisterView.vue`,
  `ForgotPasswordView.vue`, `ResetPasswordView.vue` y ahora
  `TermsView.vue` (5 copias) — cada vez más urgente extraerlo a un
  componente compartido antes de una sexta pantalla con este tratamiento.
