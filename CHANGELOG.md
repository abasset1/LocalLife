# Changelog

## 0.9.2 — 2026-09-02

### Sprint Évol-Fix / LL-EF-001 — Revoir l'affichage de la saisie d'une activité
- Le formulaire de saisie d'une activité (bandeau permanent auparavant) devient une fenêtre modale, ouverte depuis un bouton « Proposer une activité » toujours visible dans l'en-tête.
- Un visiteur non connecté cliquant sur ce bouton est redirigé vers `/login` ; seul un utilisateur connecté peut ouvrir la modale.
- Champs du formulaire présentés avec des libellés visibles (`<label>`), disposition verticale plus lisible.
- Fermeture de la modale via bouton ✕, clic sur l'arrière-plan, ou touche Échap.
- Aucun changement backend.

### Sprint Évol-Fix / LL-EF-002 — Supprimer le bandeau « Utiliser la localisation »
- Le bandeau et le bouton « Utiliser ma position » sont supprimés.
- La géolocalisation navigateur est désormais demandée automatiquement, une seule fois au chargement de la page, sans action utilisateur.
- En cas de refus, d'indisponibilité ou de timeout, plus aucun message affiché : repli silencieux sur la position par défaut (Marseille), comportement inchangé depuis LL-4008.
- État React devenu inutile (`GeolocationStatus`, message d'erreur) supprimé.

### Sprint Évol-Fix / LL-EF-003 — Revoir le rechargement de la carte
- Un déplacement/zoom pur de la carte (aucun filtre ni position n'a changé) n'entraîne plus la suppression immédiate des marqueurs ni l'affichage d'un texte « Chargement » : les anciens marqueurs restent visibles jusqu'à ce que les nouvelles données soient prêtes, puis sont remplacés en une seule fois.
- Un changement actif (filtre catégorie/date, position obtenue, nouvelle activité proposée) conserve le comportement précédent (suppression immédiate + texte de chargement).
- Aucun changement sur le debounce des appels API (400 ms, déjà en place depuis LL-4012) ni sur le cycle de vie de `MapContainer` (jamais démonté/reconstruit).

## 0.9.1 — 2026-08-25

### Sprint 8 / LL-8009 — Décision go/no-go de la bêta
- Écart trouvé en vérifiant le critère « plusieurs agendas Avignon » (LL-8004) : les propriétés `openagenda.avignon-*-uid` étaient définies mais jamais lues par aucun bean — un seul agenda (la démonstration historique, pas même Avignon-spécifique) était réellement collecté, quel que soit le nombre d'agendas configurés en propriétés.
- Corrigé : `OpenAgendaSourcesConfig` (nouvelle classe) enregistre désormais un collecteur par agenda dont l'uid est réellement configuré ; `OpenAgendaCollector` n'est plus un `@Component` auto-détecté.
- Trois tests d'intégration ajustés (`SingleMockCollectorConfig`, nouvelle configuration de test partagée) pour rester isolés d'un appel réseau réel malgré plusieurs collecteurs désormais actifs.
- Limite résiduelle documentée (`docs/DETTE_TECHNIQUE.md`) : seul l'agenda Avignon Culture a un uid réel, les trois autres (spectacles/patrimoine/loisirs) restent à identifier par Alex.
- Décision : **GO bêta conditionnel**, voir `docs/PROJECT_STATUS.md` (section LL-8009) pour les conditions précises avant ouverture effective.

## 0.9.0 — 2026-08-25

### Sprint 8 / Préparation de la bêta (LL-8001 → LL-8008)
- `LL-8001` : parcours MVP rejoué après les corrections du Sprint 7, aucune régression, baseline figée.
- `LL-8002` : premier compte `ADMIN` bootstrapé automatiquement au premier démarrage (`AdminBootstrapRunner`), remplace la promotion SQL manuelle.
- `LL-8003` : exceptions serveur non gérées journalisées (niveau `ERROR`, sans donnée sensible) par `GlobalExceptionHandler`.
- `LL-8004` : plusieurs agendas OpenAgenda configurés pour Avignon ; pagination complète de l'API OpenAgenda (`size=300` + curseur `after`).
- `LL-8005` : import automatique planifié (`ImportScheduler`, toutes les heures), en complément du déclenchement manuel existant.
- `LL-8006` : affichage de bout en bout vérifié sur la carte ; popup enrichi avec le lieu (coordonnées) et la source lisible (`ActivityResponse`, résolution de `sourceId` en `sourceName`).
- `LL-8007` : dette technique pertinente pour une bêta traitée (vulnérabilité `nanoid`, formatage de `ActivityController`, duplication des `ROADMAP.md`) — `docs/DETTE_TECHNIQUE.md` sans entrée ouverte.
- `LL-8008` : documentation consolidée sur l'ensemble des fichiers de référence (`README.md`, `docs/PROJECT_STATUS.md`, `docs/04_Project/ROADMAP.md`, `docs/01_Product/BACKLOG.md`, `docs/NEXT_TASK.md`, etc.), plus aucune ne désigne le Sprint 7 comme sprint courant.
- Reste à faire avant la clôture du Sprint 8 : `LL-8009` (décision go/no-go de la première bêta contrôlée).

### Fixed
- Correctifs de qualité signalés par `mvn verify` au fil du sprint : test d'intégration dépendant d'un id d'activité fixe (fragile sur base persistante), dépassements de la limite Checkstyle de 120 caractères, échappement `*/` dans un Javadoc (`ImportScheduler`) provoquant une erreur de compilation.

## 0.8.0 — 2026-08-21

### Sprint 7 / Validation
- Sprint 7 terminé (`LL-7001` → `LL-7009`).
- MVP validé après exécution des parcours réels et correction des blocages identifiés.
- Environnement de démonstration documenté.
- Phase suivante : préparation d'une première bêta contrôlée.

### Sprint 8 / Planification
- Sprint 8 défini pour consolider la baseline, l'opérationnel, la qualité et la documentation avant bêta.


## 0.7.0 — 2026-08-17

### Documentation / Planification
- Sprint 6 confirmé terminé après LL-6011.
- Phase 2 de validation du MVP ouverte.
- Sprint 7 défini pour valider le MVP de bout en bout avant toute nouvelle évolution.
- Backlog, roadmap et NEXT_TASK réalignés sur une source de vérité explicite.

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
