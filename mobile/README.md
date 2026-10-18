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
- `npm run typecheck` — vérification TypeScript (`tsc --noEmit`)
- `npm test` — tests unitaires (Jest, fichiers `__tests__/`)

Ces trois commandes tournent en CI à chaque modification de `mobile/`
(`.github/workflows/mobile-ci.yml`).

## Connexion Google

Le bouton « Continuer avec Google » (rubrique Compte) utilise la connexion
native de Google (`@react-native-google-signin/google-signin`). Il **ne
fonctionne pas dans Expo Go** : il faut un build (`npx expo run:android`).
Il reste masqué tant que les identifiants ci-dessous ne sont pas fournis.

Le téléphone demande à Google un ID token destiné au **client OAuth « web »**
du site, celui que vérifie le backend (`GOOGLE_CLIENT_ID`) : aucun réglage
backend en plus. Dans la console Google Cloud, même projet que le site :

1. **Android** : créer un identifiant OAuth de type *Android*, nom de package
   `com.locallife.mobile`, empreinte SHA-1 de la clé qui signe l'APK. Pour un
   build local (signé avec la clé de debug du projet Android généré) :
   `keytool -list -v -keystore android/app/debug.keystore -alias androiddebugkey -storepass android -keypass android`
   (depuis `mobile/`, après un premier `npx expo prebuild`).
2. **iOS** (facultatif) : créer un identifiant OAuth de type *iOS*, ID de
   bundle `com.locallife.mobile`.
3. Dans `mobile/.env` :
   ```
   EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID=<client web, même valeur que GOOGLE_CLIENT_ID du backend>
   EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID=<client iOS, si besoin>
   ```
4. Regénérer le projet natif : `npx expo prebuild --clean`, puis
   `npx expo run:android`.

