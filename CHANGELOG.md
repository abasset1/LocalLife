# Changelog

## 0.6.0 - Sprint 6

### Added

* Validation renforcée des activités : `title` obligatoire (≤ 255
  caractères) ; `url` de l'activité conservée après normalisation
  (colonne `url`, migration `V10`).
* Statut de modération sur `Activity`
  (`PENDING`/`PUBLISHED`/`REJECTED`, contrainte `CHECK` en base,
  migration `V11`).
* `GET /api/v1/activities/nearby` et `/within-bounds` ne retournent
  désormais que les activités `PUBLISHED` — `status` retiré des
  paramètres de ces endpoints publics.
* Contrôle administratif minimal (rôle `ADMIN` requis) :
  `GET /api/v1/admin/activities?status=...`,
  `PATCH /api/v1/admin/activities/{id}/publish`,
  `PATCH /api/v1/admin/activities/{id}/reject`.
* `GET /api/v1/sources` et `GET /api/v1/sources/{id}` (public) : une
  activité importée est désormais identifiable via l'API, pas
  seulement en base.
* Premier jalon Food Truck : nouveau module `foodtruck` indépendant,
  `GET`/`POST /api/v1/foodtrucks`, migration `V12` ; affiché sur la
  carte avec une icône dédiée, distincte de celle des activités.
* Suite de tests de non-régression consolidée
  (`NonRegressionIntegrationTest`) couvrant explicitement la
  visibilité publique par statut, le contrôle d'accès administrateur/
  utilisateur standard, la visibilité des food trucks et la recherche
  géographique.

### Fixed

* `README.md` : mention obsolète du paramètre `status` sur `/nearby`/
  `/within-bounds` (retiré depuis ce sprint) corrigée.

## 0.5.0 - Sprint 5

### Added

* Pipeline d'import de données externes : collecte → déduplication →
  normalisation/validation → persistance → journalisation.
* Premier collecteur réel (OpenAgenda).
* Modèle et module `Source` (`GET` non exposé à ce stade — ajouté en
  Sprint 6), source réservée `MANUAL` pour les contributions
  manuelles.
* Détection simple des doublons (identifiant externe, ou clé
  composite).
* Persistance des imports (création/mise à jour/archivage doux),
  journalisation.

## 0.4.0 - Sprint 4

### Added

* Recherche géographique par rayon avec PostGIS.
* Recherche par zone cartographique (bounding box).
* Filtres par catégorie et par date.
* Géolocalisation utilisateur côté frontend.
* Chargement des activités selon les critères géographiques.
* Contrats d'API et documentation OpenAPI associés.

### Fixed

* Correction du filtre SQL par date nécessitant un cast explicite du paramètre PostgreSQL.
* Correction de sécurité empêchant l'exposition de `passwordHash` dans les réponses de `UserController`.

0.3.0 - 2026-08-11

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
