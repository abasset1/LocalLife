# Changelog

## 0.9.9 — 2026-09-09

### Sprint 10 / LL-10010 — Tests et documentation du sprint (clôture)
- Ticket de clôture de sprint : `docs/PROJECT_STATUS.md` complété d'une section « Sprint 10 » couvrant `LL-10001` à `LL-10010` (chacun avec décision et source documentées) ; `docs/01_Product/BACKLOG.md` mis à jour ; `README.md` complété d'une section « Sprint 10 » ; statut de `docs/05_Sprints/SPRINT_10.md` mis à jour.
- Nouvelle entrée de dette technique (`docs/DETTE_TECHNIQUE.md`) documentant, une fois pour toutes plutôt qu'à chaque ticket, l'impossibilité récurrente d'exécuter `mvn verify` dans les sessions sandbox (Maven Central hors des domaines réseau autorisés).
- **Sprint fonctionnellement complet et documenté, non formellement clos** : `mvn verify` (critère explicite de ce ticket) n'a pu être exécuté dans aucune session ayant traité ce sprint — bloquant pour une clôture définitive, à faire par Alex. Le build frontend (`npm run build`), en revanche, a été vérifié à plusieurs reprises et passe.

## 0.9.8 — 2026-09-09

### Sprint 10 / LL-10009 — Valider le parcours carte / liste / détail
- Ticket de validation (pas de nouveau code fonctionnel) : revue de code exhaustive du parcours en 10 étapes défini par le ticket, tracée jusqu'aux lignes de `App.tsx` concernées — voir `docs/02_Architecture/LL-10009_VALIDATION_CARTE_LISTE_DETAIL.md`.
- Les 5 critères d'acceptation sont validés par le code : source de données unique (`visibleActivities`) partagée entre carte et liste, adresse/ville affichées sans transformation par rapport à l'API, aucune dépendance de l'effet de récupération des activités à `selectedCity`/`sortOrder`/`viewMode` (donc aucune régression sur `/nearby`/`/within-bounds`).
- ⚠️ Comme pour le protocole MVP (LL-7001), cette session sandbox n'a ni base PostgreSQL réelle ni navigateur pour un clic réel : la validation ci-dessus est une revue de code, pas une exécution. Une passe réelle par Alex reste nécessaire avant clôture définitive.

## 0.9.7 — 2026-09-09

### Sprint 10 / LL-10008 — Ajouter les contrôles de filtre et de tri par ville
- Deux nouveaux contrôles dans le bandeau de filtres (`App.tsx`) : « Filtrer par ville » (liste déroulante, construite depuis les activités chargées, même patron que le filtre catégorie) et « Trier par » (Par défaut / Ville / Date).
- ⚠️ Décision : filtrage et tri appliqués **côté client** (`filterAndSortActivities`), pas via l'endpoint `GET /api/v1/activities?city=...&sort=...` du contrat LL-10006, pour deux raisons documentées dans `App.tsx` :
  1. cet endpoint ne peut pas être combiné avec la recherche géographique (`nearby`/`within-bounds`) — restriction explicite du contrat LL-10006 ;
  2. plus important, cet endpoint ne filtre **aucun statut** (contrairement à `nearby`/`within-bounds`, restreints à `PUBLISHED`) : l'utiliser pour cette page publique aurait exposé des activités `PENDING`/`REJECTED`. Corriger cette lacune est un changement plus large, hors périmètre de ce ticket — signalé ici pour un futur ticket dédié si l'endpoint doit un jour devenir la source principale de navigation.
- Le tri « Par défaut » conserve le regroupement par ville historique (LL-EF-008) ; un tri explicite (Ville ou Date) bascule la liste sur un affichage plat dans l'ordre choisi, avec la ville rappelée sur chaque ligne (plus d'en-tête de groupe dans ce mode).
- Le filtre ville s'applique aussi à la carte (mêmes données affichées, critère d'acceptation « cohérent entre carte et liste »).
- Compatible avec les filtres catégorie/date existants par construction (appliqué en aval de leur résultat déjà filtré côté serveur).
- Vérifié avec `npm install && npx tsc --noEmit && npm run build` : compile sans erreur.

## 0.9.6 — 2026-09-09

### Sprint 10 / LL-10007 — Créer la vue liste des activités
- La vue liste, livrée par `LL-EF-008`, couvrait déjà la quasi-totalité du périmètre de ce ticket (bouton de bascule, activités récupérées depuis l'API, filtres catégorie/date actifs respectés, états chargement/erreur/aucun résultat, clic → détail). Seul écart identifié par rapport au critère d'acceptation explicite « la ville et l'adresse sont visibles » : l'adresse n'apparaissait que dans la modale de détail, pas dans la liste elle-même (la ville, elle, était déjà visible via l'en-tête de groupe).
- Ajout de l'adresse (`activity.address`, repli « Adresse non renseignée » si absente — jamais de valeur déduite, cohérent avec `LOCATION_CONTRACT.md`) sur chaque ligne de la liste, entre le titre et la ligne date/catégorie.
- Aucun changement côté API/backend pour ce ticket (les quatre endpoints exposent déjà `address`/`city` depuis LL-10005) ; aucun changement côté mobile (fonctionnalité non présente sur ce client, hors périmètre).

## 0.9.5 — 2026-09-08

