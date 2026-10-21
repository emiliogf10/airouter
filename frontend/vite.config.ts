/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/**
 * Configuración principal de Vite y Vitest para el Dashboard de AI Cost Router.
 * Configura el servidor de desarrollo en el puerto 5173 con proxy hacia el backend
 * Spring Boot en http://localhost:8080 y el entorno jsdom para pruebas unitarias.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
});
