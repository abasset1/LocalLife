# LocalLife - Project Status

**Version :** 0.3.0
**Dernière mise à jour :** 2026-08-12

---
## Phase actuelle
🟡 Phase 1 — Construction du socle technique

Le cadrage fonctionnel et technique est terminé.
Sprint 0 (socle technique backend) terminé. Sprint 1 (première fonctionnalité visible) terminé. Sprint 2 (utilisateurs et catégories) terminé. Sprint 3 (authentification, géocodage) terminé. Sprint 4 (recherche et découverte géographique) terminé.

---
## Avancement
   Domaine                    | État         |
 | -------------------------- | ------------ |
 | Vision produit             | ✅ Terminé    | (Phase 0)
 | Product Bible              | ✅ Terminé    | (Phase 0)
 | MVP                        | ✅ Terminé    | (Phase 0)
 | User Stories               | ✅ Terminé    | (Phase 0)
 | Backlog fonctionnel        | ✅ Terminé    | (Phase 0)
 | Roadmap                    | ✅ Terminé    | (Phase 0)
 | Architecture fonctionnelle | ✅ Terminé    | (Phase 0)
 | Architecture technique     | ✅ Terminé    | (Phase 0)
 | Modèle de données          | ✅ Terminé    |
 | API MVP                    | ✅ Terminé    |
 | Repository                 | ✅ Initialisé |
 | Développement Backend      | 🟡 En cours   |
 | Développement Frontend     | 🟡 En cours   |
 | Collecteurs                | 🟡 Prochaine étape |
 | Phase 1 (Socle Technique)  | ✅ Terminé    |
 | Infrastructure             | 🟡 À consolider |

---
## Décisions validées

* Architecture monolithe modulaire.
* API REST versionnée.
* PostgreSQL + PostGIS.
* Backend Spring Boot.
* Frontend React + TypeScript.
* Docker dès le début du projet.
* Flyway pour les migrations.
* Les collecteurs passent uniquement par l'API métier.
* Aucun microservice pour le MVP.
* Aucune application mobile native avant validation du produit.

---
# Phase 1 — Socle Technique
**Statut :** ✅ Terminé
Tous les sprints (0 à 4) sont terminés.

---
# MVP retenu

Le MVP comprend uniquement :

* Carte interactive.
* Géolocalisation.
* Recherche par zone.
* Recherche par catégorie.
* Consultation d'une activité.
* Création d'une activité.
* Validation des contributions.
* Import de données externes.
* Gestion des food trucks.
* Administration simple.

Tout le reste est hors périmètre.

---
## Priorité actuelle
Créer le socle technique.

Ordre recommandé :

1. Backend
2. Base PostgreSQL/PostGIS
3. Docker
4. Flyway
5. Frontend
6. Carte interactive
7. Modules métier

---
## Risques identifiés

* Ajouter des fonctionnalités avant validation du MVP.
* Complexifier l'architecture prématurément.
* Mélanger logique métier et logique technique.
* Développer plusieurs clients (web/mobile) avant validation.

---
## Principes du projet

* Simplicité avant optimisation.
* Documentation avant implémentation.
* API First.
* Architecture modulaire.
* Évolutions incrémentales.
* Chaque fonctionnalité doit apporter une valeur utilisateur.

---
## Définition d'une tâche terminée

Une tâche est considérée comme terminée lorsque :

* le développement est terminé ;
* les tests sont réalisés ;
* la documentation est mise à jour ;
* les critères d'acceptation sont validés.

---
# Sprint 0

Statut : ✅ Terminé.

Tickets terminés :
* LL-0001 — Initialiser le projet Spring Boot ✅
* LL-0002 — Créer l'arborescence backend ✅
* LL-0003 — Configurer les profils Spring ✅
* LL-0004 — Docker Compose (PostgreSQL/PostGIS) ✅
* LL-0005 — Configuration PostgreSQL (datasource, profil local) ✅
* LL-0006 — Installer Flyway (première migration vide) ✅
* LL-0007 — Actuator (health, info) ✅
* LL-0008 — OpenAPI (Swagger) ✅
* LL-0009 — Qualité de code (Spotless, Checkstyle) ✅
* LL-0010 — Logging (Logback, niveaux par profil, format uniforme) ✅
* LL-0011 — Gestion des erreurs (exception globale, réponse JSON standardisée) ✅
* LL-0012 — Docker Backend (Dockerfile) ✅
* LL-0013 — README Backend (démarrage, profils, Docker, commandes Maven) ✅
* LL-0014 — Pipeline GitHub Actions (build, tests) ✅
* LL-0015 — Vérification finale ✅

