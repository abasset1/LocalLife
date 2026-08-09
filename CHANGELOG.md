# Changelog

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
