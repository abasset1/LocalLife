# --- Étape de build ---
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Connexion Google (LL-EF-011) : identifiant client OAuth public, figé dans
# le bundle par Vite au moment du build (pas lisible au démarrage du
# conteneur). Vide : le bouton Google n'est pas affiché.
ARG VITE_GOOGLE_CLIENT_ID=""
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
RUN npm run build

# --- Étape de service ---
FROM nginx:1.27-alpine
# Repli SPA sur index.html (sinon 404 sur les liens /verify-email, etc.).
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
