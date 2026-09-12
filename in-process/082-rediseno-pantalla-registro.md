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
