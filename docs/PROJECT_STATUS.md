# LocalLife - Project Status

**Version :** 0.3.0
**Dernière mise à jour :** 2026-08-12

---
## Phase actuelle
🟡 Phase 1 — Construction du socle technique

Le cadrage fonctionnel et technique est terminé.
Sprint 0 (socle technique backend) terminé. Sprint 1 (première fonctionnalité visible) terminé. Sprint 2 (utilisateurs et catégories) terminé. Sprint 3 (authentification, géocodage) terminé. Sprint 4 (recherche et découverte géographique) en cours.

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
 | Collecteurs                | ⏳ À faire    |
 | Phase 1 (Socle Technique)  | ✅ Terminé    |
 | Infrastructure             | ⏳ À faire    |

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
Tous les sprints (0 à 3) sont terminés. Le Sprint 4 est en cours.

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

Statut : 🟡 En cours.

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

---
# Prochaine action

Sprint 4 : traiter LL-4005 — Filtre par date (dépend de LL-4003).
