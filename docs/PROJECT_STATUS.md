# LocalLife - Project Status

**Version :** 0.7.0
**Dernière mise à jour :** 2026-08-17 (préparation Sprint 7)

---
## Phase actuelle
🟡 Phase 2 — Validation du MVP

Le cadrage fonctionnel et technique est terminé. La construction du périmètre MVP (Sprints 0 à 6) est terminée. Le projet entre maintenant dans une phase de validation du MVP avant toute nouvelle évolution.

Sprint 0 (socle technique backend) terminé. Sprint 1 (première fonctionnalité visible) terminé. Sprint 2 (utilisateurs et catégories) terminé. Sprint 3 (authentification, géocodage) terminé. Sprint 4 (recherche et découverte géographique) terminé. Sprint 5 (alimentation réelle : sources, collecteur OpenAgenda, import) terminé. Sprint 6 (qualité des données et administration minimale : validation renforcée, statut de modération, contrôle administratif, source identifiable, premier jalon Food Truck, tests de non-régression) terminé.

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
 | Collecteurs                | ✅ Terminé (Sprint 5) |
 | Modération / administration | ✅ Terminé (Sprint 6) |
 | Phase 1 (Socle Technique + MVP) | ✅ Terminé    |
 | Phase 2 (Validation MVP)    | 🟡 En cours   |
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
Tous les sprints (0 à 6) sont terminés.

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
## Phase 2 — Validation du MVP
**Statut :** 🟡 En cours

Priorité actuelle : exécuter le Sprint 7 et vérifier le MVP de bout en bout.

Ordre :

1. définir le protocole de validation ; ✅ (`LL-7001`)
2. permettre un déclenchement contrôlé de l'import réel ;
3. vérifier import → modération → carte ;
4. vérifier recherche, contribution, authentification et Food Truck ;
5. corriger uniquement les blocages ;
6. décider si le MVP est prêt pour une première bêta.

Référence : `docs/05_Sprints/SPRINT_7.md`.

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

Statut : ✅ Terminé.

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
# Sprint 6

Statut : ✅ Terminé.

Objectif : fiabiliser les données réellement présentes dans LocalLife
après l'intégration de la première source externe (Sprint 5) — voir
`docs/05_Sprints/SPRINT_6.md`.

## LL-6011 — Documentation ✅

**Priorité : Haute. Dépendance :** tous les tickets précédents.

Mis à jour, conformément à `SPRINT_6.md` :
* **`PROJECT_STATUS.md`** (ce fichier) : version `0.3.0` → `0.6.0`
  (champ resté figé depuis un sprint antérieur alors que
  `CHANGELOG.md` affichait déjà `0.4.0` en tête — corrigé au passage) ;
  section « Phase actuelle » et tableau « Avancement » mis à jour
  (mentionnaient encore le Sprint 4 comme dernier sprint terminé,
  « Collecteurs » comme « prochaine étape » alors que livrés en
  Sprint 5).
* **`docs/04_Project/ROADMAP.md`** (fichier canonique) et
  **`docs/ROADMAP.md`** (copie parallèle maintenue depuis LL-5012,
  voir ci-dessous) : Sprint 6 ajouté, section « Phase 2 » nuancée
  (les « pistes » qualité des données/administration/food trucks y
  étaient déjà listées ; Sprint 6 en a livré un premier socle, sans
  que Phase 2 elle-même — validation par de vrais utilisateurs —
  ne soit lancée).
