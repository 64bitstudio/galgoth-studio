import { expect, test } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * Ticket 056 (HU-43, `docs/definiciones/galgoth-studio-fase3-textura.md`)
 * -- flujo E2E de aceptación completo de Fase 3, análogo a
 * `technical-alpha-acceptance.spec.ts` (033/HU-23) pero para el ciclo de
 * textura: mob con geometría ya usable → pintar textura A MANO (el
 * camino que este mismo ticket cerró -- ver checkpoint del PO en el
 * `## Hecho`) → Guardar (flush del atlas + nueva `mob_revision` con
 * textura real) → exportar `.bbmodel` que embebe esa textura real (no el
 * placeholder checkerboard, 011) → HU-20 (validación FMM) sigue en
 * verde. Corre contra un backend real (`scripts/e2e.sh` -- Docker
 * Compose + `AI_VISION_PROVIDER=mock`/`AI_REASONING_PROVIDER=mock`/
 * `AI_IMAGE_PROVIDER=mock`), nunca contra mocks de red del navegador.
 *
 * "pintar a mano y/o generar por IA" (AC #1 del ticket): se ejercita el
 * camino MANUAL a propósito -- es el que estaba roto (el editor de
 * textura, 046/047, nunca subía el bitmap al backend) y el que este
 * ticket cerró explícitamente; el camino de generación por IA (054/055)
 * ya tiene su propia cobertura de componente
 * (`TextureAiGeneratorPanel.spec.ts`) y de pipeline backend.
 *
 * "abre en Blockbench sin diálogos de reparación" (AC #3): mismo criterio
 * ya establecido por 009/012/014/033 -- se verifica que el panel de
 * compatibilidad FMM (`FmmCompatibilityValidator`, HU-20) reporte
 * "Modelo listo para usar en tu servidor" en la pantalla de exportación
 * real, en vez de lanzar Blockbench de verdad (la app nunca lo hace, ver
 * `BlockbenchRealFixtureConformanceTest` en el backend para el
 * equivalente de conformidad estructural).
 */
const referenceImagePath = path.resolve(
  fileURLToPath(new URL('.', import.meta.url)),
  '../../galgoth_studio_build_pack/references/carcomido_reference.png',
)

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])

test('flujo completo Fase 3: geometría ya usable → pintar textura a mano → Guardar → exportar .bbmodel con textura real', async ({ page }) => {
  const runId = Date.now()
  const projectName = `Galgoth Textura E2E ${runId}`
  const mobName = `Carcomido Textura E2E ${runId}`

  // -- Proyecto vacío + mob con geometría ya usable (revisión >= 1) vía el wizard IA --
  // "Usar este modelo" + "Confirmar" ya crea la PRIMERA mob_revision
  // (`DraftPersistenceService.applyGenerationProposal`) -- mismo punto de
  // partida que HU-23/033, sin necesidad de un Guardar manual de geometría antes.
  await page.goto('/')
  await page.getByRole('button', { name: 'Crear nuevo proyecto' }).click()
  await page.getByLabel('Nombre del proyecto').fill(projectName)
  await page.getByRole('button', { name: 'Crear proyecto' }).click()
  await expect(page.getByRole('heading', { name: projectName })).toBeVisible()

  await page.getByRole('link', { name: 'Crear con IA' }).click()
  await page.setInputFiles('input[aria-label="Elegir imagen de referencia"]', referenceImagePath)
  await page.getByLabel('Nombre del mob').fill(mobName)
  await page.getByRole('button', { name: 'Generar con IA →' }).click()

  await expect(page.getByRole('button', { name: 'Usar este modelo' })).toBeVisible({ timeout: 30_000 })
  await page.getByRole('button', { name: 'Usar este modelo' }).click()
  await page.getByRole('button', { name: 'Confirmar' }).click()
  await expect(page.getByRole('heading', { name: projectName })).toBeVisible()

  // -- Entrar al editor del mob recién creado --
  // `MobCard.vue` abre el editor con un `<button class="mob-card__open">`
  // (no un link -- necesita envolver un menú de acciones interactivo
  // aparte, ver su docstring) -- se filtra por clase porque ese menú de
  // acciones ("Acciones de {mob}") es OTRO botón cuyo nombre accesible
  // también contiene el nombre del mob (violación de modo estricto si se
  // filtra solo por rol+nombre).
  await page.locator('.mob-card__open', { hasText: mobName }).click()
  await expect(page.locator('.hierarchy-panel')).toBeVisible()

  // -- Pintar textura A MANO en el tab Textura (046/047, gap cerrado en 056) --
  await page.getByRole('tab', { name: 'Textura' }).click()
  const canvas = page.getByLabel('Atlas de textura del mob -- superficie de pintado')
  await expect(canvas).toBeVisible()
  const box = await canvas.boundingBox()
  if (!box) {
    throw new Error('El canvas de textura no tiene bounding box -- no se pudo pintar.')
  }
  // Pincel por default (rojo de la paleta) -- un solo trazo real alcanza
  // para que el atlas deje de ser un buffer en blanco (AC #1).
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 10, box.y + box.height / 2 + 10, { steps: 4 })
  await page.mouse.up()

  // -- Guardar real (056: flush del atlas pintado ANTES de crear la Revision) --
  await page.getByRole('tab', { name: 'Modelo' }).click()
  await page.getByRole('button', { name: 'Guardar' }).click()
  await expect(page.getByText(/Guardado \(revisión/)).toBeVisible({ timeout: 15_000 })

  // -- Exportar .bbmodel con la textura real (AC #2/#3) --
  await page.getByRole('button', { name: 'Exportar' }).click()
  await expect(page.getByText('Modelo listo para usar en tu servidor')).toBeVisible({ timeout: 10_000 }) // HU-20: sin errores pendientes

  // `mob_drafts` no se resincroniza con la Revision al hacer "Guardar"
  // (fuera de alcance de 056 -- eso es HU-30/autosave, un ticket
  // distinto) -- "Usar este modelo" YA escribió una fila de draft (sin
  // textura), así que el panel puede mostrar "Guardar y exportar" en vez
  // del botón simple. Se usa siempre "Exportar última versión guardada"/
  // "Exportar .bbmodel" (la Revision YA guardada, CON la textura real) --
  // nunca "Guardar y exportar" (crearía una Revision nueva a partir de
  // ese draft desactualizado, perdiendo el storageKey real).
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
  expect(Array.isArray(bbmodel.textures)).toBe(true)
  const texture = bbmodel.textures[0]
  // AC #2 del ticket 056: "texture", NUNCA "placeholder" -- confirma que
  // el exportador tomó la rama de textura real (gap cerrado en este
  // mismo ticket), no el checkerboard de 011.
  expect(texture.name).toBe('texture')

  const pngBytes = Buffer.from(texture.source.split(',')[1], 'base64')
  expect(pngBytes.subarray(0, 8)).toEqual(PNG_SIGNATURE) // PNG real, decodificable
  const width = pngBytes.readUInt32BE(16)
  const height = pngBytes.readUInt32BE(20)
  expect(width).toBe(texture.width)
  expect(height).toBe(texture.height)
})