L'identifiant Android n'est jamais lu par l'application : Google le
retrouve grâce au nom de package et à l'empreinte SHA-1. Une erreur
`DEVELOPER_ERROR` signifie presque toujours une empreinte ou un nom de
package qui ne correspond pas.

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
│   ├── app/                # écrans et navigation (Expo Router)
│   │   ├── _layout.tsx       # stack racine : (tabs) + détail activité
│   │   ├── (tabs)/            # écrans principaux (menu rétractable, sans barre d'onglets)
│   │   │   ├── index.tsx        # Découvrir
│   │   │   ├── contribution.tsx # Contribution
│   │   │   └── compte.tsx       # Compte
│   │   └── activite/[id].tsx  # détail d'une activité (écran empilé)
│   ├── api/              # client API LocalLife (LL-MOB-0003)
│   ├── auth/             # session, JWT, stockage sécurisé (LL-MOB-0004)
│   ├── components/       # composants UI réutilisables
│   ├── constants/        # constantes (thème, etc.)
│   ├── features/         # fonctionnalités (map/ : carte, LL-MOB-0005)
│   ├── config/           # configuration applicative (env.ts)
│   └── hooks/             # hooks React réutilisables
└── assets/               # images, icônes, splash screen
```

Cette structure sera enrichie au fil des tickets du sprint mobile
(`docs/05_Sprints/SPRINT_MOBILE.md`), notamment `src/features/` pour le
découpage par fonctionnalité (première fonctionnalité : `map/`).

## Navigation (LL-MOB-0002, FRONT-REV-02)

4 écrans principaux : **Carte**, **Liste**, **Contribuer**, **Compte**. Depuis
FRONT-REV-02, il n'y a plus de barre d'onglets : un **menu rétractable**
(bouton « ☰ » en haut de chaque écran principal, `src/features/navigation/`)
ouvre un panneau latéral avec toutes les rubriques (Carte, Liste, Proposer une
activité, Compte et, connecté, Mes favoris, Mes contributions, Mes alertes,
Paramètres de recherche). Il se ferme par « ✕ », un appui hors du panneau, le
bouton retour d'Android ou le choix d'une rubrique. La carte et la liste ont en
plus un bouton direct « Liste » / « Carte ».

Le navigateur `Tabs` d'`expo-router` est conservé, sans sa barre
(`tabBarStyle: { display: 'none' }`), pour que chaque vue garde son état
(position de la carte, défilement de la liste) quand on en change.

Le détail d'une activité s'atteint depuis la carte ou la liste via un écran
empilé (`src/app/activite/[id].tsx`), avec un header natif portant le bouton
retour. Le geste/bouton retour Android est géré nativement par le stack
navigator, sans code additionnel.

## Explorateur : ville, catégories, critères partagés (FRONT-REV-02)

- **Recherche de ville** (`src/features/explorer/CitySearch.tsx`), sur la carte
  et dans la liste : communes proposées pendant la saisie par
  `GET /api/v1/geocoding/city-suggestions` (public, sans compte, même source
  que le site web). Chargement, panne (« Réessayer ») et commune introuvable
  sont annoncés. La commune choisie devient le lieu de recherche ; « ✕ » le
  retire.
- **Catégories** (`CategoryPanel.tsx`) : une ou plusieurs catégories du
  référentiel, cochées ou décochées, « Toutes les catégories » pour retirer le
  filtre.
- **Critères partagés** (`explorerStore.ts`) : lieu, catégories et rayon sont
  communs à la carte et à la liste, et conservés quand on change de vue (la
  période l'était déjà, `periodStore.ts`). Les paramètres de recherche de
  l'utilisateur connecté les initialisent à chaque changement de ces
  paramètres, sans écraser un choix fait entre-temps.
- **Pas de ville supposée** : sans lieu choisi, la liste invite à en choisir un
  (aucune requête) et la carte signale qu'elle montre une vue d'ensemble.

## Client API (LL-MOB-0003)

Toute communication avec le backend passe par `src/api/`, importé
uniquement via `@/api`. Les écrans ne construisent jamais d'URL ni
n'appellent `fetch` directement ; l'URL de base vient de
`src/config/env.ts` (voir plus haut).

| Module | Endpoints |
| --- | --- |
| `activities.ts` | `GET /activities`, `/activities/nearby`, `/activities/within-bounds`, `/activities/{id}`, `POST /activities` (contribution) |
| `categories.ts` | `GET /categories` |
| `foodTrucks.ts` | `GET /foodtrucks` |
| `auth.ts` | `POST /auth/register`, `POST /auth/login` |

- **Réponses typées** : `src/api/types.ts` reprend les DTO du backend.
  `GET /activities/{id}` renvoie la fiche détaillée ou un résumé selon
  les droits de l'appelant : utiliser `isActivityDetail()` pour les
  distinguer.
- **JWT** : le client ne stocke rien. La couche d'authentification
  (LL-MOB-0004) l'alimente via `configureApiClient({ getToken,
  onUnauthorized })`. Le token n'est joint qu'aux appels protégés
  (`createActivity`) ; il n'est **jamais** envoyé sur les endpoints
  publics, car le `JwtFilter` du backend renvoie 401 sur un token
  expiré même sur une route publique (même règle que
  `frontend/src/api/apiClient.ts`). Exception : `getActivity`, dont le
  niveau de détail dépend de l'appelant, joint le token s'il y en a un
  (`authenticated: 'optional'`) et rejoue la requête sans lui si le
  backend le refuse.
- **Erreurs** (`src/api/errors.ts`), toutes des `ApiError` : `NetworkError`
  (serveur injoignable), `TimeoutError` (15 s par défaut),
  `RequestAbortedError` (annulation par l'appelant via `signal`, à
  ignorer), `HttpError` (statut + `message` du backend, avec
  `isUnauthorized` / `isForbidden` / `isNotFound` / `isBadRequest`),
  `ResponseFormatError`. Au login, 401 = identifiants refusés et 403 =
  e-mail non vérifié.
- Chaque fonction accepte `{ signal }` (`AbortController`) pour annuler
  une requête, par exemple lors d'un déplacement de carte.

## Authentification (LL-MOB-0004)

Écran **Compte** : inscription, connexion, déconnexion. Les endpoints
sont ceux du backend existant (`POST /auth/register`, `POST /auth/login`) ;
les règles de validation (mot de passe, format d'e-mail…) restent côté
backend, l'écran n'affiche que ses messages.

- **Stockage** : le JWT est stocké dans le stockage sécurisé de l'OS
  (Keystore / Keychain) via `expo-secure-store`, jamais dans
  `AsyncStorage`. Le mot de passe n'est jamais stocké. Sur le web (hors
  cible du sprint), le token reste en mémoire uniquement.
- **Session** : `AuthProvider` (`src/auth/`) restaure le token au
  démarrage, l'injecte dans le client API (`configureApiClient`) et expose
  `useAuth()` (`state`, `login`, `register`, `logout`).
- **Expiration / invalidation** : un token expiré (claim `exp`) est écarté
  au démarrage, à chaque appel authentifié et au retour de l'app au premier
  plan ; un 401 du backend sur un appel authentifié déconnecte
  l'utilisateur. La lecture de `exp` côté client sert uniquement à éviter
  des appels voués à l'échec : la vérification réelle reste côté backend.
- **Inscription** : ne connecte pas. Le backend exige la confirmation de
  l'adresse e-mail (lien reçu par e-mail) ; tant qu'elle n'est pas faite,
  la connexion renvoie 403 et l'écran affiche le message du backend.
  Le renvoi de l'e-mail de confirmation n'est pas encore proposé dans
  l'application (disponible sur le web).
- **Journalisation** : aucun `console.*` dans le code d'authentification ;
  ni token, ni mot de passe, ni e-mail ne sont écrits dans les logs.

## Carte (LL-MOB-0005)

L'écran **Carte** affiche une carte interactive (`src/features/map/`),
centrée au départ sur Marseille (périmètre du MVP).

- **Bibliothèque** : `react-native-maps` (version imposée par le SDK Expo,
  compatible Expo Go). Apple Plans sur iOS, Google Maps sur Android. Le
  web n'est pas supporté (écran de repli explicatif).
- **Données** : les activités de la zone visible viennent de
  `GET /activities/within-bounds` (uniquement les activités `PUBLISHED`,
  côté backend) ; les food trucks viennent de `GET /foodtrucks`, chargés
  une fois. Les zones envoyées respectent le contrat : `sw < ne`, valeurs
  bornées, pas de passage de l'antiméridien.
- **Nombre d'appels** : la zone n'est relue qu'à la **fin** d'un geste
  (`onRegionChangeComplete`), après une temporisation de 500 ms, avec
  annulation de la requête précédente ; aucun appel si la nouvelle zone
  est déjà couverte par les données chargées (zoom avant, par exemple),
  ni si la zone dépasse 2° de côté (`MAX_SEARCH_SPAN_DEGREES`) : la carte
  affiche alors « Zoomez pour voir les activités ».
- **Lisibilité** : regroupement des activités en clusters
  (`supercluster`, jusqu'au zoom 17) ; un toucher sur un cluster zoome
  dessus. Les food trucks ne sont jamais regroupés et se distinguent par
  leur forme (carré) et leur pictogramme, pas seulement par la couleur.
- **Consultation rapide** : un toucher sur un marqueur ouvre une carte
  d'aperçu (titre, catégorie, lieu, date) ; « Voir la fiche » ouvre
  l'écran de détail existant (contenu réel en LL-MOB-0008).
- **États** : chargement, erreur (avec « Réessayer ») et « Aucune activité
  dans cette zone » sont affichés en surimpression.
- **Clé Google Maps (Android, builds autonomes uniquement)** : à fournir
  via la variable d'environnement `GOOGLE_MAPS_API_KEY` (voir
  `.env.example`), jamais dans le dépôt. Inutile avec Expo Go.

## Géolocalisation (LL-MOB-0006)

Bouton **« Ma position »** sur la carte (`src/features/map/useUserLocation.ts`,
`expo-location`).

- **Permission à la demande** : la permission de localisation (au premier
  plan uniquement) n'est demandée qu'au toucher du bouton, avec un message
  qui explique l'usage (`app.config.ts`). Aucune localisation en arrière-plan.
- **Recherche autour de la position** : la carte est recentrée sur
  l'utilisateur (zoom ~ quartier) ; la zone visible alimente alors la
  recherche `within-bounds` habituelle (mêmes garde-fous : temporisation,
  annulation). Aucun endpoint ni logique de recherche supplémentaire.
- **Vie privée** : la position n'est ni stockée ni envoyée au serveur ;
  seule la zone visible de la carte l'est, comme pour tout déplacement.
- **Refus / indisponibilité / erreur** : message compréhensible en
  bandeau (« Réessayer », « Ouvrir les réglages » si le refus est
  définitif, « Fermer »). La carte reste pleinement utilisable.
- Délai maximal d'obtention de la position : 15 s.
- Sur le web (hors cible du sprint), la carte n'est pas supportée.

## Détail d'une activité (LL-MOB-0008)

Écran `src/app/activite/[id].tsx`, ouvert par « Voir la fiche » depuis
l'aperçu d'un marqueur (`src/features/activity/`).

- **Données** : `GET /activities/{id}` (titre, catégorie, date, lieu,
  description, URL). Selon les droits de l'appelant, l'API renvoie la fiche
  détaillée ou un résumé (`isActivityDetail`) : l'écran affiche ce qui est
  fourni, sans rien inventer.
- **Source** : absente de la réponse détaillée, elle est transmise par
  l'aperçu (reçue de la recherche sur la carte) et affichée si disponible.
- **URL** : ouverte dans le navigateur intégré, uniquement en `http(s)`.
- **États** : chargement ; erreur réseau/serveur avec « Réessayer » ;
  activité inexistante (404), identifiant invalide (400) ou non publiée →
  « Activité indisponible » ; « Retour à la carte » dans tous les cas (en
  plus du retour système Android).

## Food trucks (LL-MOB-0009)

- **Données** : `GET /foodtrucks` (uniquement les food trucks `PUBLISHED`,
  côté backend), chargés une fois avec la carte (LL-MOB-0005).
- **Représentation distincte** : marqueur carré + pictogramme, jamais
  regroupé dans les clusters d'activités.
- **Fiche** : « Voir la fiche du food truck » dans l'aperçu ouvre
  `src/app/foodtruck/[id].tsx` (nom, cuisine, description, contact). L'API
  n'expose pas de lecture par identifiant : la fiche est retrouvée dans la
  liste publiée (`src/features/foodtruck/useFoodTruck.ts`), donc un food
  truck non publié n'est jamais affiché. États : chargement, erreur avec
  « Réessayer », « Food truck indisponible ».
- **Contact** (texte libre côté backend) : lien `http(s)` → navigateur,
  numéro → appel, e-mail → messagerie, sinon affiché tel quel.
- `FoodTruck.category` (type de cuisine) reste un texte descriptif, sans
  lien avec le référentiel des catégories d'activités
  (`docs/CATEGORIES_REFERENCE.md`) ; aucune logique métier food truck n'est
  dupliquée dans le mobile.

## État actuel

- [x] Projet Expo + TypeScript initialisé, versionné dans le dépôt (LL-MOB-0001)
- [x] Navigation par onglets + écran de détail empilé (LL-MOB-0002)
- [x] Client API centralisé (LL-MOB-0003)
- [x] Authentification (LL-MOB-0004)
- [x] Carte mobile (LL-MOB-0005)
- [x] Géolocalisation utilisateur (LL-MOB-0006)
- [x] Détail d'une activité (LL-MOB-0008)
- [x] Food trucks (LL-MOB-0009)

## Notifications push (alertes)

Les alertes (menu → « Mes alertes ») peuvent être envoyées en
notification sur le téléphone, via le service **Expo Push**.

- `EAS_PROJECT_ID` (voir `.env.example`) est requis pour obtenir le jeton
  de notification de l'appareil. Sans lui, l'application fonctionne mais
  la notification d'une alerte ne peut pas être activée (message affiché).
- Les notifications ne fonctionnent **pas dans Expo Go sur Android** (ni sur
  un émulateur) : utiliser un build de développement (`eas build --profile
  development`) sur un vrai téléphone.
- La permission n'est demandée qu'au moment où l'utilisateur active la
  notification d'une alerte. Le jeton est rattaché au compte à chaque
  ouverture de session, et retiré à la déconnexion.
- Toucher une notification ouvre la fiche de l'activité (une seule
  activité) ou la carte (plusieurs).
- Côté backend : `EXPO_ACCESS_TOKEN` seulement si la « sécurité renforcée
  des notifications » est activée sur le projet Expo.