---
# Sprint 1

Statut : ✅ Terminé.

Tickets terminés :
* LL-1001 — Créer le module Activity ✅
* LL-1002 — Créer l'entité Activity ✅
* LL-1003 — Migration Flyway (table activity) ✅
* LL-1004 — Repository Activity ✅
* LL-1005 — Service Activity ✅
* LL-1006 — Données de démonstration ✅
* LL-1007 — API REST de consultation des activités ✅
* LL-1008 — Première carte React + Leaflet ✅
* LL-1009 — Affichage des activités sous forme de marqueurs ✅
* LL-1010 — Popup activité ✅
* LL-1011 — Documentation ✅

---
# Sprint 2

Statut : ✅ Terminé.

Objectif : gestion des utilisateurs et des catégories.

Tickets terminés :
* LL-2001 — Créer le module User ✅
* LL-2002 — Créer l'entité User ✅
* LL-2003 — Migration Flyway pour User ✅
* LL-2004 — Repository User ✅
* LL-2005 — Service User ✅
* LL-2006 — Créer le module Category ✅
* LL-2007 — Migration Flyway pour Category ✅
* LL-2008 — Repository Category ✅
* LL-2009 — Service Category ✅
* LL-2010 — API REST pour User ✅
* LL-2011 — API REST pour Category ✅
* LL-2012 — Formulaire de contribution ✅
* LL-2013 — Documentation ✅

---
# Sprint 3

Statut : ✅ Terminé.

Objectif : authentification, géocodage, gestion des utilisateurs.

Tickets terminés :
* LL-3001 — Étendre l'entité User ✅
* LL-3002 — Migration Flyway pour les nouveaux champs ✅
* LL-3003 — Service de hachage des mots de passe ✅
* LL-3004 — Implémenter le login ✅
* LL-3005 — Génération de JWT ✅
* LL-3006 — Middleware de vérification JWT ✅
* LL-3007 — Endpoints d'authentification ✅
* LL-3008 — Protéger les endpoints existants ✅
* LL-3009 — Frontend : pages de login/register ✅
* LL-3010 — Frontend : affichage de l'utilisateur connecté ✅
* LL-3011 — Frontend : appels API avec JWT ✅
* LL-3012 — Backend : intégration du géocodage ✅
* LL-3013 — Frontend : mise à jour du formulaire de contribution ✅
* LL-3014 — Tests d'intégration ✅
* LL-3015 — Mise à jour de la documentation ✅

