import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The Spring Boot backend runs on 8080. The dev server proxies API and
// WebSocket traffic so the frontend can call relative URLs.
//
// Phones only allow the camera on https (or localhost). For the demo, point
// SEATSENSE_HTTPS_CERT and SEATSENSE_HTTPS_KEY at a certificate for the laptop's
// address (see README, "Demo on phones") and the dev server serves https.
const cert = process.env.SEATSENSE_HTTPS_CERT
const key = process.env.SEATSENSE_HTTPS_KEY

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    https: cert && key ? { cert: readFileSync(cert), key: readFileSync(key) } : undefined,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
