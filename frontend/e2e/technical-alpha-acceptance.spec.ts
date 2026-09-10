import { expect, test } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * Ticket 033 (HU-23) -- flujo E2E de aceptación completo del Technical
 * Alpha: crear proyecto → agregar mob (vía el wizard IA, que crea el mob
 * como parte de su propio flujo, 027) → generar geometría por IA
 * (`MockVisionProvider`/`MockReasoningProvider`, 025) → "Usar este
 * modelo" → editar a mano y Guardar → editar por IA con diff y Apply →
 * exportar `.bbmodel` v5 real. Corre contra un backend real (ver
 * `scripts/e2e.sh` -- Docker Compose + `AI_VISION_PROVIDER=mock`/
 * `AI_REASONING_PROVIDER=mock`), nunca contra mocks de red del navegador.
 *
 * La mitad v4 del AC (validar contra las fixtures reales de Blockbench,
 * 012/014) se cubre en un test de integración BACKEND aparte
 * (`E2eAcceptanceBbmodelExportTest`, paquete `aiorchestrator`) -- el
 * producto real nunca expuso una opción de exportar v4 (decisión
 * confirmada explícitamente con el PO), así que no hay ningún paso de
 * navegador que la ejerza.
 */
const referenceImagePath = path.resolve(
  fileURLToPath(new URL('.', import.meta.url)),
  '../../galgoth_studio_build_pack/references/carcomido_reference.png',
)

test('flujo completo: crear proyecto → mob por IA → editar a mano → editar por IA → exportar', async ({ page }) => {
  const runId = Date.now()
  const projectName = `Galgoth E2E ${runId}`
  const mobName = `Carcomido E2E ${runId}`

  await page.goto('/')
  // Ticket 039 (corrección de producto): el CTA de proyecto vacío pasó de
  // "Proyecto vacío" a "Crear nuevo proyecto" -- selector actualizado en
  // el checkpoint del ticket 056, que encontró esta suite desactualizada
  // (nunca corre en CI, ver Jenkinsfile) tras 039/050.
  await page.getByRole('button', { name: 'Crear nuevo proyecto' }).click()
  await page.getByLabel('Nombre del proyecto').fill(projectName)
  await page.getByRole('button', { name: 'Crear proyecto' }).click()
  await expect(page.getByRole('heading', { name: projectName })).toBeVisible()

  // -- Agregar mob + generar geometría por IA (wizard, 027/028/029/030) --
  await page.getByRole('link', { name: 'Crear con IA' }).click()
  await page.setInputFiles('input[aria-label="Elegir imagen de referencia"]', referenceImagePath)
  await page.getByLabel('Nombre del mob').fill(mobName)
  await page.getByRole('button', { name: 'Generar con IA →' }).click()

  await expect(page.getByRole('button', { name: 'Usar este modelo' })).toBeVisible({ timeout: 30_000 })
  await page.getByRole('button', { name: 'Usar este modelo' }).click()
  await page.getByRole('button', { name: 'Confirmar' }).click()
  await expect(page.getByRole('heading', { name: projectName })).toBeVisible()

  // -- Editar a mano y Guardar (016-020/034) --
  // `MobCard.vue` abre el editor con un `<button class="mob-card__open">`
  // (no un link) -- se filtra por clase porque el menú de acciones
  // ("Acciones de {mob}") es OTRO botón cuyo nombre accesible también
  // contiene el nombre del mob (violación de modo estricto por rol+nombre
  // solo) -- selector actualizado en 056, mismo hallazgo de
  // desactualización que arriba.
  await page.locator('.mob-card__open', { hasText: mobName }).click()
  // `.mob-editor__hierarchy` no existe en el markup actual (el panel real
  // es `HierarchyPanel.vue`, clase `.hierarchy-panel`) -- mismo hallazgo
  // de desactualización que el selector de arriba, corregido en 056.
  await expect(page.locator('.hierarchy-panel')).toBeVisible()
  // Ticket 056, hallazgo real: "Add cuboid" (ya renombrado a "Agregar
  // cuboide" en el 039) crece el footprint UV -- combinado con el
  // resize de MockReasoningProvider en el edit por IA de abajo, podía
  // desbordar el atlas (UV_ATLAS_OVERFLOW, un rechazo de negocio real).
  // Se reemplaza por un move (`moveSelectedCuboid`, 100% client-side,
  // nunca toca UV/atlas) sobre el cuboid "torso" ya existente -- sigue
  // siendo una edición manual real seguida de Guardar (AC del ticket),
  // sin crecer el footprint. La causa raíz completa del overflow (escala
  // del mock de edición demasiado agresiva para el packing real de hoy)
  // se corrigió aparte en `MockReasoningProvider` -- ver su docstring.
  await page.getByText('torso', { exact: true }).click()
  await page.getByLabel('Posición X').fill('2')
  await page.getByLabel('Posición X').blur()
  await page.getByRole('button', { name: 'Guardar' }).click()
  await expect(page.getByText(/Guardado \(revisión/)).toBeVisible()

  // -- Editar por IA con diff y Apply (031) --
  await page.getByRole('button', { name: 'Asistente IA' }).click()
  await page.getByLabel('Instrucción para la IA').fill('Haz las manos más grandes y los hombros más irregulares.')
  await page.getByRole('button', { name: 'Generar cambios' }).click()
  await expect(page.getByText('La IA modificará:')).toBeVisible({ timeout: 15_000 })
  await page.getByRole('button', { name: 'Aplicar cambios' }).click()
  await expect(page.getByLabel('Instrucción para la IA')).toBeVisible() // panel vuelve al estado fresco -- confirma que Apply terminó

  // -- Exportar .bbmodel v5 real (032) --
  await page.getByRole('button', { name: 'Exportar' }).click()
  await expect(page.getByText('Modelo listo para usar en tu servidor')).toBeVisible({ timeout: 10_000 })

  // `mob_drafts` no se resincroniza con la Revision al hacer "Guardar"/
  // "Aplicar cambios" (fuera de alcance -- eso es HU-30/autosave, un
  // ticket distinto): "Usar este modelo" ya escribió una fila de draft,
  // así que el panel puede mostrar "Guardar y exportar" en vez del botón
  // simple. Se usa siempre "Exportar última versión guardada"/"Exportar
  // .bbmodel" (la Revision YA guardada) -- nunca "Guardar y exportar"
  // (crearía una Revision nueva a partir de ese draft desactualizado).
  const exportLatestButton = page.getByRole('button', { name: 'Exportar última versión guardada' })
  const exportSimpleButton = page.getByRole('button', { name: 'Exportar .bbmodel' })
  const exportButton = (await exportLatestButton.count()) > 0 ? exportLatestButton : exportSimpleButton

  const [download] = await Promise.all([page.waitForEvent('download'), exportButton.click()])
  const downloadPath = await download.path()
  expect(downloadPath).toBeTruthy()

  const fs = await import('node:fs/promises')
  const bbmodelRaw = await fs.readFile(downloadPath!, 'utf-8')
  const bbmodel = JSON.parse(bbmodelRaw)

  expect(bbmodel.meta.format_version).toBe('5.0')
  expect(Array.isArray(bbmodel.elements)).toBe(true)
  expect(bbmodel.elements.length).toBeGreaterThan(0)
  expect(Array.isArray(bbmodel.groups)).toBe(true)
  expect(bbmodel.groups.length).toBeGreaterThan(0)
})
