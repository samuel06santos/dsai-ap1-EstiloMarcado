# Imagem de producao do frontend (Estilo Marcado).
# Build multi-stage: compila o Angular com Node e serve os arquivos estaticos
# com o nginx nao privilegiado (uid 101, porta 8080).

# --- Estagio 1: build -------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /build

COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build -- --configuration production

# --- Estagio 2: execucao ----------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

# Configuracao interna: SPA + proxy de /api e /actuator para o backend.
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

# Artefato do Angular (application builder: dist/<projeto>/browser).
COPY --from=build /build/dist/estilo-marcado-web/browser /usr/share/nginx/html

EXPOSE 8080