* **`CHANGELOG.md`** : entrée `0.6.0 - Sprint 6` ajoutée. Écart repéré
  et corrigé au passage : `0.5.0 - Sprint 5` n'avait **jamais** été
  ajoutée (le fichier passait directement de `0.4.0 - Sprint 4` à
  `0.3.0 - 2026-08-11`, laissant croire qu'aucun changement notable
  n'avait eu lieu en Sprint 5) — comblé.
* **Documentation API** : annotations Swagger/OpenAPI (`@Operation`,
  `@ApiResponses`) ajoutées à `SourceController` et
  `FoodTruckController`, pour la même richesse de documentation
  générée que `ActivityController`/`AdminActivityController` — ces
  deux contrôleurs avaient été volontairement laissés sans
  annotations lors de LL-6007/LL-6009 (miroir de `CategoryController`,
  le seul précédent de contrôleur minimal du projet à l'époque), mais
  `AdminActivityController` avait entre-temps établi un niveau de
  documentation plus riche qu'il est apparu cohérent de généraliser.
* **`docs/02_Architecture/ARCHITECTURE.md`** : module `foodtruck`
  ajouté à la liste des modules ; section Frontend complétée (second
  type de marqueur sur la carte).
* **`README.md`** (racine) : section « Sprint 6 » ajoutée, même format
  que les sections précédentes. Deux inexactitudes corrigées au
  passage : la section « Recherche géographique » mentionnait encore
  `status` comme paramètre de `/nearby`/`/within-bounds`, retiré
  depuis LL-6004 ; la section Sprint 2 affirmait « pas de modération à
  ce stade », devenu faux depuis LL-6003.

**Au-delà du périmètre strict du ticket** (même justification que
LL-5012 : ces fichiers existent précisément pour ce type
d'information) : une entrée ajoutée à `docs/DETTE_TECHNIQUE.md` — la
duplication `docs/04_Project/ROADMAP.md`/`docs/ROADMAP.md`, déjà
repérée sans être résolue lors de LL-5012, toujours non consolidée
(décision structurante — suppression d'un fichier — hors périmètre
d'un ticket de documentation, à confirmer avec Alex).

Non compilé (documentation + annotations uniquement, aucune logique
modifiée) : brace-checker Python (79 fichiers, OK) comme proxy de
compilation habituel pour les deux contrôleurs annotés.

**Definition of Done du sprint** (`SPRINT_6.md`) : les dix points sont
couverts par LL-6001 à LL-6011 (données invalides mieux contrôlées,
statut de publication, visibilité publique restreinte, consultation/
publication/rejet administrateur, source identifiable, food truck
référencé et visible sur la carte, tests de non-régression écrits,
documentation à jour). Réserve constante depuis le début du sprint :
aucun test backend n'a pu être réellement compilé/exécuté en sandbox
(Maven indisponible) — seul le brace-checker Python a servi de proxy ;
le frontend, en revanche, a pu être réellement compilé et testé
(`tsc`/`npm run build`) à partir de LL-6009.

## LL-6010 — Tests de non-régression ✅

**Priorité : Haute. Dépendance :** LL-6004, LL-6006, LL-6009 (tous terminés).

**Audit préalable** : les neuf points listés dans `SPRINT_6.md` (« ###
Tester ») étaient déjà couverts, au moins en partie, par les tests des
tickets précédents :
* accès administrateur / accès utilisateur standard / publication /
  rejet : `AdminActivityControllerIntegrationTest` (LL-6005/LL-6006) ;
* food truck visible sur la carte :
  `FoodTruckControllerIntegrationTest` (LL-6009) ;
* recherche géographique : `ActivityControllerIntegrationTest`
  (LL-4001-LL-4014, vérifie les codes HTTP) et
  `ImportedActivityVisibilityIntegrationTest` (LL-5011, vérifie le
  contenu via `ActivityService` directement, pas via HTTP).

**Écart réel identifié** : aucun test bout en bout, au niveau HTTP,
avec des données réellement créées, ne vérifiait explicitement qu'une
activité `PENDING`/`REJECTED` est **absente** de `GET /nearby` et
`GET /within-bounds` (LL-6004 n'était couvert qu'au niveau unitaire —
`ActivityServiceTest` vérifie que le repository est toujours appelé
avec `PUBLISHED`, une garantie architecturale forte mais qui ne
protège pas contre une régression qui contournerait
`ActivityService`).

* **`NonRegressionIntegrationTest`** (nouveau, `integration/`) : une
  suite dédiée, volontairement redondante par endroits avec les suites
  existantes — voir sa javadoc pour la justification de ce choix (faire
  correspondre chacun des neuf points du ticket à une méthode de test
  explicitement nommée et repérable en un seul endroit, plutôt que de
  se fier à une couverture dispersée). Sept méthodes couvrent les neuf
  points : publication+visibilité publiée, en-attente invisible,
  rejet+invisibilité rejetée, accès admin, accès utilisateur standard,
  food truck visible, recherche géographique inchangée (filtres
  combinés inclus).
* Comble l'écart réel identifié ci-dessus : `pendingActivity_
  ShouldNotBeVisibleInPublicSearch` et `rejectedActivity_
  ShouldNotBeVisibleInPublicSearch` créent réellement une activité via
  `POST /api/v1/activities`, la font transiter par
  `PATCH .../reject` le cas échéant, puis vérifient son absence du
  corps de réponse HTTP de `/nearby` et `/within-bounds` — jamais
  vérifié bout en bout avant ce ticket.
* **Correction appliquée en cours de route** (LL-6009, avant push,
  donc sans impact sur l'historique) : `FoodTruckControllerIntegrationTest`
  utilisait `.expectBody(String.class).value(lambda)`, un enchaînement
  jamais utilisé ni validé ailleurs dans le projet (seul `.expectBody
  (Type.class).returnResult().getResponseBody()` l'était,
  voir `AdminActivityControllerIntegrationTest`) — remplacé par ce
  dernier pattern, plus sûr en l'absence de compilation Maven réelle
  en sandbox. Même correction appliquée par précaution dans
  `NonRegressionIntegrationTest`.
* Aucun changement de code de production : ticket de test uniquement,
  conforme à son intitulé.

Non exécuté en sandbox : toujours pas de Maven disponible (ni binaire
installé, ni accès réseau à Maven Central) — brace-checker Python
utilisé comme à l'habitude comme proxy de compilation (79 fichiers,
tous équilibrés).

## LL-6009 — Première intégration Food Truck ✅

**Dépendance :** LL-6008.

**Décision d'extensibilité (demande explicite d'Alex, avant ce ticket)** :
prévoir de pouvoir ajouter d'autres modules de ce type plus tard.
Traduite en un **patron reproductible documenté**, pas en une
abstraction générique construite par anticipation (rejetée : avec
seulement deux types de points sur la carte — `Activity` et
`FoodTruck` — une couche d'abstraction configurable ajouterait de la
complexité sans bénéfice mesurable, à réévaluer si un troisième type
concret est demandé) :
* backend : un nouveau module autonome par type
  (`domain`/`application`/`infrastructure`/`api`), documenté dans
  `FOOD_TRUCK_CONTRACT.md` (LL-6008) ;
* frontend : patron en 4 étapes documenté directement dans
  `App.tsx` (javadoc de `FOOD_TRUCK_MARKER_ICON`) — interface
  TypeScript dédiée, `useEffect` de récupération isolé, icône
  `divIcon` dédiée, bloc de marqueurs supplémentaire dans le même
  `<MapContainer>` (jamais un second composant carte).

**Backend, nouveau module `foodtruck`** (structure calquée sur
`source`, comme prévu par la décision structurante de LL-6008) :
* **`FoodTruck`** (record), **`FoodTruckRepository`**
  (`Repository<FoodTruck, Long>`, `save`/`findByStatus`, même style
  que `SourceRepository`), **`FoodTruckService`**
  (`createFoodTruck`/`findAllPublished`), **`FoodTruckController`**
  (`GET`/`POST /api/v1/foodtrucks`).
* **Migration `V12__create_food_truck_table.sql`** : table `food_truck`
  neuve avec contrainte `CHECK` sur `status` posée dès la création
  (contrairement à `activity`, où V11 est arrivée après coup) — mêmes
  trois valeurs `PENDING`/`PUBLISHED`/`REJECTED` qu'`Activity.status`
  (LL-6003), défaut DB `'PENDING'` (filet de sécurité, voir migration).
* ⚠️ **Décision, à valider avec Alex** : le statut choisi par
  l'application à la création est **`PUBLISHED`** (pas `PENDING` comme
  pour une activité manuelle) — voir la javadoc de `FoodTruck` pour la
  justification complète : aucun endpoint de modération (publier/
  rejeter) n'existe pour les food trucks dans ce ticket ; un statut par
  défaut `PENDING` sans aucun moyen de le faire évoluer aurait rendu
  tout food truck créé invisible, contredisant directement le critère
  d'acceptation « visibilité sur la carte ». Si une modération des food
  trucks est souhaitée, un ticket dédié pourra réutiliser le mécanisme
  déjà en place pour les activités (LL-6005/LL-6006).
* **Pas de géocodage d'adresse** (contrairement à
  `ActivityService#createActivity`, LL-3012) : la création reçoit
  directement `latitude`/`longitude` — décision documentée dans
  `FoodTruckService`, pour rester strictement dans le périmètre du
  ticket (pas demandé par les critères d'acceptation).
* **`POST /api/v1/foodtrucks`** protégé (`SecurityConfig`,
  `.authenticated()`) — même posture que `POST /api/v1/activities`
  (LL-3008) : n'importe quel utilisateur connecté peut contribuer.
  `GET /api/v1/foodtrucks` reste public, ne retourne que les
  `PUBLISHED`, même convention que les activités depuis LL-6004.
* **Pas d'endpoint `GET /api/v1/foodtrucks/{id}`** : non demandé par
  les critères d'acceptation du ticket, volontairement omis pour ne
  pas dépasser le périmètre.

**Frontend (`App.tsx`)** :
* Nouvel état `foodTrucks`, `useEffect` de récupération isolé de celui
  des activités (pas de filtres, un seul appel au montage — les
  critères d'acceptation ne demandent aucun filtrage pour les food
  trucks). Échec de la requête traité silencieusement (la carte reste
  utilisable sans food trucks) — délibérément différent du traitement
  d'erreur des activités (`searchError`), les food trucks restant un
  contenu secondaire de la carte.
* **`FOOD_TRUCK_MARKER_ICON`** : `divIcon` (HTML/CSS, camion 🚚 sur
  fond orange, classe `.food-truck-marker` dans `styles.css`) plutôt
  qu'une image externe — évite tout problème de résolution d'assets
  Leaflet avec Vite pour un seul marqueur. Distinction visuelle avec
  une activité (critère d'acceptation explicite). Distinction
  fonctionnelle : popup sans date (un food truck n'est pas un
  événement daté).
* Bloc de marqueurs food trucks ajouté dans le même `<MapContainer>`
  que les activités — un seul système cartographique, conformément à
  la contrainte MVP de `SPRINT_6.md`.

**Tests** : unitaires (`FoodTruckServiceTest`, 8 cas — création valide,
validations nom/catégorie/coordonnées, restriction `PUBLISHED` ;
`FoodTruckControllerTest`, 3 cas) + intégration bout en bout
(`FoodTruckControllerIntegrationTest`, 5 cas — `GET` public, `POST`
authentifié réussi, visibilité immédiate après création sans
modération, `401` sans token, `400` sur validation).

Non exécuté en sandbox pour le backend : ni compilation Maven (toujours
indisponible), ni requête sur une base réelle. **Le frontend, en
revanche, a pu être réellement vérifié** cette fois (`npm install`
fonctionnel dans ce sandbox, contrairement à Maven) : `tsc --noEmit`
et `npm run build` passent tous les deux sans erreur.

## LL-6008 — Définir le modèle Food Truck ✅

**Ticket de conception/documentation uniquement** (comme LL-5001 pour le
modèle `Source`) : aucun code, aucune migration, aucun endpoint.

* **`docs/02_Architecture/FOOD_TRUCK_CONTRACT.md`** (nouveau) : modèle
  `FoodTruck` — `id`, `name`, `description`, `latitude`/`longitude`,
  `category` (chaîne libre, même convention qu'`Activity.category`),
  `contact` (URL **ou** contact, champ texte libre unique, sans
  validation de format stricte), `status` (réutilise les trois valeurs
  `PENDING`/`PUBLISHED`/`REJECTED` déjà établies par `Activity`
  depuis LL-6003, pour rester compatible avec le mécanisme de
  modération existant si LL-6009 ou un ticket ultérieur souhaite le
  réutiliser).
* **Décision structurante, à valider avec Alex avant LL-6009** : module
  `FoodTruck` **séparé** (nouveau module `foodtruck`, sur le schéma
  domain/application/infrastructure/api déjà utilisé par `source`/
  `category`), plutôt qu'une extension d'`Activity` avec un
  discriminant de type. Raison principale : `startDate`/`endDate` et
  `sourceId`/`importKey` n'ont pas de sens pour un food truck (pas
  d'événement daté, pas d'import externe prévu à ce stade) — les
  ajouter en colonnes nullables sur `Activity` aurait introduit des
  branches conditionnelles dans `ActivityService`/
  `NormalizationService`/`ImportService`, contraire à l'interdiction
  explicite de `SPRINT_6.md` (« pas de module complexe ») et au
  principe « pas de changement spéculatif ». La contrainte du ticket
  (« sans créer un second système cartographique ») porte sur le
  résultat visible pour l'utilisateur (une seule carte, prévu en
  LL-6009 par un second appel API affiché sur le même composant
  Leaflet), pas sur une table unique côté modèle.
* **Aucun lien avec `Source`** : décision documentée, à valider —
  aucun food truck importé n'est prévu par ce ticket ni par
  `SPRINT_6.md`.
* Aucun test à écrire (documentation uniquement).

## LL-6007 — Gestion minimale des activités importées ✅

**Dépendance :** LL-6006.

**Constat de départ (audit rapide avant implémentation)** : 3 des 4
critères d'acceptation étaient déjà satisfaits par LL-5008/LL-5009,
sans rapport avec LL-6006 :
* *activité manuelle conservée* / *activité importée conservée avec sa
  source* : `Activity.sourceId` toujours renseigné depuis LL-5008
  (source réservée `MANUAL` pour les contributions manuelles) ;
* *aucune duplication de données* : `ImportService` retrouve toute
  activité déjà importée via `findBySourceIdAndImportKey` et la met à
  jour plutôt que d'en recréer une (LL-5008).

Seul écart réel : *source identifiable*. `sourceId` était bien présent
dans chaque réponse JSON contenant une `Activity` (champ de record,
sérialisé automatiquement), mais rien ne permettait à un consommateur
de l'API de résoudre cet id en un nom/type lisible — aucun
`SourceController` n'existait (`SOURCE_CONTRACT.md`, LL-5001,
l'excluait alors explicitement du périmètre).

* **`SourceController`** (nouveau, `source/api`) : `GET /api/v1/sources`
  (miroir exact de `CategoryController#getAllCategories`, pour rester
  cohérent avec l'unique autre module de référencement simple du
  projet) et `GET /api/v1/sources/{id}` (réutilise
  `SourceService#getSourceById`, méthode déjà existante depuis LL-5002
  mais jusqu'ici inutilisée par aucun contrôleur — répond concrètement
  au besoin de résolution). `404` sans corps si id inconnu, même
  convention que `ActivityController#getActivityById`.
* **Non protégé** : une source n'est pas une donnée sensible (comme les
  catégories) ; `SecurityConfig` inchangé, `anyRequest().permitAll()`
  couvre déjà ce nouveau chemin par défaut — javadoc de la classe mise
  à jour pour tracer cette décision sans modification de code.
* **Aucun changement** sur `SourceService`, `SourceRepository`,
  `Source`, `ImportService`, `ActivityService` : les trois autres
  critères étaient déjà couverts, conformément à la règle « une seule
  responsabilité par ticket, pas de changement spéculatif ».
* **Tests** : unitaires (`SourceControllerTest`, 4 cas — liste pleine/
  vide, résolution par id existant/inexistant) + intégration bout en
  bout légère (`SourceControllerIntegrationTest`, 3 cas — accès sans
  JWT, 200 sur la source `MANUAL` d'id 1 [migration `V8`], 404 sur id
  inconnu). Pas de tests de sécurité (401/403) : rien à couvrir, aucune
  protection ajoutée.

Non exécuté en sandbox : ni compilation (Maven absent, pas d'accès
réseau à Maven Central), ni requête sur une base réelle — même
limitation que tous les tickets précédents.

## LL-6006 — Publier ou rejeter une activité ✅

**Dépendance :** LL-6005.

* **`PATCH /api/v1/admin/activities/{id}/publish`** et **`PATCH
  /api/v1/admin/activities/{id}/reject`** (`AdminActivityController`,
  même contrôleur que LL-6005) : transitions `PENDING → PUBLISHED` et
  `PENDING → REJECTED`, seules prévues par la javadoc du champ `status`
  d'`Activity` depuis LL-6003. Réservés au rôle `ADMIN`, même mécanisme
  que `GET /api/v1/admin/activities` (LL-6005).
* **`ActivityService#publish`/`#reject`** : délèguent à une méthode
  privée commune `transitionStatus` — charge l'activité, vérifie qu'elle
  est bien `PENDING`, sauvegarde une copie avec le nouveau statut.
  Réutilise le pattern « charger, copier avec le champ modifié, `save`
  avec `id` déjà renseigné (= UPDATE, pas INSERT) » déjà exploité par
  `ImportService#archiveMissingActivities` (LL-5009) pour la transition
  vers `ARCHIVED` — pas de nouvelle méthode de repository nécessaire,
  `findById`/`save` existants suffisent.
* Activité inexistante → `Optional.empty()` côté service, `404` sans
  corps côté contrôleur (même convention que
  `ActivityController#getActivityById`).
* ⚠️ **Décision prise, à valider avec Alex** (point resté ouvert dans
  `NEXT_TASK.md` avant ce ticket) : que faire si l'activité existe mais
  n'est pas `PENDING` (déjà `PUBLISHED`/`REJECTED`, transition non
  prévue par LL-6003) ? Choix retenu : `IllegalArgumentException` →
  `400`, même convention que toute autre erreur de validation métier
  dans ce service (pas de no-op silencieux, pour ne pas laisser croire
  à l'appelant qu'une transition a eu lieu ; pas de nouveau statut HTTP
  introduit). Aucune machine à états ajoutée : une seule vérification
  directe du statut courant, conforme à l'interdiction explicite de
  `SPRINT_6.md` (« pas de workflow de modération complexe »).
* **Tests** (critère d'acceptation explicite du ticket) : unitaires
  ajoutés dans `ActivityServiceTest` (transition réussie, activité
  inexistante, transition invalide pour `publish` et pour `reject`,
  6 cas) ; intégration ajoutés dans
  `AdminActivityControllerIntegrationTest` (200 avec statut mis à jour,
  404 sur id inconnu, 400 sur transition invalide, 401 sans JWT, 403
  avec un JWT de rôle `USER`, pour `publish` et `reject`, 10 cas) —
  même structure bout en bout que LL-6005 (token `ADMIN` construit
  directement, token `USER` obtenu via le flux public réel).

Non exécuté en sandbox : ni compilation (Maven absent, pas d'accès
réseau à Maven Central), ni requête sur une base réelle — même
limitation que tous les tickets précédents.

## LL-6005 — Contrôle administratif minimal ✅

**Dépendance :** LL-6003 (ticket indépendant de LL-6004 dans
`SPRINT_6.md`, mais implémenté après par cohérence de workflow).

* **`GET /api/v1/admin/activities?status=PENDING`** (nouveau
  `AdminActivityController`, distinct d'`ActivityController` : chemin,
  protection et objectif différents — voir sa javadoc) : liste les
  activités correspondant exactement au statut demandé, sans filtre
  géographique. `status` volontairement **obligatoire**, aucune valeur
  par défaut (contrairement aux endpoints publics) — lister sans
  distinction reviendrait à réintroduire `GET /api/v1/activities`,
  déjà disponible.
* **`ActivityService#findByStatus`** : validation (statut obligatoire,
  doit être une des trois valeurs formalisées en LL-6003) + délégation
  au repository. `KNOWN_STATUSES` réintroduit (retiré en LL-6004,
  puisqu'il ne servait plus à rien côté recherche publique) — reprend
  ici un rôle différent : valider ce paramètre administratif, pas
  filtrer une recherche publique.
* **`ActivityRepository#findByStatus`** : requête dérivée du nom de la
  méthode (comme `findBySourceId`), aucun `@Query` nécessaire.
* **`SecurityConfig`** : `GET /api/v1/admin/activities` protégé par
  `.hasRole("ADMIN")`, même mécanisme que `POST /api/v1/users`
  (LL-3008) — vérifié directement dans le code avant d'écrire cette
  note (une mémoire précédente s'était révélée obsolète sur ce point
  lors de LL-6004).
* **Tests de sécurité** (critère d'acceptation explicite du ticket) :
  nouveau `AdminActivityControllerIntegrationTest` (bout en bout,
  serveur embarqué, même approche que `AuthenticationFlowIntegrationTest`
  LL-3014) — 401 sans JWT, **403 avec un JWT valide mais de rôle
  `USER`** (inscrit via le flux public réel), 200 avec un JWT `ADMIN`.
  ⚠️ Aucun endpoint ne permet de créer un compte `ADMIN`
  (l'inscription publique crée toujours un `USER`, voir
  `AuthService#register`) : le token administrateur est construit
  directement dans le test, comme `AuthenticationFlowIntegrationTest`
  le fait déjà pour un token expiré (même technique, `role=ADMIN` et
  expiration dans le futur). Tests unitaires ajoutés dans
  `ActivityServiceTest` pour la validation du paramètre `status`.

Non exécuté en sandbox : ni compilation (Maven absent, pas d'accès
réseau à Maven Central — même limitation que tous les tickets
précédents), ni requête sur une base réelle.

## LL-6004 — Exclure les activités non publiées de la carte publique ✅

**Dépendance :** LL-6003.

* **`ActivityService#findNearby`/`#findWithinBounds`** (les deux
  endpoints publics de recherche, `GET /nearby` et `GET /within-bounds`,
  sans authentification) : le paramètre `status` a été **retiré** de
  ces deux méthodes plutôt que simplement doté d'un défaut — elles
  demandent désormais systématiquement `PUBLISHED` au repository
  (constante `PUBLIC_STATUS`), sans que l'appelant puisse demander
  autre chose. Choix plus strict que le correctif envisagé dans
  `DETTE_TECHNIQUE.md` (qui ne visait qu'`ARCHIVED` par défaut) : avec
  la modération introduite en LL-6003, laisser `status` filtrable
  aurait permis à n'importe quel visiteur de consulter la file de
  modération via `?status=PENDING` sur un endpoint public.
* **`ActivityController`** : paramètre `status` retiré des deux
  endpoints (`@RequestParam`, javadoc, `@Parameter`/`@ApiResponses`
  OpenAPI) — changement d'API volontaire (rétrocompatibilité non
  demandée par le ticket), une future consultation par statut passera
  par un endpoint dédié (LL-6005), pas par ceux-ci.
* **Portée volontairement limitée** aux deux endpoints appelés
  « recherche » dans le projet (`/nearby`, `/within-bounds`), seuls
  visés par la dette technique résolue et par la formulation du
  ticket. ⚠️ Point signalé, non traité ici : `GET /api/v1/activities`
  (liste complète sans filtre) et `GET /api/v1/activities/{id}`
  restent inchangés et peuvent encore exposer des activités
  `PENDING`/`REJECTED` — pour `{id}`, c'est nécessaire (LL-6005/LL-6006
  auront besoin de consulter une activité non publiée pour la
  modérer) ; pour la liste complète sans filtre, aucun ticket ne le
  couvre à ce stade. À signaler à Alex si un besoin de confidentialité
  plus large est attendu.
* **`DETTE_TECHNIQUE.md`** : entrée « activités ARCHIVED visibles par
  défaut » marquée résolue (la solution retenue couvre `ARCHIVED` au
  passage, puisqu'il n'est jamais `PUBLISHED`).
* **Contrats mis à jour** : `GEO_SEARCH_CONTRACT.md` et
  `BOUNDING_BOX_SEARCH_CONTRACT.md` (LL-4001/LL-4006) — paramètre
  `status` retiré de la documentation, note explicite référençant
  LL-6004 plutôt que suppression silencieuse.
* Tests : réécriture complète des tests de recherche dans
  `ActivityServiceTest`/`ActivityControllerTest` (signatures changées,
  suppression des cas `status` obsolètes, 2 nouveaux tests vérifiant
  que `PUBLISHED` est systématiquement demandé au repository quels que
  soient les autres filtres) ; `ImportedActivityVisibilityIntegrationTest`
  (LL-5011) mis à jour de la même façon, avec un test renommé
  vérifiant qu'une activité importée apparaît en recherche publique
  sans qu'aucun paramètre de statut ne soit nécessaire.

Non exécuté en sandbox : ni compilation (Maven absent, pas d'accès
réseau à Maven Central — même limitation que tous les tickets
précédents), ni requête sur une base réelle.

## LL-6003 — Ajouter le statut de modération ✅

**Dépendance :** LL-6002.

* **Migration `V11__enforce_activity_status.sql`** : `status` passe de
  colonne libre sans contrainte à `NOT NULL DEFAULT 'PENDING'` +
  `CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED'))`. Un `UPDATE`
  de secours convertit d'éventuelles lignes `NULL` en `PENDING` avant
  d'ajouter la contrainte `NOT NULL` — ne devrait rien changer en
  pratique (le code applicatif renseigne déjà toujours `status`
  explicitement, jamais laissé à `NULL`), mais impossible à confirmer
  sans accès à une base réelle en sandbox ; à vérifier par Alex avant
  application.
* **`Activity`** : javadoc du champ `status` étoffée — les trois
  valeurs MVP, leur rôle, et les transitions minimales documentées :
  `PENDING → PUBLISHED` et `PENDING → REJECTED` uniquement (aucun
  retour en arrière, aucune transition depuis `PUBLISHED`/`REJECTED`),
  conformément au critère d'acceptation « aucune machine à états
  complexe ». Aucun endpoint ne permet encore de déclencher ces
  transitions — prévu en LL-6006, hors périmètre de ce ticket.
* **`ActivityService`** : `KNOWN_STATUSES` (validation du paramètre
  `status` de la recherche géographique, LL-4003) étendu à `REJECTED` ;
  aucune règle de visibilité appliquée ici — l'exclusion des activités
  non `PUBLISHED` des recherches publiques reste le périmètre de
  LL-6004, volontairement non anticipé.
* Pas de nouvelle règle de validation métier (`PENDING`/`PUBLISHED`
  restent les seules valeurs jamais produites par le code applicatif à
  ce stade — `REJECTED` n'existera en pratique qu'à partir de LL-6006).
* Tests unitaires ajoutés : `findNearby`/`findWithinBounds` acceptent
  désormais `REJECTED` comme valeur de `status` sans lever d'exception
  (2 nouveaux cas dans `ActivityServiceTest`).

Non exécuté en sandbox : ni compilation (Maven absent, pas d'accès
réseau à Maven Central — même limitation que tous les tickets
précédents), ni migration appliquée à une base réelle.

## LL-6002 — Renforcer la validation de `Activity` ✅

**Dépendance :** LL-6001. Décision reçue d'Alex entre les deux
tickets : ajouter le champ `url` manquant (point laissé ouvert par
l'audit) plutôt que le différer.

* **Champ `url` ajouté à `Activity`** (dernier champ du record, même
  approche que `sourceId`/`importKey` en LL-5008) + migration
  `V10__add_url_to_activity.sql` (`VARCHAR(512)` nullable, alignée sur
  `source.url`). `NormalizationService` reporte désormais
  `CollectedActivity#sourceUrl` sur `Activity#url` au lieu de le perdre
  (problème n°8 de l'audit) ; `ImportService` le propage lors des
  mises à jour/changements de statut. Toujours `null` pour une création
  manuelle (le formulaire de contribution n'en demande pas — hors
  périmètre de ce ticket).
* **`NormalizationService`** (import) : validation étendue au-delà de
  LL-5005 — longueur max de `title` (255, alignée sur la colonne),
  cohérence `endDate >= startDate` (problème n°3), `category` non
  vide/blanche si renseignée (problème n°7, interprétation minimale vu
  qu'aucune liste de référence n'existe — voir javadoc), `url` valide
  (`http`/`https`, syntaxe correcte) si renseignée (problème n°8).
* **`ActivityService#createActivity`** (contribution manuelle) :
  `title` désormais obligatoire et validé (non vide, ≤ 255 caractères —
  problème n°1, jusqu'ici totalement absent sur ce chemin), `category`
  non vide/blanche si renseignée, coordonnées revalidées après
  géocodage par défense en profondeur (peu probable en pratique,
  Nominatim ne renvoyant que des coordonnées réelles).
* ⚠️ Décision non tranchée par ce ticket, signalée dans le code : la
  notion de « catégorie valide » reste minimale (non vide/blanche)
  tant que `category` est un champ libre sans lien avec la table
  `category` — une validation plus stricte impliquerait un changement
  de modèle hors périmètre.
* Tests unitaires ajoutés : `NormalizationServiceTest` (9 nouveaux cas
  — longueur de titre, cohérence de dates, catégorie blanche, URL
  invalide/valide/absente, propagation de l'URL) et `ActivityServiceTest`
  (6 nouveaux cas — titre nul/blanc/trop long, catégorie blanche,
  création réussie avec/sans catégorie).
* Tous les constructeurs `Activity` existants (17 occurrences, code +
  tests) mis à jour pour le nouveau champ. ⚠️ Vérifié par comptage
  programmatique des arguments (script Python contant les virgules de
  premier niveau de chaque appel `new Activity(...)`), **pas par
  compilation** : Maven absent de la sandbox et Maven Central hors des
  domaines réseau autorisés — même limitation que tous les tickets
  précédents. À confirmer par `mvn verify` de ton côté.

Non exécuté en sandbox : ni compilation, ni migration appliquée à une
base réelle.

## LL-6001 — Auditer la qualité des données ✅

**Dépendance :** aucune (premier ticket du sprint).

`docs/02_Architecture/DATA_QUALITY_AUDIT.md` : audit du code produit
par le Sprint 5 (modèle `Activity`, migrations Flyway,
`ActivityService`, `NormalizationService`, `OpenAgendaCollector`) —
pas d'accès à une base réelle en sandbox, donc audit par relecture de
code plutôt que par requête sur des données réelles (même limitation
que tous les tickets précédents), à confirmer par Alex avec un accès
réel. Pas de code modifié à ce stade, comme `SOURCE_CONTRACT.md` pour
LL-5001.

Neuf problèmes identifiés, classés par sévérité (voir le document pour
le détail complet) :
* **Haute** : `title` non validé à la contribution manuelle (vide/nul
  accepté) ; `sourceUrl` collecté par `OpenAgendaCollector` mais perdu
  à la normalisation — `Activity` n'a aucun champ pour porter une URL.
* **Moyenne** : aucune cohérence `startDate`/`endDate` vérifiée ;
  aucune détection de coordonnées non exploitables (type « Null
  Island ») au-delà de la simple plage `-90/90`/`-180/180` ; `category`
  non normalisée (casse/accents) ; aucune détection de doublon pour les
  contributions manuelles (seuls les imports sont couverts par
  `DeduplicationService`/l'index unique partiel de LL-5007/LL-5008).
* **Basse** : pas de longueur max applicative sur `title` ; aucune date
  aberrante détectée ; colonnes `latitude`/`longitude` nullables en
  base malgré un type Java non nullable.

Règles de validation proposées pour LL-6002 documentées dans le même
fichier, avec un point explicitement laissé à la décision d'Alex :
faut-il ajouter un champ `url` à `Activity` (le critère « URL valide
lorsqu'elle est fournie » de LL-6002 suppose qu'un tel champ existe,
ce qui n'est pas le cas actuellement) ?

Non compilé (documentation uniquement, aucun fichier source touché).

# Sprint 7

Statut : 🟡 En cours.

Objectif : passer du MVP techniquement construit à un MVP vérifiable en
conditions réelles, sans ajouter de nouvelle fonctionnalité produit
majeure — voir `docs/05_Sprints/SPRINT_7.md`.

## LL-7001 — Définir le protocole de validation du MVP ✅

**Dépendance :** aucune (premier ticket du sprint).

**Ticket de conception/documentation uniquement** (comme
`SOURCE_CONTRACT.md` en LL-5001 ou `FOOD_TRUCK_CONTRACT.md` en
LL-6008) : aucun code, aucune migration, aucun endpoint.

* **`docs/02_Architecture/MVP_VALIDATION_PROTOCOL.md`** (nouveau) : les
  dix scénarios minimum de `SPRINT_7.md` détaillés (objectif, étapes,
  critère de succès, critère d'échec, dépendance MVP explicite pour
  chacun), un critère de succès/échec global par scénario et pour le
  protocole dans son ensemble, et un rappel de traçabilité (résultats à
  consigner dans ce fichier au fil de LL-7003 à LL-7006, synthétisés en
  LL-7009).
* **Emplacement retenu** : `docs/02_Architecture/`, par analogie avec
  `DATA_QUALITY_AUDIT.md` (LL-6001) — même nature de document (audit/
  protocole transversal, pas un contrat d'API à proprement parler), déjà
  rangé dans ce dossier plutôt que dans un nouveau dossier `06_...`
  dédié à la validation.
* **Critères d'acceptation du ticket couverts** :
  * *protocole documenté* : section 4 du document (dix scénarios) ;
  * *critères succès/échec définis* : section 4 (par scénario) et
    section 5 (globaux, alignés sur la décision « MVP validé »/« MVP non
    validé » attendue en LL-7009) ;
  * *aucun scénario ne dépend d'une fonctionnalité hors MVP* : vérifié
    explicitement scénario par scénario (champ « Dépend de »,
    systématiquement une fonctionnalité déjà livrée en Sprints 0 à 6 et
    listée dans le MVP retenu), et section 2.3 dédiée à cette
    vérification.
* **Vérifications effectuées avant rédaction** (sandbox, lecture de
  code) : endpoints réels des contrôleurs (`ActivityController`,
  `AdminActivityController`, `AuthController`, `FoodTruckController`)
  relus directement dans le code plutôt que supposés à partir de la
  documentation, pour éviter de documenter un endpoint erroné dans le
  protocole (leçon du correctif SecurityConfig, Sprint 3) ; confirmé que
  `ImportService#importAll()` n'a toujours aucun déclencheur (cohérent
  avec la dette technique ouverte, planifiée pour `LL-7002`).
* **Aucun code modifié** : conforme à l'intitulé du ticket.

Non compilé (documentation uniquement, aucun fichier source touché) —
sans objet ici, pas de vérification brace-checker nécessaire.

## LL-7002 — Ajouter un déclencheur contrôlé du pipeline d'import ✅

**Dépendance :** LL-7001.

**Décision MVP** (`SPRINT_7.md`) : déclenchement manuel protégé par le
rôle `ADMIN`, pas de scheduler — appliquée telle quelle.

* **`collector/api/AdminImportController.java`** (nouveau) :
  `POST /api/v1/admin/import` → `ImportService#importAll()` → `200` avec
  la liste des `ImportResult` (un par `Collector` enregistré, un seul à
  ce stade : `OpenAgendaCollector`). Nouveau package `collector/api`
  (le module `collector` n'avait jusqu'ici aucune couche `api` — la
  seule couche manquante pour ce module, cohérent avec la structure
  domain/application/infrastructure déjà en place pour `activity`,
  `auth`, `foodtruck`, `source`).
* **`SecurityConfig.java`** : `.requestMatchers(HttpMethod.POST,
  "/api/v1/admin/import").hasRole("ADMIN")` ajouté, même mécanisme que
  les endpoints d'administration existants (LL-6005/LL-6006). Contrôleur
  distinct d'`AdminActivityController` plutôt qu'une méthode
  supplémentaire dessus (chemin et objectif différents — voir la javadoc
  du nouveau contrôleur, même raisonnement déjà documenté sur
  `AdminActivityController` pour sa propre séparation
  d'`ActivityController`).
* **Réutilisation sans duplication** (critère d'acceptation) : le
  contrôleur ne fait qu'appeler `ImportService#importAll()`, déjà
  responsable de l'orchestration complète `Collector → Normalisation →
  Validation → Persistance` depuis LL-5008 — aucune logique d'import
  écrite ici.
* **Journalisation** (critère d'acceptation) : déjà assurée par
  `ImportService` (LL-5009, un log `INFO` par source) — non dupliquée
  côté contrôleur ; le résultat détaillé est en plus renvoyé directement
  dans la réponse HTTP.
* **Comportement en cas d'échec de collecte** : `ImportService` capture
  déjà toute erreur de collecte et la traduit en `ImportResult` dégradé
  (`errors = 1`) plutôt que de laisser remonter une exception — ce
  contrôleur répond donc toujours `200` avec le détail, y compris en cas
  d'échec, plutôt qu'une erreur HTTP générique qui masquerait ce détail
  (documenté dans la javadoc du contrôleur).
* **Tests** :
  * `AdminImportControllerTest` (unitaire, `ImportService` mocké) :
    résultat renvoyé tel quel, résultat dégradé toujours `200`, liste
    vide si aucun collecteur enregistré.
  * `AdminImportControllerIntegrationTest` (bout en bout, contexte Spring
    réel, base réelle, `Collector` mocké — même isolation du seul point
    d'accès réseau que `ImportServiceIntegrationTest`, LL-5010) : accès
    `200` en `ADMIN` avec persistance réellement vérifiée en base (le
    pipeline existant est bien exécuté, pas seulement invoqué en
    apparence), `401` sans token, `403` avec un compte `USER` (flux
    d'inscription/connexion public réel, pas de token fabriqué pour ce
    cas — mêmes conventions qu'`AdminActivityControllerIntegrationTest`).
    Ne revérifie pas le détail du pipeline lui-même (création/mise à
    jour/rejet), déjà couvert exhaustivement par
    `ImportServiceTest`/`ImportServiceIntegrationTest` (LL-5008/LL-5010)
    — se concentre sur ce qui est propre à LL-7002 (protection par rôle,
    déclenchement réel via HTTP).

Vérification (proxy de compilation, Maven indisponible en sandbox) :
script Python de balance des accolades relancé sur l'ensemble des
fichiers `.java` (82 après ce ticket) ; 3 fichiers préexistants et non
modifiés par ce ticket ressortent en faux positif à cause d'une
limitation connue du script (regex contenant des quantificateurs du
type `{2}` dans une chaîne, ex. `OpenAgendaCollector.java` ligne 164) —
sans lien avec ce ticket. Les 4 fichiers créés/modifiés par LL-7002 ont
été vérifiés individuellement (comptage brut, sans ce risque de faux
positif ici) : tous équilibrés.

# Prochaine action

`LL-7002` est terminé.

La prochaine tâche est **LL-7003 — Valider le parcours de données réelles de bout en bout**, dans `docs/05_Sprints/SPRINT_7.md`. Ce ticket dépend d'un import réel exécuté par Alex (hors sandbox, via `POST /api/v1/admin/import`) sur un environnement avec les identifiants OpenAgenda configurés — la sandbox n'a pas accès au réseau externe requis.

Aucun Sprint 8 ne doit être défini avant la conclusion de Sprint 7.

## LL-5012 — Documentation ✅

**Dépendance :** tous les tickets précédents.

Mis à jour, conformément à `SPRINT_5.md` :
* `PROJECT_STATUS.md` (ce fichier) : statut du sprint passé à
  « ✅ Terminé », prochaine action mise à jour ;
* `docs/04_Project/ROADMAP.md` et `docs/ROADMAP.md` : Sprint 5 marqué
  terminé avec son contenu réel (le second contenait des informations
  obsolètes attribuant à tort le pipeline d'import au Sprint 3 —
  corrigé au passage) ;
* `docs/02_Architecture/ARCHITECTURE.md` : passé d'un résumé de deux
  lignes à une vue d'ensemble des modules, des couches, et du pipeline
  d'import ;
* `docs/02_Architecture/COLLECTOR_OPERATIONS.md` (nouveau) :
  documentation opérationnelle du collecteur — configuration,
  déclenchement (ou plutôt son absence), déduplication, stratégie de
  suppression douce, et guide pour ajouter un futur collecteur, y
  compris les deux pièges déjà rencontrés (constructeur `@Autowired`
  manquant, format de décalage horaire) ;
* `README.md` (racine) : section « Sprint 5 — Alimentation réelle »
  ajoutée, même format que les sections précédentes.

**Au-delà du périmètre strict du ticket** (justifié : ces fichiers
existent précisément pour ce type d'information, et les deux points
étaient déjà flagués comme ⚠️ dans plusieurs tickets précédents sans
jamais avoir été consolidés) : deux entrées ajoutées à
`docs/DETTE_TECHNIQUE.md` — le filtrage `status` absent par défaut
(activités `ARCHIVED` toujours visibles) et l'absence de déclencheur
pour `ImportService#importAll()`.

Non compilé (documentation uniquement, aucun fichier source touché).

## LL-5011 — Vérifier l'affichage sur la carte ✅

**Dépendance :** LL-5006, LL-5008.

Ticket de vérification, pas de nouvelle fonctionnalité (« aucune
fonctionnalité frontend spécifique aux collecteurs n'est requise » —
explicite dans `SPRINT_5.md`). `ImportedActivityVisibilityIntegrationTest`
(nouveau, même approche que `ImportServiceIntegrationTest` de LL-5010) :
importe une activité via `ImportService` (pipeline réel, `Collector`
mocké), puis vérifie via `ActivityService` — les mêmes méthodes
qu'utilise `ActivityController` — qu'elle est retrouvée exactement
comme le serait une activité manuelle, sans aucun traitement particulier
côté recherche/filtres.

Les 4 critères de `SPRINT_5.md` couverts :
* recherche géographique par rayon (LL-4002/LL-4003) ;
* recherche par zone cartographique (LL-4006/LL-4007) ;
* filtres catégorie (LL-4004) et date (LL-4005) — cas positif et
  négatif pour les deux ;
* filtre statut (`PUBLISHED`, le statut attribué aux imports depuis
  LL-5005) ;
* consultation individuelle par id (LL-1007) — titre, description,
  statut, et présence de `sourceId`/`importKey` (LL-5008).

Aucun code de production modifié : ce ticket ne fait que prouver, par
les tests, que l'intégration Sprint 4 ↔ Sprint 5 fonctionne déjà
correctement — cohérent avec l'absence totale de traitement spécial
pour les activités importées dans `ActivityService`/`ActivityRepository`.

Non compilé/testé en sandbox : Maven Central inaccessible, comme pour
les tickets précédents.

## LL-5010 — Tests du pipeline ✅

**Dépendance :** LL-5008.

`ImportServiceIntegrationTest` (nouveau, `@SpringBootTest` + `@Transactional`,
base réelle — comme `ActivityRepositoryIntegrationTest`) : contrairement
aux tests unitaires de LL-5008/LL-5009 (tout mocké), seul `Collector` est
remplacé par un mock ici — `NormalizationService`, `DeduplicationService`,
`SourceService`/`SourceRepository`, `ActivityRepository` sont les
implémentations réelles. C'est la seule façon de vérifier que le pipeline
fonctionne réellement de bout en bout (pas seulement que chaque maillon,
testé isolément, appelle correctement le suivant).

⚠️ Point technique à connaître : `@MockBean` est retiré en Spring Boot
4.0 (déprécié depuis 3.4) — ce projet est en Spring Boot 4.1. Utilisé
`@MockitoBean` (`org.springframework.test.context.bean.override.mockito`,
fourni par `spring-test`, déjà sur le classpath de test via
`spring-boot-starter-test`) à la place. Premier test du projet à mocker
un bean dans un contexte Spring réel — aucun autre test existant n'en
avait encore eu besoin.

Les 7 cas demandés par `SPRINT_5.md` sont couverts, chacun avec un nom
de source unique (`UUID`) pour éviter toute interférence entre tests
(même précaution que `activityAt` dans
`ActivityRepositoryIntegrationTest`) :
* donnée valide → activité créée avec les bons champs ;
* donnée invalide (titre vide) → `ignored`, rien en base ;
* doublon → import exécuté deux fois avec la même donnée, une seule
  ligne en base au final ;
* nouvelle activité → `created` au premier import ;
* mise à jour → import exécuté deux fois avec un titre modifié, même
  ligne (même `id`) mise à jour plutôt qu'une nouvelle créée ;
* erreur du collecteur → `CollectorException`, résultat dégradé, pas
  d'exception propagée, rien en base ;
* import vide → `collect()` retourne une liste vide, tous les
  compteurs à zéro, pas d'erreur.

Non compilé/testé en sandbox : Maven Central inaccessible, comme pour
les tickets précédents.

## LL-5009 — Journalisation des imports ✅

**Dépendance :** LL-5008.

`ImportResult` étendu avec les champs demandés par `SPRINT_5.md` :
`sourceName`, `startedAt`, `endedAt`, `fetched`, `created`, `updated`,
`ignored` (renommé depuis `rejected`), `errors` (nouveau). `archived`
conservé au-delà du minimum demandé (décision LL-5008).

`ImportService` :
* chronomètre chaque import (`startedAt`/`endedAt` autour de tout le
  traitement d'une source, y compris la résolution de la `Source` et
  l'archivage) ;
* journalise (SLF4J, niveau `INFO`) une ligne récapitulative par source
  en fin d'import ;
* **traitement par élément isolé** (`try/catch`) : une exception
  inattendue sur un élément collecté (bug, donnée malformée au-delà de
  ce que `NormalizationService` sait rejeter) est comptée dans
  `errors` et journalisée (`WARN`), sans interrompre le traitement des
  autres éléments de cette source — distinct d'`ignored` (rejet
  « normal » et anticipé par la normalisation) ;
* **échec total de la collecte** (`Collector#collect()` lève une
  exception, ex. configuration OpenAgenda manquante ou panne réseau)
  également capturé, journalisé (`ERROR`), traduit en un
  `ImportResult` dégradé (`fetched=0`, `errors=1`) plutôt que de faire
  échouer `importAll()` pour les autres sources.

Aucun tableau de bord d'administration ajouté — explicitement exclu par
`SPRINT_5.md` ; uniquement des logs applicatifs standard.

Tests : `ImportServiceTest` complété (9 cas au total — les 6 de LL-5008
adaptés aux nouveaux noms de champs, plus 3 nouveaux : `errors` sur
échec inattendu d'un élément, résultat dégradé sur échec total du
collecteur).

Non compilé/testé en sandbox : Maven Central inaccessible, comme pour
les tickets précédents.

## Correctif hors ticket — parsing des dates OpenAgenda

`OpenAgendaCollector#toLocalDateTime` utilisait
`OffsetDateTime.parse()`, qui exige un décalage horaire avec « : »
(`+01:00`). Signalé par Alex : la valeur `+0100` (sans « : ») fait
échouer le parsing (`DateTimeParseException`). La forme exacte
renvoyée par l'API OpenAgenda n'a pas pu être vérifiée en sandbox
(pas d'accès réseau à l'API réelle) — correctif : normalisation du
décalage (insertion du « : » si absent) avant parsing, pour tolérer
les deux formes. Nouveau test couvrant la forme avec « : ».

## Correctif hors ticket — démarrage de l'application cassé depuis LL-5006

`OpenAgendaCollector` a deux constructeurs (public 3 arguments pour
Spring, package-privé 4 arguments pour les tests) mais aucun n'était
annoté `@Autowired`. Avec plusieurs constructeurs et aucune annotation,
Spring essaie par défaut un constructeur sans argument — inexistant ici
— et le démarrage de l'`ApplicationContext` échouait entièrement
(`NoSuchMethodException: OpenAgendaCollector.<init>()`), faisant
échouer en cascade tous les tests d'intégration dépendant du contexte
Spring (`UserRepositoryIntegrationTest`,
`ActivityControllerIntegrationTest`, etc.) — signalé par Alex après
LL-5008.

`GeocodingService` évitait ce piège avec un constructeur public **sans
argument** comme point d'entrée Spring ; `OpenAgendaCollector`
reproduisait le style sans ce détail. Correctif : `@Autowired` ajouté
sur le constructeur public 3 arguments.

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
