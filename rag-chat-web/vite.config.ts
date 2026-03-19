import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }

          if (
            id.includes('node_modules/vue')
            || id.includes('node_modules/vue-router')
            || id.includes('node_modules/pinia')
          ) {
            return 'framework'
          }

          if (
            id.includes('node_modules/element-plus')
            || id.includes('node_modules/@element-plus')
          ) {
            return 'ui'
          }

          if (
            id.includes('node_modules/axios')
            || id.includes('node_modules/@microsoft/fetch-event-source')
          ) {
            return 'http'
          }

          return undefined
        },
      },
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:9212',
        changeOrigin: true,
      },
    },
  },
})
