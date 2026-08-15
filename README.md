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
  `POST /api/v1/activities` (statut `PENDING` par défaut — pas de
  modération à ce stade).

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
* Filtres optionnels, communs aux deux endpoints : `status`, `category`
  (une ou plusieurs valeurs séparées par des virgules), `date` (format
  ISO-8601 `yyyy-MM-dd` — une activité est retenue quand cette date
  tombe dans sa période `startDate`/`endDate`).
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
* ⚠️ Aucun déclencheur automatique n'existe encore (pas de tâche
  planifiée, pas d'endpoint) — voir
  [`docs/02_Architecture/COLLECTOR_OPERATIONS.md`](docs/02_Architecture/COLLECTOR_OPERATIONS.md).

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