Correctif hors ticket : fuite `passwordHash` signalée en LL-3010, corrigée (`UserController` renvoie désormais `UserResponse`, plus jamais l'entité `User`).

---
# Sprint 4

Statut : ✅ Terminé.

Objectif : recherche et découverte géographique (recherche par rayon PostGIS, recherche par zone cartographique, filtres catégorie/date, géolocalisation utilisateur).

Tickets terminés :
* LL-4001 — Définir la recherche géographique ✅ — contrat documenté dans `docs/02_Architecture/GEO_SEARCH_CONTRACT.md` (endpoint `GET /api/v1/activities/nearby`, `radius` en km plafonné à 50, filtre optionnel `status`).
* LL-4002 — Ajouter la recherche géographique PostGIS ✅ — migration `V7` (extension PostGIS, colonne `location GEOGRAPHY`, trigger d'alimentation depuis latitude/longitude), `ActivityRepository.findWithinRadius` (`ST_DWithin`/`ST_Distance`), `ActivityService.findNearby`.
* LL-4003 — Endpoint des activités proches ✅ — `GET /api/v1/activities/nearby` (paramètres `latitude`/`longitude`/`radius`/`status`, validation complète renvoyant `400` sur erreur — volontairement en `String`/`required=false` plutôt que `double`/`required=true`, car `GlobalExceptionHandler` attrape `Exception` de façon générique et renverrait `500` sinon sur un paramètre manquant/invalide). Documentation OpenAPI ajoutée (`@Operation`/`@Parameter`/`@ApiResponses`, première utilisation dans ce projet). Tests : `ActivityServiceTest`, `ActivityRepositoryIntegrationTest` (filtre statut), `ActivityControllerTest`, `ActivityControllerIntegrationTest`.
  - ⚠️ Non exécuté en sandbox (pas de `mvn` ni d'accès réseau à Maven Central) — à valider avec `mvn verify`.
* LL-4004 — Filtre par catégorie ✅ — nouveau paramètre `category` (query string, liste séparée par des virgules, ex. `concert,marché`) sur `GET /api/v1/activities/nearby`.
  - ⚠️ Décision importante à valider avec toi : le ticket donne l'exemple `categoryId=1`, mais ce n'est implémentable tel quel — `activity.category` est une chaîne libre saisie à la contribution (LL-2012), sans aucune relation avec la table `category` (pas de FK, pas de données). Utiliser `categoryId` aurait exigé d'ajouter une vraie relation `Activity` → `Category`, une modification du modèle métier explicitement interdite sans ticket dédié par les règles du Sprint 4. Le paramètre s'appelle donc `category` (chaîne, comparaison exacte contre `activity.category`), documenté en détail dans `docs/02_Architecture/GEO_SEARCH_CONTRACT.md`.
  - Catégorie inexistante → liste vide (`200 OK`), pas d'erreur `400` : contrairement à `status`, il n'existe pas de liste fermée de catégories valides (champ libre), donc rien de "invalide" à proprement parler — satisfait quand même le critère d'acceptation "catégorie inexistante gérée proprement".
  - `ActivityRepository.findWithinRadius` complété avec un paramètre `categoriesCsv` nullable, filtré côté SQL via `string_to_array(...)`/`ANY(...)` (même pattern « paramètre nullable unique » que `status`, pas de binding de collection Java).
  - `ActivityService.findNearby` normalise le paramètre brut (`trim`, valeurs vides retirées) avant de le transmettre.
  - Tests : `ActivityServiceTest` (normalisation CSV, valeurs avec espaces, uniquement des valeurs vides → `null`) ; `ActivityRepositoryIntegrationTest` (catégorie unique, plusieurs catégories combinées avec le rayon, catégorie inexistante → vide) ; `ActivityControllerTest`/`ActivityControllerIntegrationTest` complétés.
  - ⚠️ Non exécuté en sandbox (même limitation qu'aux tickets précédents) — à valider avec `mvn verify`.
* LL-4005 — Filtre par date ✅ — nouveau paramètre `date` (query string, format ISO-8601 `yyyy-MM-dd`) sur `GET /api/v1/activities/nearby`.
  - Une activité est retenue quand la date fournie tombe dans sa période `[startDate, endDate]` (bornes incluses, comparaison au jour près — l'heure de `startDate`/`endDate` n'entre pas en jeu). Couvre les activités d'une seule journée et celles sur plusieurs jours (critères d'acceptation du ticket).
  - ⚠️ Décision à valider avec toi : `endDate` peut être `NULL` en base (activités créées via le formulaire de contribution, qui ne renseigne pas de date de fin). Traitée comme ne durant que la journée de `startDate` (`COALESCE(endDate, startDate)` côté SQL) plutôt que d'exclure ces activités de tout filtre par date — détaillé dans `docs/02_Architecture/GEO_SEARCH_CONTRACT.md`.
  - `date` fournie mais pas au format ISO-8601 → `400 Bad Request` (même pattern de validation locale que les autres paramètres de `findNearby`).
  - `ActivityRepository.findWithinRadius` complété avec un paramètre `date` (`LocalDate`) nullable, filtré côté SQL via `BETWEEN ... start_date::date AND COALESCE(end_date, start_date)::date`.
  - Tests : `ActivityServiceTest` (parsing ISO-8601, date invalide, date inexistante type 30 février) ; `ActivityRepositoryIntegrationTest` (activité d'une journée incluse/exclue, activité sur plusieurs jours à chaque date de sa période et au jour suivant, `endDate` absente traitée comme même jour que `startDate`) ; `ActivityControllerTest`/`ActivityControllerIntegrationTest` complétés.
  - ⚠️ Non exécuté en sandbox (même limitation qu'aux tickets précédents) — à valider avec `mvn verify`.
* LL-4006 — Définir la recherche par zone cartographique ✅ — contrat documenté dans `docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md` (nouvel endpoint `GET /api/v1/activities/within-bounds`, paramètres `swLatitude`/`swLongitude`/`neLatitude`/`neLongitude`, filtres `status`/`category`/`date` réutilisés à l'identique du contrat `/nearby`). Pas de code à ce stade (ticket sans section détaillée dans `SPRINT_4.md`, traité par analogie avec LL-4001 : contrat d'abord, implémentation PostGIS + endpoint en LL-4007 qui en dépend explicitement).
  - ⚠️ Décisions à valider avec toi : nom de l'endpoint (`/within-bounds`, plutôt que d'étendre `/nearby` — évite de mélanger deux contrats de validation différents) ; pas de support de la traversée de l'antiméridien (`swLongitude < neLongitude` strict, `400` sinon) ; tri par `id` croissant en l'absence de point de référence pour une distance. Détail complet dans le contrat.
* LL-4007 — Bounding Box ✅ — implémentation du contrat défini en LL-4006 : endpoint `GET /api/v1/activities/within-bounds` (paramètres `swLatitude`/`swLongitude`/`neLatitude`/`neLongitude` obligatoires, `status`/`category`/`date` optionnels réutilisés à l'identique de `/nearby`).
  - `ActivityRepository.findWithinBounds` : filtrage PostGIS via l'opérateur `&&` contre `ST_MakeEnvelope(swLongitude, swLatitude, neLongitude, neLatitude, 4326)` (bounding box exploitant l'index spatial), résultats triés par `id` croissant (pas de point de référence pour une distance, décision du contrat LL-4006).
  - `ActivityService.findWithinBounds` : même pattern de validation locale que `findNearby` (paramètres reçus en `String`, `IllegalArgumentException` → `400`) ; `swLatitude`/`neLatitude`/`swLongitude`/`neLongitude` validés individuellement (plages -90/90, -180/180) puis `swLatitude < neLatitude` et `swLongitude < neLongitude` strictement (sinon `400`, cf. décision antiméridien du contrat). Petit refactor : extraction de `validateLatitude`/`validateLongitude`, réutilisées par `findNearby` (évite la duplication introduite par LL-4001/LL-4002).
  - Tests : `ActivityServiceTest` (paramètres manquants/non numériques/hors plage, `swLatitude >= neLatitude`, `swLongitude >= neLongitude`, statut inconnu, date invalide, transmission correcte des filtres) ; `ActivityRepositoryIntegrationTest` (activité dans/hors zone, bordure nord exclue, filtres status/category/date combinés à la zone, tri par id et non par distance) ; `ActivityControllerTest`/`ActivityControllerIntegrationTest` complétés avec le nouvel endpoint.
  - ⚠️ Non exécuté en sandbox (même limitation qu'aux tickets précédents) — à valider avec `mvn verify`.

## Correctif post-LL-4007 — BadSqlGrammarException sur le filtre par date

Signalé par Alex via `mvn verify` (première exécution réelle contre
PostgreSQL depuis l'introduction du filtre `date` en LL-4005) :
`ActivityControllerIntegrationTest.getNearbyActivities_ShouldReturnOk_WhenParamsValid`
et `ActivityRepositoryIntegrationTest.findWithinRadius_ShouldExcludeActivity_WhenOutsideRequestedRadius`
échouaient avec `BadSqlGrammarException`.

**Cause** : dans `findWithinRadius` (LL-4005) et `findWithinBounds`
(LL-4007), le filtre par date s'écrivait `:date IS NULL OR :date BETWEEN
...`. PostgreSQL ne peut pas déterminer le type du paramètre `:date` à
partir d'un simple `? IS NULL` isolé, sans contexte de type — erreur
`could not determine data type of parameter`, traduite par Spring en
`BadSqlGrammarException`. Se déclenche uniquement quand `date` vaut
`null` (donc systématiquement dès qu'un appel n'utilise pas ce filtre),
ce qui explique pourquoi les deux tests touchés appellent la recherche
sans paramètre `date`. Bug introduit en LL-4005, non détectable en
sandbox faute d'accès à une vraie base PostgreSQL (les requêtes n'y sont
jamais exécutées, seulement relues).

**Correctif** : cast explicite `:date::date` aux deux occurrences (le
test `IS NULL` et la comparaison `BETWEEN`), dans les deux requêtes
(`findWithinRadius` et `findWithinBounds`, qui partagent ce même
fragment de filtre).

Aucun changement de comportement fonctionnel, aucun test modifié —
uniquement la requête SQL corrigée.

---
# Sprint 5

Statut : 🟡 À démarrer.

Objectif : alimenter LocalLife avec une première source réelle et valider le pipeline collecte → normalisation → validation → persistance.

Voir `docs/05_Sprints/SPRINT_5.md`.

Tickets prévus :
* LL-5001 — Définir le contrat Source
* LL-5002 — Créer le module Source
* LL-5003 — Définir le contrat Collector
* LL-5004 — Définir le modèle de données importées
* LL-5005 — Pipeline de normalisation
* LL-5006 — Premier collecteur réel
* LL-5007 — Détection simple des doublons
* LL-5008 — Persistance des imports
* LL-5009 — Journalisation des imports
* LL-5010 — Tests du pipeline
* LL-5011 — Vérifier l'affichage sur la carte
* LL-5012 — Documentation

---

## LL-5001 — Définir le contrat Source ✅

`docs/02_Architecture/SOURCE_CONTRACT.md` : modèle `Source` (`id`,
`name`, `type`, `url`, `status`, `lastSyncAt`), valeurs de `type`
(`API`, `RSS`, `MANUAL`) et de `status` (`ACTIVE`, `INACTIVE`,
`ERROR`). Pas de code à ce stade, comme `BOUNDING_BOX_SEARCH_CONTRACT.md`
pour LL-4006.

⚠️ Décision validée par Alex : compatibilité avec les activités créées
manuellement assurée par une source réservée `MANUAL` (une seule ligne
en base) plutôt qu'un `sourceId` nullable sur `Activity` — évite
d'introduire un cas particulier « pas de source » dans le domaine
métier. Implémentée en LL-5002 ci-dessous.

## LL-5002 — Créer le module Source ✅

**Dépendance :** LL-5001.

* `source/domain/Source.java` : record aligné sur le contrat
  (`type`/`status` en chaîne libre, comme `Activity.status`).
* `source/infrastructure/SourceRepository.java` : `save`/`findAll`/
  `findById` uniquement — même style que `CategoryRepository`/
  `UserRepository` (extension de `Repository`, pas `CrudRepository`,
  pour n'exposer que les méthodes nécessaires).
* `source/application/SourceService.java` : délégation minimale,
  `createSource(name, type, url)` avec statut `ACTIVE` et
  `lastSyncAt` à `null` par défaut, conformément au contrat.
* Migration `V8__create_source_table.sql` : table `source` + insertion
  de la source réservée `MANUAL` (`Saisie manuelle`), décision LL-5001.
* Tests : `SourceServiceTest` (Mockito, sans base) et
  `SourceRepositoryIntegrationTest` (base réelle, `@Transactional`,
  vérifie aussi la présence de la source `MANUAL` insérée par la
  migration).
* Pas de contrôleur REST : ni `SPRINT_5.md` ni les critères
  d'acceptation de LL-5001/LL-5002 ne demandent d'endpoint à ce stade.
* Pas de modification d'`Activity` : le lien `Activity` → `Source`
  (colonne, FK) est explicitement différé à un ticket ultérieur
  (LL-5008, persistance des imports), voir `SOURCE_CONTRACT.md`.
* Non compilé/testé en sandbox : Maven Central (`repo.maven.apache.org`)
  n'est pas dans les domaines réseau autorisés — même limitation que
  LL-3012. `mvn verify` à lancer de ton côté.

---
# Prochaine action

Sprint 5 : traiter LL-5009 — Journalisation des imports (dépend de LL-5008, ci-dessus).

## LL-5008 — Persistance des imports ✅

**Dépendance :** LL-5007.

Le lien `Activity` → `Source`, explicitement différé depuis LL-5001/
LL-5003, est enfin créé :
* migration `V9__link_activity_to_source.sql` : colonnes `source_id`
  (FK vers `source`, `NOT NULL`, rétro-remplie sur la source `MANUAL`
  pour les activités existantes) et `import_key` (nullable — `NULL`
  pour toute activité manuelle) ; index unique partiel
  `(source_id, import_key) WHERE import_key IS NOT NULL` ;
* `Activity` (record) : deux champs ajoutés (`sourceId`, `importKey`) —
  **tous les sites de construction existants ont dû être mis à jour**
  (`ActivityService#createActivity`, `NormalizationService`,
  `ActivityControllerTest`, `ActivityServiceTest`,
  `ActivityRepositoryIntegrationTest`) : conséquence nécessaire, pas un
  élargissement de périmètre — voir chaque fichier pour le détail ;
* `ActivityService#createActivity` : résout désormais la source `MANUAL`
  via `SourceService#findByType` avant de sauvegarder — comportement
  observable inchangé (critère « création manuelle non affectée »),
  seul un champ interne obligatoire est renseigné.

* `SourceRepository`/`SourceService` : `findByType` (résoudre `MANUAL`
  sans dépendre de son libellé) et `findOrCreateByName` (rapprochement
  « recherche par nom, création si absente », différé depuis LL-5003/
  LL-5001 — voir `SOURCE_CONTRACT.md`/`COLLECTOR_CONTRACT.md`).
* `ActivityRepository` : `findBySourceIdAndImportKey` (détection d'une
  activité déjà importée) et `findBySourceId` (balayage pour
  l'archivage, scopé à une seule source).
* `collector/application/ImportService.java` : orchestre `Collector` →
  `DeduplicationService` → `NormalizationService` →
  `ActivityRepository`/`SourceService`. `importAll()` boucle sur tous
  les `Collector` enregistrés (`List<Collector>`, un seul actuellement :
  `OpenAgendaCollector`).
* `collector/application/ImportResult.java` : récapitulatif
  (créées/mises à jour/archivées/rejetées) par source.

⚠️ Décisions prises, à valider :
* **suppression douce** : une activité déjà importée pour une source
  mais absente de la dernière collecte est **archivée** (`status =
  ARCHIVED`), jamais supprimée physiquement — plus prudent pour un MVP
  (une panne réseau partielle du collecteur ne doit pas effacer des
  activités réelles), et exploitable par LL-5009. ⚠️ **Point à
  surveiller** : ni la recherche ni la carte ne filtrent `status` par
  défaut — une activité `ARCHIVED` continuera d'apparaître tant qu'un
  filtre explicite n'est pas ajouté (hors périmètre de ce ticket) ;
* type par défaut `"API"` attribué à une `Source` nouvellement créée
  lors d'un import — `Collector` n'expose pas de `getSourceType()`
  (voir `COLLECTOR_CONTRACT.md`), ce choix serait à revoir si un futur
  collecteur RSS est ajouté ;
* une donnée rejetée par la normalisation (invalide) n'est pas comptée
  comme « vue » : si elle correspond à une activité déjà importée,
  celle-ci sera archivée à ce passage plutôt que laissée telle quelle.

**Aucun déclencheur ajouté** (pas d'endpoint, pas de tâche planifiée) :
ni `SPRINT_5.md` ni les critères de LL-5008 n'en demandent un.
`ImportService#importAll()` est appelable directement (tests, LL-5010)
mais rien ne l'invoque encore dans l'application en cours d'exécution —
à surveiller si Alex attend un import réellement déclenchable avant la
fin du sprint.

Tests : `ImportServiceTest` (6 cas — création, mise à jour, rejet,
archivage, non-réarchivage, portée par source), `SourceServiceTest`
complété (`findByType`, `findOrCreateByName`), et les tests
`Activity`-dépendants existants mis à jour pour compiler.

Non compilé/testé en sandbox : Maven Central inaccessible, comme pour
LL-5002/LL-5006.

## LL-5007 — Détection simple des doublons ✅

**Dépendance :** LL-5005.

`collector/application/DeduplicationService.java` : `computeDeduplicationKey(CollectedActivity)`
→ `String`, en mémoire uniquement, aucune requête en base (la
comparaison avec les activités déjà persistées relève de LL-5008 — ce
ticket ne dépend d'ailleurs que de LL-5005, pas de LL-5008).

Stratégie conforme à `SPRINT_5.md` :
* priorité à l'identifiant externe (`source` + `externalId`, préfixe
  `external:`) — traité comme absent si vide/blanc, pas seulement nul ;
* à défaut, combinaison déterministe `source`/`title`/`startDate`/
  latitude/longitude, hachée en SHA-256 (préfixe `composite:`) pour une
  clé de taille fixe.

Calculé sur `CollectedActivity` (pas sur `Activity`, qui ne porte plus
`source`/`externalId` après normalisation) — donc avant ou indépendamment
de `NormalizationService`.

Tests : `DeduplicationServiceTest` (9 cas — clé stable par identifiant
externe, clés différentes par identifiant, repli sur la clé composite
si identifiant vide/nul, stabilité et divergence de la clé composite
selon titre/date/localisation).

## LL-5006 — Premier collecteur réel ✅

**Dépendance :** LL-5003, LL-5004, LL-5005.

**Source retenue : l'API officielle OpenAgenda** (developers.openagenda.com),
validée par Alex le 14/08/2026 après comparaison avec Open Data AMP /
DATAtourisme (pas de clé API, mais parsing plus complexe : catégories
multi-niveaux, dates concaténées, doublons par représentation). OpenAgenda
retenue pour son JSON plus simple, au prix d'une clé API à gérer.

* `collector/domain/Collector.java` : l'interface documentée en LL-5003
  existe désormais en code — c'était le premier ticket à en avoir
  réellement besoin.
* `collector/infrastructure/OpenAgendaCollector.java` : implémente
  `Collector` via `RestClient` (`GET /v2/agendas/{agendaUid}/events`),
  même style que `GeocodingService` (constructeur package-privé pour
  injecter un `RestClient.Builder` en test).
* `collector/infrastructure/CollectorException.java` : exception non
  vérifiée, fixée pour ce ticket (`COLLECTOR_CONTRACT.md` ne l'imposait
  pas), levée en cas de configuration manquante ou d'échec réseau.
* Configuration (`application.properties`, aucun secret committé) :
  `OPENAGENDA_API_KEY`, `OPENAGENDA_AGENDA_UID`, `OPENAGENDA_SOURCE_NAME`
  (optionnel, défaut `"OpenAgenda"`) — vides par défaut ;
  `collect()` échoue explicitement (`CollectorException`) tant qu'elles
  ne sont pas renseignées, plutôt que de bloquer le démarrage de
  l'application.

⚠️ **Action requise de ta part avant que ce collecteur puisse réellement
fonctionner** : créer un compte OpenAgenda, obtenir une clé publique,
choisir l'agenda Marseille à utiliser (ex. « Marseille Alive », ou l'agenda
de l'Office de Tourisme de Marseille s'il en existe un accessible), relever
son identifiant numérique (visible en pied de barre latérale sur
openagenda.com), puis définir `OPENAGENDA_API_KEY` et `OPENAGENDA_AGENDA_UID`
dans ton environnement. Sans ça, `collect()` lève une `CollectorException`
explicite — comportement attendu, pas un bug.

⚠️ Décisions prises pour ce premier collecteur, à valider :
* une seule `CollectedActivity` par événement (sa prochaine occurrence,
  `nextTiming`), pas une par créneau récurrent de `timings` ;
* catégorie dérivée du premier mot clé français (`keywords.fr[0]`) —
  OpenAgenda n'a pas de champ « catégorie » dédié sur les événements ;
* les événements sans lieu physique (`location` absent) sont ignorés ;
* URL source reconstruite (`https://openagenda.com/agendas/{agendaUid}/events/{slug}`),
  faute d'URL canonique fournie directement par l'API.

Tests : `OpenAgendaCollectorTest` (7 cas, `MockRestServiceServer`, aucun
appel réseau réel) — conversion valide, événement sans lieu ignoré,
liste vide, échec réseau, configuration manquante (clé/agenda).

Non compilé/testé en sandbox : Maven Central inaccessible, comme pour
LL-5002 et LL-3012.

## LL-5005 — Pipeline de normalisation ✅

**Dépendance :** LL-5004.

`collector/application/NormalizationService.java` : `normalize(CollectedActivity)`
→ `Optional<Activity>`, en mémoire uniquement (pas de persistance, hors
périmètre — LL-5008). Rejette (`Optional.empty()`) si `title` vide/nul,
`startDate` nul, ou latitude/longitude hors plage ; convertit sinon.
`description`/`endDate`/`category` restent optionnels, comme sur
`Activity`.

Volontairement séparé d'`ActivityService`, non modifié (critère
d'acceptation : « aucune logique spécifique à un collecteur dans
ActivityService »). Les bornes de latitude/longitude sont dupliquées
plutôt que factorisées avec celles déjà présentes dans `ActivityService`
— un refactoring de ce service n'est pas demandé par ce ticket.

⚠️ Décision à valider : statut `PUBLISHED` attribué par défaut aux
activités normalisées, plutôt que `PENDING` (statut par défaut des
contributions manuelles, voir `ActivityService#createActivity`). Une
source réelle est choisie pour sa fiabilité (critères LL-5006 :
stabilité, qualité des données) et il n'existe aucune interface de
modération dans ce sprint — avec `PENDING`, les activités importées
n'apparaîtraient jamais sur la carte (LL-5011).

Tests : `NormalizationServiceTest` (7 cas — conversion valide, titre
vide/nul, date de début nulle, latitude/longitude hors plage, champs
optionnels absents), sans mock (fonction pure).

## LL-5004 — Définir le modèle de données importées ✅

**Dépendance :** LL-5003.

`collector/domain/CollectedActivity.java` : record avec les 10 champs
requis (titre, description, dates début/fin, catégorie, latitude,
longitude, URL source, identifiant externe, source — `source` en
`String`, nom de la source, cohérent avec la décision `getSourceName()`
de LL-5003). Simple porteur de données, comme `Activity`/`Source` :
aucune validation, aucun test dédié (pas de logique à tester),
conformément au reste du projet. La conversion vers `Activity` et le
rejet des données invalides sont explicitement hors périmètre — c'est
le pipeline de normalisation, LL-5005.

`COLLECTOR_CONTRACT.md` mis à jour : la référence anticipée à
`CollectedActivity` (LL-5003) est confirmée sans ajustement.

Toujours pas de code pour l'interface `Collector` elle-même : ni
LL-5004 ni les tickets précédents ne le demandent explicitement — voir
`COLLECTOR_CONTRACT.md`, création différée au plus tard à LL-5006.

## LL-5003 — Définir le contrat Collector ✅

Aucune dépendance déclarée dans `SPRINT_5.md` (indépendant de LL-5001/
LL-5002), mais LL-5004 en dépend.

`docs/02_Architecture/COLLECTOR_CONTRACT.md` : interface `Collector`
documentée (`getSourceName()`, `collect()`), pas de code — aucun ticket
dédié à un « module Collector » n'existe dans le sprint (contrairement
à `Source`), donc l'interface sera créée en Java au moment où un ticket
en a réellement besoin (LL-5004 ou LL-5006 selon l'usage réel).

⚠️ Décisions posées, à valider :
* identification de la source par `getSourceName()` (String) plutôt que
  par `Source.id`, pour ne pas coupler le collecteur à la persistance ;
* le type de retour `CollectedActivity` est une référence anticipée au
  modèle que LL-5004 doit créer — la signature exacte ne sera figée
  qu'à ce moment-là ;
* aucun type d'exception imposé pour la gestion d'erreurs : différé à
  LL-5006 (premier collecteur réel), une fois la source retenue connue.
