/**
 * Setup global de Vitest (post-075) -- jsdom no implementa `ResizeObserver`
 * (ver `ThreeViewportService.attachTo`, que ahora observa el contenedor
 * para resincronizar el canvas/cámara cuando cambia de tamaño, ej. al
 * expandir/colapsar `GSidebar.vue`). Sin este fake, CUALQUIER test que
 * monte un componente que llame `attachTo` (ThreeViewport.vue,
 * TextureCanvas.vue, GenerationPreviewViewport.vue, y cualquier otra
 * pantalla que los envuelve -- MobEditor.vue, AiMobWizard.vue,
 * GenerationStep.vue, ResultStep.vue) lanza `ReferenceError: ResizeObserver
 * is not defined`.
 *
 * Un solo setup global en vez de repetir el mismo `beforeAll` en cada uno
 * de esos ~7 archivos de test -- a diferencia del polyfill puntual de
 * `HTMLDialogElement.showModal()` (una sola pantalla lo necesita cada
 * vez), este afecta a cualquier pantalla que toque el viewport 3D
 * compartido.
 *
 * Fake inerte a propósito (nunca dispara el callback): ningún test
 * depende de que un resize real ocurra en jsdom (que no hace layout de
 * verdad) -- alcanza con que `new ResizeObserver()`/`observe()`/
 * `disconnect()` no revienten.
 */
class FakeResizeObserver implements ResizeObserver {
  observe(): void {
    // Inerte a propósito -- ver docstring de arriba.
  }
  unobserve(): void {
    // Inerte a propósito -- ver docstring de arriba.
  }
  disconnect(): void {
    // Inerte a propósito -- ver docstring de arriba.
  }
}

if (globalThis.ResizeObserver === undefined) {
  globalThis.ResizeObserver = FakeResizeObserver
}
