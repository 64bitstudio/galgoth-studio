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
  await page.getByRole('button', { name: 'Proyecto vacío' }).click()
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
  await page.getByRole('link', { name: new RegExp(mobName) }).click()
  await expect(page.locator('.mob-editor__hierarchy')).toBeVisible()
  await page.getByRole('button', { name: 'Add cuboid' }).click()
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

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Exportar .bbmodel' }).click(),
  ])
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
