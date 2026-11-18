# =============================================================================
# Dockerfile Multi-Stage — Frontend Dashboard (React 19 + Vite + Nginx 1.27)
# =============================================================================

# -----------------------------------------------------------------------------
# Etapa 1: Construcción estática con Node.js 22 Alpine
# -----------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

# Instalar dependencias de forma determinista aprovechando la caché de Docker
COPY package*.json ./
RUN npm ci

# Copiar código fuente del frontend y generar el bundle de producción en /app/dist
COPY . .
RUN npm run build

# -----------------------------------------------------------------------------
# Etapa 2: Servidor web ligero Nginx 1.27 Alpine con Reverse Proxy y Hardening
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# Copiar configuración endurecida de Nginx y artefactos estáticos compilados
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
