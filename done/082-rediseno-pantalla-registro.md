# 082 — Rediseño visual de la pantalla de registro

## Objetivo
Mismo tratamiento visual del ticket 081 (login), ahora para
`RegisterView.vue`: fondo real aportado por Marco (escena distinta a la
del login), layout split-screen, tarjeta de vidrio con logo.
Puramente visual salvo una validación real nueva (confirmar contraseña) —
ningún endpoint ni el contrato de `sessionStore.register`/`authApi`
cambia.

## Alcance
- **Sí incluye:** rediseño de `RegisterView.vue` con el fondo/referencia
  aportados, reusando exactamente el patrón ya corregido de
  `LoginView.vue` (fondo a pantalla completa vía `.auth-view`, vela
  pareja, tarjeta de vidrio con borde/resplandor en el acento).
- **Agregado real (no decorativo):** campo "Confirmar contraseña" con
  validación del lado del cliente (bloquea el submit si no coincide con
  "Contraseña", mensaje de error explícito) — la referencia lo trae y es
  una mejora real de UX, no solo visual. Toggle de mostrar/ocultar en
  ambos campos de contraseña (mismo patrón del login).
- **No incluye:** login social real, página de términos y condiciones,
  ni preferencia de newsletter en el backend (ver decisiones abajo).

## Decisiones de Marco (elementos de la referencia sin funcionalidad real)
- **Google/Facebook**: mismo tratamiento del ticket 081 — visibles,
  `disabled`, etiqueta "Próximamente".
- **"Acepto términos y condiciones"**: no existe página de términos en la
  app hoy. Se muestra el checkbox y el link fieles a la referencia, pero
  ambos `disabled`/no interactivos con "Próximamente" — no bloquea el
  submit (el campo no existe realmente, así que no se puede exigir).
- **"Quiero recibir novedades del proyecto"**: `RegisterRequest` de
  auth-core-mc no soporta esta preferencia. Se muestra `disabled` con
  "Próximamente" — nunca un checkbox que aparente guardar algo que se
  descarta en silencio.

## Criterios de aceptación
- El formulario sigue enviando exactamente `{email, nombre, apellidos,
  password}` a `session.register` — mismo contrato de siempre. Nombre y
  Apellidos se mantienen como dos campos reales (la referencia trae solo
  uno, pero el backend exige ambos) — se acomodan en una fila de dos
  columnas para acercarse al layout de la referencia sin perder el campo.
- "Confirmar contraseña" bloquea el submit con un error explícito cuando
  no coincide con "Contraseña" — cubierto por un test nuevo.
- Tests de `RegisterView.spec.ts` actualizados (los existentes necesitan
  rellenar el campo nuevo) y verdes.
- Responsive/accesibilidad: mismo criterio que el ticket 081 (breakpoint
  980px, hit targets ≥ 40px, sin `<input>`/`<button>` multilínea).
- Verificación visual en vivo contra `studio-dev.galgoth.64bitstudio.com`
  tras el deploy, incluyendo un registro real de punta a punta.

## Hecho

Implementado como se describe arriba, reusando el patrón ya corregido de
`LoginView.vue` (fondo a pantalla completa vía `.auth-view`, vela pareja,
tarjeta de vidrio con borde/resplandor en el acento). Nombre/Apellidos en
fila de dos columnas; "Confirmar contraseña" con validación real de
cliente (bloquea el submit, nunca llega a `session.register`); toggle de
mostrar/ocultar en ambos campos de contraseña. Google/Facebook, términos
y newsletter: visibles, `disabled`, "Próximamente" (decisión de Marco —
ninguno tiene función real hoy).

**Tests**: `RegisterView.spec.ts` actualizado (los 3 tests existentes
ahora rellenan "Confirmar contraseña") + 1 test nuevo para el mismatch.
Suite completa: **750/750 tests**, lint y `vue-tsc -b` limpios.

**PR #108** mergeado a `dev`. El primer intento de merge falló en CI por
`TextureGenerationServiceTest` — un test de **backend** no relacionado
con este diff (100% frontend); el build anterior con el mismo backend
había pasado sin problema, mismo patrón de flake de infra ya visto en
esta sesión (Testcontainers/recursos compartidos de la VM). Confirmado
con un commit vacío (**PR #109**) que pasó limpio en el reintento.

**Verificación en vivo contra DEV real** (no simulada), tras el deploy
automático (Jenkins build #108 de `dev`, SUCCESS):
- Visual: layout split-screen completo, fondo continuo, tarjeta de
  vidrio — coincide con la referencia.
- Toggle de mostrar/ocultar contraseña y la validación de "las
  contraseñas no coinciden" probados en vivo (dev local) antes de
  desplegar.
- **Registro real de punta a punta** contra
  `https://studio-dev.galgoth.64bitstudio.com/register`: formulario
  enviado con datos reales, `201` del backend, pantalla "Revisa tu
  correo" mostrada correctamente con el email real de destino.

## Pendiente (fuera de alcance de este ticket)
- Extraer el shell split-screen (fondo/vela/tarjeta de vidrio) a un
  componente compartido entre `LoginView.vue` y `RegisterView.vue` —
  hoy están duplicados; el fix del ticket 081 (fondo "mochado") tuvo que
  aplicarse dos veces por separado. No se hizo en este ticket para no
  mezclar un refactor con una entrega visual, pero es una duplicación
  real que conviene resolver antes de una tercera pantalla con este
  mismo tratamiento.
