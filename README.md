# LocalLife

Plateforme locale d'activités, avec une API Spring Boot et une carte web React.

## Fonctionnalités (Sprint 1)

* Carte interactive (affichage, zoom, déplacement) centrée sur Marseille.
* Affichage des activités de démonstration sous forme de marqueurs.
* Popup au clic sur un marqueur : titre, catégorie et date de l'activité.

## Fonctionnalités (Sprint 2)

* Création d'un compte utilisateur (`POST /api/v1/users`) et consultation
  par id (`GET /api/v1/users/{id}`).
* Consultation des catégories (`GET /api/v1/categories`).
* Formulaire de contribution : proposer une activité directement depuis la
  carte (titre, description, catégorie, localisation), envoyée à
  `POST /api/v1/activities` (statut `PENDING` par défaut — modération
  décrite dans la section Sprint 6 ci-dessous).

## Authentification

* Inscription : `POST /api/v1/auth/register` (`username`, `email`,
  `password`) → `201 Created`, retourne l'utilisateur créé (sans le mot de
  passe). Le rôle `USER` est attribué par défaut.
* Connexion : `POST /api/v1/auth/login` (`email`, `password`) →
  `200 OK`, retourne un token JWT (`{ "token": "..." }`), valable 24h.
* Le token doit être envoyé dans l'en-tête `Authorization: Bearer <token>`
  pour accéder aux endpoints protégés :
  * `POST /api/v1/activities` — tout utilisateur authentifié.
  * `POST /api/v1/users` — réservé au rôle `ADMIN`.
* Sans JWT valide, ces endpoints renvoient `401 Unauthorized` ; avec un
  rôle insuffisant, `403 Forbidden`.
* Frontend : pages `/login` et `/register`, en-tête affichant l'utilisateur
  connecté (email) avec un bouton de déconnexion, et le formulaire de
  contribution qui envoie automatiquement le JWT stocké.

## Géocodage

* Le formulaire de contribution (`POST /api/v1/activities`) ne demande
  qu'une **adresse** (texte libre), plus de latitude/longitude.
