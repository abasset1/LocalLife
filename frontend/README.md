# Frontend LocalLife

Application React + TypeScript construite avec Vite. Elle affiche une carte
Leaflet centrée sur Marseille. Les activités retournées par l'API y sont
affichées sous forme de marqueurs.

## Démarrage

```powershell
npm install
npm run dev
```

L'application est disponible sur `http://localhost:5173`.

Pour afficher les activités, le backend doit être lancé sur le port `8080`.
Le serveur de développement Vite redirige les requêtes `/api` vers ce backend.

## Vérification

```powershell
npm run build
```
