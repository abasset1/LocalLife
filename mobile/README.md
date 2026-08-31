# LocalLife — Mobile

Client mobile (Android/iOS) de LocalLife. Ce n'est **pas** une nouvelle
version du produit : c'est un nouveau client du même backend Spring Boot
déjà utilisé par le frontend web (`frontend/`).

Voir `docs/05_Sprints/SPRINT_MOBILE.md` pour le périmètre complet du sprint
mobile.

## Stack

- [Expo](https://expo.dev) (React Native)
- TypeScript
- [Expo Router](https://docs.expo.dev/router/introduction/) (navigation par fichiers, dossier `src/app/`)

## Prérequis

- Node.js ≥ 20 (comme `frontend/`)
- npm
- Pour tester sur téléphone : l'app [Expo Go](https://expo.dev/go), ou un
  émulateur Android / simulateur iOS

## Lancer le projet en local

```bash
cd mobile
npm install
cp .env.example .env   # puis ajuster EXPO_PUBLIC_API_URL si besoin
npm start
```

Le terminal propose ensuite d'ouvrir l'app sur Android, iOS ou dans Expo Go
(scanner le QR code).

Scripts disponibles :

- `npm start` — démarre le serveur de développement Expo
- `npm run android` — démarre et ouvre sur un émulateur/appareil Android
- `npm run ios` — démarre et ouvre sur un simulateur/appareil iOS
- `npm run web` — démarre la version web (utile pour un aperçu rapide, pas
  une cible du sprint)
- `npm run lint` — lint du projet

## Configuration de l'URL de l'API

L'URL de l'API backend n'est **jamais** codée en dur dans les écrans. Elle
est définie par la variable d'environnement `EXPO_PUBLIC_API_URL` (fichier
`.env`, non commité — voir `.env.example`) et exposée à l'application via
`app.config.ts` puis `src/config/env.ts`.

⚠️ Si vous testez sur un appareil physique (pas un émulateur), `localhost`
pointe vers le téléphone lui-même, pas vers votre machine de développement.
Utilisez l'adresse IP de votre machine sur le réseau local, ou l'URL de
l'API bêta.

Aucun secret (clé API, mot de passe, etc.) ne doit être ajouté dans ce
projet mobile : l'authentification aux services tiers reste gérée côté
backend.

## Structure du projet

```text
mobile/
├── app.config.ts       # config Expo + lecture de EXPO_PUBLIC_API_URL
├── .env.example         # template de configuration (à copier en .env)
├── src/
│   ├── app/              # écrans et navigation (Expo Router)
│   ├── components/       # composants UI réutilisables
│   ├── constants/        # constantes (thème, etc.)
│   ├── config/           # configuration applicative (env.ts)
│   └── hooks/             # hooks React réutilisables
└── assets/               # images, icônes, splash screen
```

Cette structure sera enrichie au fil des tickets du sprint mobile
(`docs/05_Sprints/SPRINT_MOBILE.md`), notamment `src/features/` pour le
découpage par fonctionnalité prévu par LL-MOB-0001, et un client API dédié
en LL-MOB-0003.

## État actuel (LL-MOB-0001)

- [x] Projet Expo + TypeScript initialisé, versionné dans le dépôt
- [x] Expo Router en place (navigation par fichiers)
- [x] URL de l'API configurable par environnement, aucun secret embarqué
- [ ] Navigation adaptée aux usages mobiles (LL-MOB-0002)
- [ ] Client API centralisé (LL-MOB-0003)

L'écran de démarrage actuel est celui du template Expo par défaut ; il sera
remplacé par la navigation cible lors de LL-MOB-0002.
