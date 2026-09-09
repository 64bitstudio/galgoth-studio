import { defineConfig } from '@playwright/test'

/**
 * Ticket 033 (HU-23) -- suite de aceptación E2E. Corre contra un
 * backend/frontend REALES (Postgres+MinIO vía Docker Compose, backend
 * con `AI_VISION_PROVIDER=mock`/`AI_REASONING_PROVIDER=mock`, ver
 * `scripts/e2e.sh`), nunca contra mocks de red del lado del navegador --
 * eso ya lo hacen los tests Vitest de cada componente. `webServer` NO
 * se usa acá: además del frontend hacen falta Postgres/MinIO/backend, 3
 * procesos más que Playwright no orquesta bien con una sola entrada de
 * `webServer` -- `scripts/e2e.sh` levanta todo el stack antes de invocar
 * `playwright test` y lo apaga siempre al salir (incluso si algo falla).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
})