### Sprint 10 / LL-10006 — Ajouter le filtre et le tri par ville
- `GET /api/v1/activities` accepte désormais deux paramètres optionnels : `city` (filtre exact, insensible à la casse) et `sort` (une ou plusieurs clés parmi `city`/`date`, séparées par une virgule, ex. `sort=city,date`) — contrat détaillé dans `docs/02_Architecture/LOCATION_CONTRACT.md`.
- Filtrage et tri réalisés côté base de données (requête SQL unique couvrant toutes les combinaisons de tri via des expressions `CASE WHEN`), pas en mémoire côté application.
- Sans `city` ni `sort`, le comportement historique de l'endpoint est strictement inchangé (aucun `ORDER BY` ajouté).
- `sort` contenant une valeur inconnue ou une clé en double renvoie `400 Bad Request`. `city` ne correspondant à aucune activité renvoie une liste vide (`200 OK`), pas une erreur.
- `nearby`/`within-bounds` ne sont pas concernés par ce ticket (leurs contrats respectifs restent inchangés).

## 0.9.4 — 2026-09-06

### Sprint Évol-Fix / LL-EF-006 — Créer une interface utilisateur
- Nouvelle page `/profile` : consultation (rôle, date d'inscription) et modification (username/email) du profil de l'utilisateur connecté, déconnexion accessible depuis cette page. Lien « Mon profil » ajouté dans l'en-tête, pour tout utilisateur connecté.
- Nouveaux endpoints `GET`/`PATCH /api/v1/users/me` : l'utilisateur cible est résolu exclusivement depuis le JWT (`JwtAuthentication`), jamais depuis un paramètre de requête — impossible de consulter/modifier le profil d'un autre utilisateur par ce chemin.
- `role`/`passwordHash` ne sont jamais modifiables via `PATCH /api/v1/users/me` (changement de mot de passe hors périmètre, prévu pour LL-EF-007).
- Écart de sécurité trouvé et corrigé en traitant ce ticket : `GET /api/v1/users/{id}` n'avait aucune protection (accessible à quiconque, sans authentification) — restreint au rôle `ADMIN`, cet endpoint n'étant consommé par aucun client (frontend ni mobile).

## 0.9.3 — 2026-09-06

### Sprint Évol-Fix / LL-EF-008 — Ajouter une liste des activités
- Bouton « Liste » ajouté dans le bandeau de filtres, à côté de « Filtrer par catégorie » ; bascule Carte ↔ Liste avec état actif visible (`aria-pressed`).
- Vue liste : activités regroupées par ville, triées par date à l'intérieur de chaque ville ; chaque ligne affiche titre, date (+ heure si non nulle), catégorie. Clic sur une ligne : détail complet dans une modale (adresse, date/heure, catégorie, source).
- Écart trouvé en traitant ce ticket : aucune notion de ville n'existait dans le modèle métier (seules `latitude`/`longitude` étaient stockées) — changement de modèle documenté dans `docs/02_Architecture/ADR-0001-adresse-structuree-activites.md`. Trois colonnes nullables ajoutées à `activity` (`address`, `city`, `postal_code`, migration `V15` — `V14` déjà pris par LL-EF-005), résolues une seule fois à l'écriture (contribution manuelle : réutilisation de l'appel Nominatim existant avec `addressdetails=1` ; import : champs déjà présents dans l'objet `location` d'OpenAgenda, jusqu'ici ignorés). Aucun appel réseau supplémentaire.
- Demande complémentaire d'Alex traitée dans le même changement : la carte et le détail affichent désormais une adresse lisible (`address`, repli sur `city` puis sur les coordonnées) au lieu des coordonnées GPS brutes.
- `LL-EF-006` et `LL-EF-007` restent non traités (ticket pris hors ordre à la demande d'Alex).
- Deux correctifs de compilation post-livraison (sites de construction `Activity`/`Coordinates` manqués lors du premier passage) : `SourceService#withSourceId`, puis 4 tests d'intégration (`AdminActivityControllerIntegrationTest`, `AuthenticationFlowIntegrationTest`, `NonRegressionIntegrationTest`).

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

### Sprint Évol-Fix / LL-EF-004 — Créer une interface d'administration
- Nouvelle page `/admin`, réservée aux utilisateurs avec le rôle `ADMIN` (lien dans l'en-tête + garde de redirection côté page ; protection réelle déjà assurée côté backend, `SecurityConfig`, depuis LL-6005/LL-6006).
- Trois onglets (En attente / Publiées / Rejetées) listent les activités par statut, avec le détail complet de chacune (titre, description, catégorie, dates, position).
- Boutons Valider/Refuser sur les activités en attente, qui appellent les endpoints de modération existants (`PATCH /api/v1/admin/activities/{id}/publish` et `.../reject`).
- Aucun changement backend : le statut de modération `PENDING`/`PUBLISHED`/`REJECTED` et ses endpoints existaient déjà depuis le Sprint 6.

### Sprint Évol-Fix / LL-EF-005 — Gérer les agendas depuis l'interface d'administration
- Nouvelle section « Agendas » dans `/admin` : liste, création, modification, suppression (avec confirmation) des sources/agendas.
- Les agendas OpenAgenda sont désormais configurés dynamiquement en base (uid + filtre région par agenda), en remplacement du système par propriétés d'environnement — un ajout/modification/suppression prend effet dès le prochain import, sans redémarrage.
- Suppression d'un agenda encore lié à des activités : autorisée, les activités sont détachées vers la source réservée « Saisie manuelle » plutôt que la suppression bloquée.
- CRUD générique sur tous les types de source (API/RSS/MANUAL), pas réservé à OpenAgenda.
- Nouveaux endpoints protégés (rôle ADMIN) : `POST`/`PUT`/`DELETE /api/v1/sources[/{id}]`.

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
