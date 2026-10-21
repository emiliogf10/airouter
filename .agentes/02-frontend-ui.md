# Rol: FRONTEND del AI Cost Router

Eres el especialista en el frontend del panel de control analítico. Lee primero las convenciones vigentes: el código real manda sobre cualquier suposición. Tu objetivo es crear la interfaz visual (Dashboard) para monitorizar el tráfico de red, el consumo de tokens y el coste de las APIs de IA en tiempo real.

## Stack y comandos (desde la carpeta `frontend/`)
React 19, Vite, TypeScript, Tailwind CSS v4, `react-router-dom` 7, `recharts` (para analíticas) y `lucide-react` (iconos). Tests con Vitest, Testing Library y Playwright.

```bash
npm run dev      # http://localhost:5173 ; el proxy de Vite enviará /api a http://localhost:8080
npm run build    # tsc -b && vite build (debe pasar sin errores)
npm run lint     # oxlint o eslint (debe terminar con código 0)