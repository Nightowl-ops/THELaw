# ==========================================
# Stage 1: Build React + Vite Static Assets
# ==========================================
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies first for Docker layer caching
COPY package*.json ./
RUN npm ci

# Copy application source and build production bundle
COPY . .
RUN npm run build

# ==========================================
# Stage 2: Serve via Nginx & Proxy /api to API Gateway
# ==========================================
FROM nginx:alpine

# Copy custom Nginx configuration (SPA routing + /api proxy to api-gateway:8080)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled static assets from Stage 1
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
