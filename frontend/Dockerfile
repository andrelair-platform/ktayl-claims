# Angular adjuster workbench (ADR-009) — SPA served by nginx, which also BFF-proxies /api → the
# internal ACL Service (API_URL, injected at runtime via nginx envsubst → one env-agnostic image).

# Stage 1 — build the Angular app
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2 — nginx (unprivileged: non-root, listens 8080) serves the static app + the BFF proxy
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runner
# Patch base-image OS CVEs (e.g. CVE-2026-31789 in openssl libcrypto3/libssl3) — the Trivy scan-before-push
# gate blocks the image otherwise. Upgrade as root, then drop back to the non-root nginx uid (101).
USER root
RUN apk --no-cache upgrade
USER 101
# the app builder emits to dist/<project>/browser
COPY --from=build /app/dist/claims-workbench/browser /usr/share/nginx/html
# template → envsubst(${API_URL}) → /etc/nginx/conf.d/default.conf at container start
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV API_URL=http://ktayl-claims
EXPOSE 8080
