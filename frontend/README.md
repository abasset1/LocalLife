# Frontend LocalLife

Application React + TypeScript construite avec Vite. Elle affiche une carte
Leaflet centrée sur Marseille. Les activités retournées par l'API y sont
affichées sous forme de marqueurs, regroupés en clusters au-delà d'une
certaine densité (`react-leaflet-cluster`, LL-8004). Le popup affiché au
clic sur un marqueur indique le titre, la catégorie, la date, le lieu
(coordonnées) et la source de l'activité (LL-8006).

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
