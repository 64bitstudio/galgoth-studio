# 037 — Corrección de UX y fidelidad visual del flujo de generación por IA

## Objetivo
El PO no da VoBo al flujo de generación por IA (wizard de 4 pasos:
Referencia → Configuración → Generación → Resultado). El flujo actual no
transmite que haya una IA trabajando: se siente como formularios estáticos
conectados, no como la experiencia premium de creación asistida que
definen los mockups (`02_nuevo_proyecto.png`, `03_generacion_ia.png`,
`04_resultado_ia.png`). Corrección EXCLUSIVA de UX/presentación -- sin
cambios de backend/contratos salvo necesidad real, sin rediseñar el
producto, sin convertirlo en un wizard genérico -- conservando toda la
lógica ya construida (SSE real, 4 etapas reales, preview incremental,
las 3 acciones de Resultado).

## Criterios de aceptación (TDD)
- **Generación** (prioridad 1, mockup `03_generacion_ia.png`): layout de
  2 columnas (etapas+progreso | preview 3D grande), marcadores de etapa
  reales (check/spinner/pendiente, no solo texto ●/○), overlay
  "Esperando la primera geometría…" mientras el preview real está vacío
  (nunca se ve como un viewport roto), Cancelar integrado y consistente.
- **Resultado** (prioridad 2, mockup `04_resultado_ia.png`): composición
  sólida (no una tarjeta de 360px en un vacío), preview del modelo real
  cuando existe (el `previewModel` final de Generación, YA calculado en
  memoria vía SSE -- sin ningún endpoint nuevo), acciones con jerarquía
  real (Usar este modelo = primaria, Regenerar = secundaria, Descartar =
  destructiva).
- **Referencia** (prioridad 3): zona de carga con estados reales
  (idle/dragover/uploaded/inválido), no solo un rectángulo punteado.
- **Configuración** (prioridad 4): pulido de jerarquía visual/spacing,
  sin cambiar los campos ya definidos (nombre/tipo/resolución).
- Ningún endpoint, contrato ni comportamiento de negocio cambia -- la
  única extensión es que `GenerationStep` ya pasa su `previewModel` en
  memoria a `AiMobWizard`/`ResultStep` (puramente frontend). Verificado
  con `npm run test`/`lint`/`build` en verde.
- Las 4 pantallas comparadas visualmente contra su mockup real antes de
  cerrar el ticket.

## Hecho

Las 4 pantallas verificadas en vivo (`npm run dev` + backend real con `AI_REASONING_PROVIDER=mock`/`AI_VISION_PROVIDER=mock`, sin costo) contra un flujo real de punta a punta -- Referencia (subida real de `carcomido_reference.png`, 3MB) → Configuración → Generación → Resultado -- con screenshots comparados contra los mockups antes de cerrar el ticket.

**Generación** (prioridad 1, mockup `03_generacion_ia.png`):
- `GenerationStep.vue` reescrito: layout de 2 columnas (`etapas+progreso` | `preview 3D grande`, antes todo apilado en una columna de `max-width: 480px`).
- Marcadores de etapa reales: `IconCheck` para completadas, spinner CSS real (anillo girando, sin librería) para la activa, círculo vacío para pendientes -- antes solo texto `●`/`○`.
- Barra de progreso con el % visible como número + mensaje real de la etapa debajo (`currentMessage`, ya existía, solo se le dio jerarquía visual).
- Overlay "Esperando la primera geometría…" (con el mismo spinner) mientras el preview real todavía no tiene cuboids -- nunca se ve como un viewport roto/vacío sin explicación.
- Cancelar integrado al fondo del panel de etapas, con la confirmación en dos pasos ya existente restyleada con `GButton`.
- **Verificado en vivo de verdad, no solo en tests**: interceptando `EventSource` en el propio navegador (JS de la sesión de verificación, nunca tocó código fuente) para introducir un delay artificial entre eventos SSE del proveedor mock -- que si no, completa la generación completa en <100ms y es imposible de fotografiar -- se capturó la pantalla real a mitad de generación real: "Creando rig..." activo con spinner, "Analizando referencia"/"Detectando silueta" con check verde, "Generando cuboides" pendiente, progreso 68%, mensaje real "Creando hueso: left_arm", y el primer cuboid ya renderizado en el viewport.

**Resultado** (prioridad 2, mockup `04_resultado_ia.png`):
- `ResultStep.vue` reescrito: layout de 2 columnas (preview del modelo real | panel de resumen+acciones, antes una tarjeta de `max-width: 360px` sola en un gran vacío).
- **`previewModel` (nuevo, opcional)**: `GenerationStep` ya pasa su modelo final (calculado en memoria vía SSE, el mismo que ya renderizaba mientras generaba) a través de `AiMobWizard.vue` hasta `ResultStep`, que lo muestra de solo lectura con `GenerationPreviewViewport` (029) -- **cero endpoint nuevo, cero dato nuevo del backend**, es 100% un dato que el frontend ya tenía en memoria y antes se descartaba al llegar a "completado".
- Acciones con jerarquía real: Descartar (`danger`), Regenerar (`secondary`), Usar este modelo (`primary`) -- antes los 3 eran `<button>` casi idénticos visualmente.
- Confirmación de "Usar este modelo" reescrita para explicar explícitamente "se creará la primera revisión... pasará a ser el modelo base real" (antes decía solo "se crea la primera revisión guardada del mob").
- **Verificado en vivo**: generación real completa (proveedor mock) → Resultado muestra el modelo real (4 cuboides) renderizado en 3D, stats reales, "Regenerar" real (vuelve a Generación, genera un segundo modelo).

**Referencia** (prioridad 3): `ReferenceStep.vue` -- dropzone mucho más grande y prominente (antes un rectángulo punteado chico), ícono real (`IconImage`, nuevo), estado `dragover` real (antes solo `hover` con `:hover`/`@dragover.prevent` sin feedback visual propio), estado inválido con ícono de advertencia (`IconWarning`, nuevo) y borde/color de error en el propio dropzone (antes solo un párrafo de texto rojo debajo). Mismo comportamiento exacto (valida y emite `selected`, el wizard avanza automáticamente) -- **cero funcionalidad nueva**.

**Configuración** (prioridad 4): pulido -- panel con fondo/borde consistente con el resto del wizard, subtítulo explicativo, imagen de referencia más grande con sombra. Mismos campos exactos (nombre/tipo/resolución), sin ningún cambio de comportamiento.

**Verificación real**: `npm run lint`/`vue-tsc -b`/`npm run build`/`npm run test` (317 tests, incluye actualización de selectores en `GenerationStep.spec.ts`/`ResultStep.spec.ts`/`AiMobWizard.spec.ts`/`ReferenceStep.spec.ts` por el refactor de `<button>` sin clase a `GButton`/`IconButton` -- mismo comportamiento, nueva forma de encontrar los elementos) todos en verde. `git status` confirma cero archivos de `backend/` tocados -- la única "extensión funcional" de todo el ticket es el nuevo segundo parámetro de `completed` (el `previewModel`), puramente frontend, sin tocar ningún contrato/endpoint real.
