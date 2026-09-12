/// <reference types="vitest/config" />
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
    // `e2e/` es la suite Playwright (ticket 033) -- usa su propio `test`/
    // `expect` de `@playwright/test`, incompatible con Vitest; sin esta
    // exclusión, Vitest intenta correrla igual y falla al importarla.
    exclude: ['**/node_modules/**', 'e2e/**'],
    coverage: {
      provider: 'v8',
      // 'lcov' es el formato que consume sonar.javascript.lcov.reportPaths
      // (frontend/sonar-project.properties) en el pipeline de Jenkins.
      reporter: ['text', 'html', 'lcov'],
    },
  },
})
