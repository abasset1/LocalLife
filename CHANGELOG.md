# Changelog

## 0.4.0 - 2026-08-14

### Added

* Recherche géographique par rayon (`GET /api/v1/activities/nearby`,
  PostGIS `ST_DWithin`) et par zone rectangulaire
  (`GET /api/v1/activities/within-bounds`, PostGIS `&&`/
  `ST_MakeEnvelope`), triées respectivement par distance et par id.
* Filtres optionnels combinables sur les deux endpoints : statut,
  catégorie (valeurs multiples séparées par des virgules), date
  (ISO-8601, une activité est retenue si la date tombe dans sa période
  `startDate`/`endDate`).
* Frontend : filtres catégorie et date au-dessus de la carte,
  géolocalisation navigateur (bouton dédié, permission explicite,
  aucune position persistée), rechargement automatique des activités au
  déplacement/zoom de la carte (bascule vers la recherche par zone),
  états chargement/résultats/aucun résultat/erreur explicites.
* Tests d'intégration couvrant les deux modes de recherche, chaque
  filtre isolément, et leur combinaison (sémantique ET).

### Fixed

* `BadSqlGrammarException` sur le filtre par date quand celui-ci n'était
  pas fourni : PostgreSQL ne peut pas déterminer le type d'un paramètre
  utilisé seul dans un test `IS NULL` sans contexte — cast explicite
  `::date` ajouté.

## 0.3.0 - 2026-08-11

### Added

* Authentification par JWT :
  * Inscription (`POST /api/v1/auth/register`) et connexion
    (`POST /api/v1/auth/login`, retourne un token JWT valable 24h).
  * Mots de passe hachés avec BCrypt (`PasswordHashingService`), jamais
    stockés ni renvoyés en clair.
  * Filtre JWT (`JwtFilter`) et configuration Spring Security
    (`SecurityConfig`) : `POST /api/v1/activities` requiert désormais un
    utilisateur authentifié, `POST /api/v1/users` est réservé au rôle
    `ADMIN`. Réponses JSON standardisées en cas de `401`/`403`.
  * Frontend : pages `/login` et `/register`, en-tête affichant
    l'utilisateur connecté avec déconnexion, et envoi automatique du JWT
    sur les appels protégés.
* Géocodage d'adresse (`GeocodingService`, API Nominatim) : le formulaire
  de contribution envoie désormais une adresse texte au lieu de
  latitude/longitude ; le backend géocode côté serveur et ne conserve que
  les coordonnées obtenues.
* Tests d'intégration bout en bout couvrant inscription → connexion →
  accès à un endpoint protégé, création d'activité géocodée, et refus
  d'accès sur JWT expiré ou absent.

## 0.2.0 - 2026-08-08

### Added

* Module User : entité, migration Flyway (table `users`), repository,
  service, API REST (`POST /api/v1/users`, `GET /api/v1/users/{id}`).
* Module Category : entité, migration Flyway (table `category`),
  repository, service, API REST (`GET /api/v1/categories`).
* Endpoint de création d'activité (`POST /api/v1/activities`), ajouté
  hors périmètre initial du Sprint 2 pour débloquer le formulaire de
  contribution.
* Formulaire de contribution côté frontend : proposer une activité
  (titre, description, catégorie, localisation) directement depuis la
  carte.

## 0.1.0 - 2026-08-07

### Added

* API REST de consultation des activités (`GET /api/v1/activities` et
  `GET /api/v1/activities/{id}`).
* Frontend React + TypeScript avec une carte Leaflet centrée sur Marseille,
  zoomable et déplaçable.
* Affichage des activités de l'API sous forme de marqueurs sur la carte.
* Popup au clic sur un marqueur, affichant le titre, la catégorie et la date
  de l'activité.