* Le backend convertit cette adresse en coordonnées via l'API publique
  [Nominatim](https://nominatim.openstreetmap.org) (OpenStreetMap) et ne
  conserve que les coordonnées obtenues — l'adresse saisie n'est pas
  stockée en base.
* Erreurs possibles : `400 Bad Request` (adresse vide ou introuvable),
  `503 Service Unavailable` (service de géocodage injoignable).

## Recherche géographique

* Recherche par rayon : `GET /api/v1/activities/nearby?latitude=...&longitude=...&radius=...`
  (`radius` en kilomètres, max 50), triée par distance croissante.
* Recherche par zone rectangulaire (typiquement la zone visible sur la
  carte) : `GET /api/v1/activities/within-bounds?swLatitude=...&swLongitude=...&neLatitude=...&neLongitude=...`,
  triée par id croissant (pas de point de référence pour une distance).
* Filtres optionnels, communs aux deux endpoints : `category`
  (une ou plusieurs valeurs séparées par des virgules), `date` (format
  ISO-8601 `yyyy-MM-dd` — une activité est retenue quand cette date
  tombe dans sa période `startDate`/`endDate`). Depuis le Sprint 6, ces
  deux endpoints ne retournent que les activités `PUBLISHED` — `status`
  n'est plus un paramètre exposé (voir plus bas).
* Détail des contrats :
  [`docs/02_Architecture/GEO_SEARCH_CONTRACT.md`](docs/02_Architecture/GEO_SEARCH_CONTRACT.md)
  et
  [`docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md`](docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md).
* Frontend :
  * Filtres catégorie et date au-dessus de la carte, combinables.
  * Bouton « Utiliser ma position » (géolocalisation navigateur,
    permission demandée explicitement au clic, aucune position stockée
    côté serveur).
  * La carte recharge automatiquement les activités après un
    déplacement ou un changement de zoom significatif (recherche par
    zone plutôt que par rayon fixe dès la première interaction avec la
    carte).
  * États affichés à l'utilisateur : chargement, résultats, aucun
    résultat, erreur.

## Sprint 5 — Alimentation réelle

* Les activités peuvent désormais provenir d'une source externe en plus
  de la contribution manuelle, via un pipeline d'import : collecte →
  déduplication → normalisation/validation → persistance →
  journalisation.
* Premier collecteur réel : [OpenAgenda](https://developers.openagenda.com/)
  (`OPENAGENDA_API_KEY`/`OPENAGENDA_AGENDA_UID` à configurer, voir
  [`docs/02_Architecture/COLLECTOR_OPERATIONS.md`](docs/02_Architecture/COLLECTOR_OPERATIONS.md)).
* Toute activité est rattachée à une `Source` (source réservée `MANUAL`
  pour les contributions manuelles, source externe pour les imports) —
  transparent pour la recherche/les filtres/la carte, une activité
  importée est consultable exactement comme une activité manuelle.
* Une activité déjà importée mais absente d'une collecte plus récente
  est archivée (`status = "ARCHIVED"`), jamais supprimée.
* Deux façons de déclencher l'import : automatiquement, toutes les
  heures (`ImportScheduler`, LL-8005), ou manuellement via
  `POST /api/v1/admin/import` (rôle `ADMIN`) — voir la section
  « Déclenchement d'un import » plus bas et
  [`docs/02_Architecture/COLLECTOR_OPERATIONS.md`](docs/02_Architecture/COLLECTOR_OPERATIONS.md).

## Sprint 6 — Qualité des données et administration minimale

* **Validation renforcée** : `title` obligatoire (≤ 255 caractères),
  `url` conservée (perdue à tort par la normalisation avant ce
  sprint), migration `V10` (colonne `url`).
* **Statut de modération** : chaque activité a désormais un statut
  (`PENDING`/`PUBLISHED`/`REJECTED`, contrainte `CHECK` en base,
  migration `V11`). Seules les activités `PUBLISHED` sont retournées
  par `GET /api/v1/activities/nearby` et `/within-bounds` — `status`
  n'est plus un paramètre exposé par ces endpoints publics.
* **Contrôle administratif minimal** (rôle `ADMIN` requis) :
  * `GET /api/v1/admin/activities?status=...` — consultation par
    statut (ex. la file d'attente `PENDING`).
  * `PATCH /api/v1/admin/activities/{id}/publish` et
    `PATCH /api/v1/admin/activities/{id}/reject` — publier ou rejeter
    une activité `PENDING`.
* **Source identifiable** : `GET /api/v1/sources` et
  `GET /api/v1/sources/{id}` (public) permettent de résoudre le
  `sourceId` porté par une activité en un nom/type lisible (ex.
  `OpenAgenda` vs `Saisie manuelle`).
* **Premier jalon Food Truck** : nouveau module indépendant
  `foodtruck` (voir
  [`docs/02_Architecture/FOOD_TRUCK_CONTRACT.md`](docs/02_Architecture/FOOD_TRUCK_CONTRACT.md)),
  affiché sur la même carte que les activités avec une icône dédiée.
  * `GET /api/v1/foodtrucks` (public) — food trucks `PUBLISHED`
    (statut par défaut à la création, aucune modération food truck à
    ce stade).
  * `POST /api/v1/foodtrucks` — utilisateur connecté requis, même
    posture que `POST /api/v1/activities`.
* **Tests de non-régression** : suite dédiée vérifiant explicitement
  la visibilité publique par statut, le contrôle d'accès
  administrateur/utilisateur standard, la visibilité des food trucks,
  et que la recherche géographique reste inchangée.

## Démarrage

La base de données doit être démarrée avant le backend :

```powershell
cd infra
docker compose up -d

cd ..\backend
mvn spring-boot:run
```

Dans un second terminal, démarrer le frontend :

```powershell
cd frontend
npm install
npm run dev
```

Le frontend est accessible sur `http://localhost:5173` et le backend sur
`http://localhost:8080`.

## Sprint 7 — Démonstration du MVP (LL-7008)

Le MVP a été validé à l'issue du Sprint 7. Le guide ci-dessous reste la
procédure de référence pour la démonstration de la baseline, mise à
jour au fil du Sprint 8 (bootstrap admin, import automatique, popup
enrichi — voir la section « Sprint 8 » plus bas pour l'état complet).

Parcours complet pour reproduire une démonstration à partir d'un
environnement neuf, sans connaissance préalable du développement.

### 1. Base de données

```powershell
cd infra
docker compose up -d
```

Démarre PostgreSQL/PostGIS (`infra/docker-compose.yml`) : base
`locallife`, utilisateur/mot de passe `locallife`, port `5432`. Les
migrations Flyway (`backend/src/main/resources/db/migration`) sont
appliquées automatiquement au démarrage du backend, aucune action
manuelle requise.

### 2. Configuration

Deux fichiers de variables d'environnement à préparer, en s'appuyant
sur `backend/.env.example` :

* `JWT_SECRET` — obligatoire. Générer une valeur avec
  `openssl rand -base64 32` (une valeur de secours existe pour le
  développement, mais explicite, pas pour une démonstration).
* `OPENAGENDA_API_KEY` / `OPENAGENDA_AGENDA_UID` — obligatoires
  uniquement si l'étape 5 (import réel) doit utiliser de vraies
  données OpenAgenda plutôt que des activités créées manuellement.
  Voir `docs/02_Architecture/COLLECTOR_OPERATIONS.md` pour le détail
  de ces variables.

### 3. Démarrage backend/frontend

Voir la section « Démarrage » ci-dessus. Vérifier que le backend
répond : `GET http://localhost:8080/actuator/health` doit renvoyer
`{"status":"UP"}`.

### 4. Compte administrateur de démonstration

Depuis LL-8002 (Sprint 8), le premier compte `ADMIN` est créé
automatiquement au **premier démarrage** du backend sur une base
neuve, à partir de variables d'environnement — plus besoin de
promotion SQL manuelle. Aucun compte `ADMIN` n'est créé par défaut :
sans ces variables, le bootstrap est simplement ignoré (log
`Bootstrap admin non configuré ... : ignoré.`).

```powershell
# À définir AVANT de lancer `mvn spring-boot:run`, dans le même terminal
$env:LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL = "demo-admin@example.com"
$env:LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD = "changez-moi-en-production"
# Optionnel, "admin" par défaut :
$env:LOCALLIFE_BOOTSTRAP_ADMIN_USERNAME = "demo-admin"

cd backend
mvn spring-boot:run
```

Puis se connecter normalement pour récupérer le JWT :

```powershell
curl -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"demo-admin@example.com","password":"changez-moi-en-production"}'
```

**Comportement de sécurité important** : ce bootstrap ne se déclenche
que si **aucun** compte `ADMIN` n'existe déjà en base — sur une base
déjà initialisée (ou lors d'un redémarrage suivant), il est ignoré,
même si les variables d'environnement restent définies. Il ne permet
jamais d'élever un compte `USER` existant, ni de créer un second
administrateur. Une fois le premier `ADMIN` créé, il est recommandé
de retirer ces variables d'environnement du terminal.

### 5. Déclenchement d'un import

Deux options, non exclusives :

* **Automatique** : depuis LL-8005, `ImportScheduler` déclenche
  `ImportService#importAll()` toutes les heures (à la minute 0), sans
  action manuelle — utile pour une démonstration longue, mais pas
  pour obtenir des données immédiatement après un démarrage.
* **Manuel**, avec le JWT obtenu à l'étape précédente (rôle `ADMIN`
  requis, `POST /api/v1/admin/import`, voir `AdminImportController`) :

```powershell
curl -X POST http://localhost:8080/api/v1/admin/import `
  -H "Authorization: Bearer <token>"
```

Répond `200` avec un `ImportResult` par collecteur enregistré
(`fetched`/`created`/`updated`/`ignored`/`errors`/`archived`). Si
`OPENAGENDA_API_KEY`/`OPENAGENDA_AGENDA_UID` ne sont pas configurées,
l'import se termine quand même avec un résultat dégradé
(`errors = 1`) plutôt qu'une erreur HTTP — voir
`docs/02_Architecture/COLLECTOR_OPERATIONS.md`. Sans configuration
OpenAgenda, la carte peut aussi être démontrée avec une activité créée
manuellement via le formulaire de contribution du frontend, publiée
ensuite par l'administrateur (`PATCH /api/v1/admin/activities/{id}/publish`).

### 6. Vérification de la carte

Ouvrir `http://localhost:5173` : les activités importées ou publiées
doivent apparaître comme marqueurs sur la carte (regroupés en clusters
si nombreux, voir `react-leaflet-cluster`), avec popup au clic
(titre, catégorie, date, lieu — coordonnées géographiques — et source).
Le filtre catégorie/date et le bouton « Utiliser ma position »
permettent de vérifier la recherche géographique (voir la section
« Recherche géographique » ci-dessus).

## Sprint 8 — Préparation de la bêta

Sprint en cours, voir `docs/05_Sprints/SPRINT_8.md` pour le détail des
tickets et `docs/PROJECT_STATUS.md` pour le suivi et les preuves.
Résumé de l'avancement (25 août 2026) :

* **LL-8001** — baseline rejouée après les corrections du Sprint 7,
  aucune régression, baseline figée.
* **LL-8002** — premier compte `ADMIN` bootstrapé automatiquement (voir
  section 4 ci-dessus).
* **LL-8003** — exceptions serveur non gérées désormais journalisées
  (niveau `ERROR`, sans donnée sensible) par `GlobalExceptionHandler`.
* **LL-8004** — pagination complète de l'API OpenAgenda (au-delà des 20
  premiers résultats par défaut) ; agendas Avignon configurés en
  propriétés, mais réellement câblés (plusieurs collecteurs enregistrés
  simultanément) seulement depuis la correction apportée par LL-8009
  (voir ci-dessous — écart trouvé lors de la vérification finale).
* **LL-8005** — import automatique planifié (voir section 5
  ci-dessus).
* **LL-8006** — affichage de bout en bout vérifié sur la carte ; popup
  enrichi (lieu, source).
* **LL-8007** — dette technique pertinente pour une bêta traitée à la
  date du ticket (nouvelle entrée ouverte depuis, voir LL-8009).
* **LL-8008** — documentation consolidée.
* **LL-8009** — décision **GO bêta conditionnel** : écart trouvé et
  corrigé (agendas Avignon jamais réellement câblés malgré les
  propriétés déjà présentes), un seul agenda Avignon réel reste actif
  (Culture) en attendant qu'Alex en identifie d'autres — voir
  `docs/PROJECT_STATUS.md` et `docs/DETTE_TECHNIQUE.md` pour le détail
  et les conditions avant ouverture effective.

## Sprint 10 — Adresses, villes et liste des activités

> Note : le Sprint 9 (corrections post-bêta) et le Sprint Évol-Fix
> (`docs/05_Sprints/SPRINT_EVOL_FIX.md`), tous deux menés en parallèle
> du Sprint 10, ne sont pas résumés dans ce README — écart de
> documentation antérieur à ce sprint, signalé dans
> `docs/NEXT_TASK.md`, non rattrapé ici (hors périmètre de LL-10010,
> qui porte sur le Sprint 10).

* **Localisation structurée** — une activité expose désormais `address`,
  `postalCode` et `city` en plus de `latitude`/`longitude`, chacun
  individuellement nullable (jamais de valeur déduite : `city` n'est
  jamais construite à partir de `address`). Contrat complet, y compris
  le comportement des champs absents et l'état d'implémentation par
  endpoint :
  [`docs/02_Architecture/LOCATION_CONTRACT.md`](docs/02_Architecture/LOCATION_CONTRACT.md).
* **Filtre et tri par ville** — `GET /api/v1/activities` (le listing
  simple, pas `nearby`/`within-bounds`) accepte deux paramètres
  optionnels : `city` (filtre exact, insensible à la casse) et `sort`
  (une ou plusieurs clés parmi `city`/`date`, séparées par une
  virgule, ex. `sort=city,date`). Filtrage et tri réalisés côté base
  de données ; sans ces deux paramètres, le comportement historique de
  l'endpoint est strictement inchangé. Détail :
  [`docs/02_Architecture/LOCATION_CONTRACT.md`](docs/02_Architecture/LOCATION_CONTRACT.md#filtre-city-et-tri-sort-ll-10006).
* **Vue liste** — bouton Carte/Liste dans le bandeau de filtres ; la
  liste affiche titre, date, ville, adresse et catégorie, regroupée
  par ville par défaut ou triée explicitement (ville et/ou date) ;
  clic sur une activité → même modale de détail que depuis la carte.
  Carte et liste partagent une seule source de données filtrée/triée
  (`visibleActivities`), sans appel réseau supplémentaire au
  changement de vue ou de filtre ville/tri — voir
  `docs/02_Architecture/LL-10009_VALIDATION_CARTE_LISTE_DETAIL.md`
  pour la vérification détaillée de cette cohérence carte/liste.
* Filtres ville/tri appliqués côté client (pas via `GET
  /api/v1/activities?city=...&sort=...`) : `GET /api/v1/activities`
  ne restreint aucun statut (contrairement à `nearby`/`within-bounds`,
  limités à `PUBLISHED` depuis le Sprint 6), l'utiliser depuis la
  recherche géographique existante aurait exposé des activités
  `PENDING`/`REJECTED` — décision documentée dans `docs/NEXT_TASK.md`.
* ⚠️ **`mvn verify` non exécuté par les sessions ayant traité ce
  sprint** — accès réseau restreint (Maven Central hors des domaines
  autorisés en sandbox). À lancer avant tout merge en production, avec
  une base PostgreSQL réelle.
