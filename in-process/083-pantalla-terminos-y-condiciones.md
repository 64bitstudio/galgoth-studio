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
