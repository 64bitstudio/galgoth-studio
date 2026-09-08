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
    coverage: {
      provider: 'v8',
      // 'lcov' es el formato que consume sonar.javascript.lcov.reportPaths
      // (frontend/sonar-project.properties) en el pipeline de Jenkins.
      reporter: ['text', 'html', 'lcov'],
    },
  },
})
