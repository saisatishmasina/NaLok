import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev-only proxy: the desktop app will talk to the same-origin Spring server
// later, so no CORS is ever needed. /api is forwarded to the backend on 8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
