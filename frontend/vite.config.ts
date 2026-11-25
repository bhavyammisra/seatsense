import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The Spring Boot backend runs on 8080. The dev server proxies API and
// WebSocket traffic so the frontend can call relative URLs.
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
