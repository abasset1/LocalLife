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
# Prochaine action

Sprint 4 : appliquer le correctif ci-dessus, relancer `mvn verify`, puis traiter LL-4008 — Filtre par catégorie (frontend), dépend de LL-4004.

## LL-4008 — Filtre par catégorie (frontend) ✅

`frontend/src/App.tsx` :
* Le chargement des activités bascule de `GET /api/v1/activities` vers
  `GET /api/v1/activities/nearby` (seul endpoint qui supporte le filtre
  `category`, LL-4004). ⚠️ **Décision à valider avec toi** : en
  attendant la géolocalisation utilisateur (LL-4010) et le chargement
  dynamique selon la zone de carte (LL-4012), la recherche utilise le
  point de référence Marseille déjà utilisé pour centrer la carte, avec
  le rayon maximum autorisé par le contrat (`DEFAULT_SEARCH_RADIUS_KM =
  50`, LL-4001). Se rapproche du comportement actuel (toutes les
  données de démo sont autour de Marseille) mais reste un choix
  temporaire, à remplacer par LL-4010/LL-4012.
* Nouveau menu déroulant « Filtrer par catégorie » (`activity-filters`)
  au-dessus du formulaire de contribution. Liste des catégories
  construite dynamiquement à partir de la réponse **non filtrée**
  (`selectedCategory === ALL_CATEGORIES`), pour ne pas se réduire au fil
  des sélections (sinon impossible de revenir à une catégorie déjà
  écartée).
* La création d'activité (formulaire existant) déclenche désormais un
  **refetch** (`refreshKey`) plutôt qu'un ajout local à la liste : avec
  le filtre catégorie actif, ajouter l'activité créée localement
  l'aurait affichée même si elle ne correspondait pas au filtre en
  cours.
* CSS (`styles.css`) : nouvelle rangée `.activity-filters` dans la
  grille `.application-shell` (`grid-template-rows` passe de 3 à 4
  lignes).
* ⚠️ Pas de tests frontend ajoutés : aucun framework de test frontend
  n'est configuré dans le projet à ce jour (pas de Vitest/RTL dans
  `package.json`), donc rien à suivre comme convention existante.
* Vérifié avec `npx tsc --noEmit` et `npm run build` (les deux passent
  sans erreur) — contrairement au backend, ces outils sont disponibles
  en sandbox, donc c'est une vérification réelle, pas une simple
  relecture.

---
# Prochaine action

Sprint 4 : traiter LL-4009 — Filtre par date (frontend), dépend de LL-4005.

## LL-4009 — Filtre par date (frontend) ✅

`frontend/src/App.tsx`, dans la continuité de LL-4008 :
* Nouveau champ `<input type="date">` (« Filtrer par date ») ajouté à
  côté du filtre catégorie existant, dans la même barre
  `.activity-filters`. Un input HTML natif de type `date` renvoie déjà
  une chaîne au format ISO-8601 `yyyy-MM-dd` — exactement le format
  attendu par le paramètre `date` du contrat LL-4005 — donc aucune
  conversion n'est nécessaire avant de l'ajouter aux query params de
  `/nearby`.
* Bouton « ✕ » affiché uniquement quand un filtre date est actif, pour
  l'effacer facilement. ⚠️ Petite décision UX : les inputs `date` HTML
  n'ont pas toujours un bouton natif pour revenir à « aucune date »
  selon le navigateur (Firefox notamment), d'où ce bouton explicite.
* Reconstruction de la liste des catégories disponibles (LL-4008) :
  condition étendue pour ne se déclencher que si **ni** la catégorie
  **ni** la date ne sont filtrées (avant LL-4009, seule la catégorie
  était prise en compte) — sinon filtrer par date aurait aussi réduit
  la liste des catégories proposées.
* Les deux filtres (catégorie et date) se combinent naturellement
  puisqu'ils passent par les mêmes query params sur le même appel
  `/nearby`, qui accepte déjà les deux simultanément côté backend
  (LL-4004 + LL-4005).
* Toujours pas de tests frontend (aucun framework configuré, cf.
  LL-4008). Vérifié avec `npx tsc --noEmit` et `npm run build` : les
  deux passent sans erreur.

---
# Prochaine action

Sprint 4 : traiter LL-4010 — Géolocalisation utilisateur.

## LL-4010 — Géolocalisation utilisateur ✅

`frontend/src/App.tsx` :
* Nouveau bouton « Utiliser ma position » (barre `.geolocation-bar`,
  entre le header et les filtres). ⚠️ Décision : déclenchement par clic
  explicite plutôt qu'automatique au chargement de la page — correspond
  au critère d'acceptation « demande explicite de permission » et évite
  l'invite de permission intrusive dès l'arrivée sur la page (moins bien
  perçue, taux d'acceptation généralement plus faible).
* `navigator.geolocation.getCurrentPosition` avec gestion des 5 états
  requis par les critères d'acceptation : `idle` (jamais demandé),
  `loading` (bouton désactivé pendant la demande), `granted` (position
  affichée, arrondie à 4 décimales), `denied` (message dédié —
  distingué de `error` pour un message plus clair), `error` (timeout,
  position indisponible, ou API absente du navigateur).
* Aucune position envoyée au backend ni stockée en dehors de l'état
  React (`userPosition`) — perdue au rechargement de la page, conforme
  au critère « aucune position utilisateur persistée en base ».
* ⚠️ Important : ce ticket **n'utilise pas encore** la position obtenue
  pour la recherche — la recherche reste centrée sur
  `MARSEILLE_LATITUDE`/`MARSEILLE_LONGITUDE` (LL-4008). C'est le
  périmètre explicite de LL-4011 (« Recherche autour de l'utilisateur »),
  qui dépend justement de LL-4010 pour cette raison — voir les critères
  d'acceptation respectifs dans `SPRINT_4.md`.
* Vérifié avec `npx tsc --noEmit` et `npm run build` (les deux passent).
  Comme pour LL-4008/LL-4009, la partie purement JS (event handlers,
  gestion d'état) ne peut pas être testée en conditions réelles en
  sandbox — pas d'accès à l'API de géolocalisation navigateur hors d'un
  vrai navigateur — à valider visuellement de ton côté (accepter/refuser
  la permission, couper le réseau pour simuler une erreur, etc.).

---
# Prochaine action

Sprint 4 : traiter LL-4011 — Recherche autour de l'utilisateur (dépend de LL-4010, LL-4003).

## LL-4011 — Recherche autour de l'utilisateur ✅

`frontend/src/App.tsx`, dans la continuité de LL-4010, conforme aux 5
critères d'acceptation :
* **Utilisation des coordonnées du navigateur** : la recherche `/nearby`
  utilise `userPosition.latitude`/`userPosition.longitude` une fois la
  géolocalisation accordée (LL-4010), avec repli sur le point Marseille
  par défaut tant qu'elle ne l'est pas (`userPosition?.latitude ??
  MARSEILLE_LATITUDE`).
* **Appel de l'API `/nearby`** : déjà en place depuis LL-4008, la
  requête se redéclenche automatiquement dès que `userPosition` change
  (ajouté aux dépendances du `useEffect`).
* **Affichage des résultats** : inchangé, les marqueurs sur la carte se
  mettent à jour avec la nouvelle réponse.
* **Gestion du chargement** : nouvel état `isLoadingActivities`, message
  « Chargement des activités… » affiché pendant la requête. Gestion
  soignée de l'annulation (`AbortController`) pour éviter qu'une requête
  obsolète (filtre changé pendant qu'une requête précédente était en
  vol) ne réinitialise `isLoadingActivities` à `false` par erreur juste
  après que la requête suivante l'a remis à `true`.
* **Gestion de l'absence de résultats** : message « Aucune activité
  trouvée dans cette zone. » affiché une fois le chargement terminé si
  la liste est vide.
* Refactor JSX : le message de statut et `<MapContainer>` sont regroupés
  dans un conteneur `.map-area` (flexbox) plutôt que d'être deux enfants
  directs de la grille `.application-shell` — un message conditionnel
  directement dans la grille aurait ajouté une ligne implicite
  seulement quand affiché, changeant la hauteur de la carte entre les
  états chargé/non chargé.
* ⚠️ Décision explicitement **hors périmètre** de LL-4011 (voir
  commentaire dans le code) : le rayon de recherche reste fixe à 50 km
  même une fois la position utilisateur connue, aucune prise en compte
  du zoom de la carte. C'est le périmètre de LL-4012 (chargement
  dynamique selon la zone de la carte).
* La carte ne se recentre **pas** automatiquement sur la position
  utilisateur obtenue (seuls les marqueurs affichés changent). Recentrer
  la carte programmatiquement avec react-leaflet nécessite un composant
  enfant dédié utilisant `useMap()`/`setView` (le prop `center` de
  `MapContainer` n'est utilisé qu'au montage initial) — laissé de côté
  pour rester strictement dans le périmètre des 5 critères d'acceptation
  du ticket, mais à valider avec toi si c'est un comportement attendu
  malgré tout.
* Aucun test frontend (toujours pas de framework configuré). Vérifié
  avec `npx tsc --noEmit` et `npm run build` (les deux passent). Note
  hors-sujet : `npm audit` signale une vulnérabilité transitive
  (`nanoid`), sans lien avec ce ticket — consignée dans
  `docs/DETTE_TECHNIQUE.md` plutôt que corrigée dans ce diff.

---
# Prochaine action

Sprint 4 : traiter LL-4012 — Chargement dynamique de la carte.

## LL-4012 — Chargement dynamique de la carte ✅

`frontend/src/App.tsx`, conforme aux 5 critères d'acceptation :
* **Nouvelle recherche après déplacement/zoom significatif** : nouveau
  composant `MapBoundsWatcher`, monté à l'intérieur de `<MapContainer>`
  (seule façon d'écouter les événements de la carte avec react-leaflet —
  `useMapEvents` doit être appelé depuis un descendant, `MapContainer`
  n'expose pas de props `onMoveEnd`/`onZoomEnd` directement). Écoute
  `moveend`/`zoomend`, qui ne se déclenchent qu'une fois à la fin du
  geste.
* **Pas de requête à chaque événement de mouvement** : `moveend`/
  `zoomend` (pas `move`/`zoom`, qui eux sont continus) satisfont déjà
  l'essentiel du critère ; ajout d'un debounce de 400 ms
  (`MAP_BOUNDS_DEBOUNCE_MS`) en plus, pour absorber une rafale de
  `moveend` rapprochés (glisser/relâcher/glisser à nouveau rapidement).
* **Gestion du chargement** : réutilise `isLoadingActivities` (LL-4011),
  déclenché aussi par les changements de `mapBounds`.
* **Suppression des anciens marqueurs avant affichage des nouveaux
  résultats** : `setActivities([])` appelé immédiatement au début de
  chaque nouvelle recherche, avant l'appel réseau. ⚠️ Décision : appliqué
  systématiquement (changement de filtre, de position, ou de carte), pas
  seulement au cas du déplacement de carte — plus simple à maintenir
  qu'une logique différenciée par type de déclencheur, et cohérent avec
  l'esprit du critère.
* **Affichage des résultats** : inchangé.
* **Bascule de source de recherche** : tant que la carte n'a pas été
  déplacée/zoomée (`mapBounds === null`), la recherche reste sur
  `/nearby` (comportement LL-4008/LL-4011, rayon fixe autour de la
  position utilisateur ou de Marseille). Dès la première interaction
  avec la carte, elle bascule sur `/within-bounds` (contrat LL-4006,
  endpoint LL-4007) et cette zone devient la source de vérité pour
  toutes les recherches suivantes (y compris les changements de filtre
  catégorie/date) — cohérent avec l'objectif du ticket : afficher ce qui
  est réellement visible sur la carte.
* ⚠️ Point à valider avec toi : une fois `mapBounds` défini, cliquer à
  nouveau sur « Utiliser ma position » (LL-4010) récupère bien une
  nouvelle position mais celle-ci **n'a plus d'effet sur la recherche**
  tant que `mapBounds` reste actif (la zone de carte prime). Il faudrait
  probablement réinitialiser `mapBounds` à `null` lors d'un clic sur ce
  bouton pour que « recentrer sur ma position » redevienne prioritaire —
  non implémenté ici pour rester strictement dans le périmètre des 5
  critères d'acceptation de LL-4012, mais je peux l'ajouter si tu
  confirmes que c'est le comportement voulu.
* Aucun test frontend (toujours pas de framework configuré). Vérifié
  avec `npx tsc --noEmit` et `npm run build` (les deux passent). Le
  comportement des événements Leaflet réels (glisser/zoomer la carte)
  ne peut pas être vérifié en sandbox (pas de navigateur) — à valider
  visuellement de ton côté.

---
# Prochaine action

Sprint 4 : traiter LL-4013 — États frontend.

## LL-4013 — États frontend ✅

`frontend/src/App.tsx` : les états « chargement » et « aucun résultat »
existaient déjà (LL-4011/LL-4012), mais l'état « erreur » n'était pas
distingué de « aucun résultat » — un échec réseau ou une réponse `4xx`/
`5xx` déclenchait `setActivities([])`, affichant le même message
« Aucune activité trouvée » qu'une recherche légitimement vide. Ce
ticket corrige ça :
* Nouvel état `searchError: string | null`, mis à jour séparément de
  `activities` :
  - réponse HTTP en échec (`!response.ok`) : tente de lire le corps
    d'erreur JSON standard de l'API (`ApiErrorBody`, déjà utilisé pour
    le formulaire de contribution) pour un message précis, sinon message
    générique ;
  - exception réseau (backend injoignable) : message dédié ;
  - requête annulée (changement de filtre pendant le chargement) :
    **pas** une erreur, ignorée comme avant (`abortController.signal.aborted`).
* JSX : 4 branches désormais mutuellement exclusives — chargement,
  erreur (`role="alert"`, nouveau style `.activities-status-error` en
  rouge, pour se distinguer visuellement des deux autres états neutres),
  aucun résultat, résultats (pas de message dédié : les marqueurs sur la
  carte suffisent à rendre cet état « visible et compréhensible »,
  seul critère d'acceptation du ticket).
* `searchError` réinitialisé à `null` au début de chaque nouvelle
  recherche (même endroit que `setActivities([])`), pour qu'une
  erreur affichée disparaisse dès qu'une nouvelle tentative démarre.
* Aucun test frontend (toujours pas de framework configuré). Vérifié
  avec `npx tsc --noEmit` et `npm run build` (les deux passent).

---
# Prochaine action

Sprint 4 : traiter LL-4014 — Tests d'intégration.

## LL-4014 — Tests d'intégration ✅

Ce ticket demande d'automatiser 7 scénarios : recherche par rayon,
activité hors rayon, filtre catégorie, filtre date, bounding box,
paramètres invalides, combinaison de filtres. Les 6 premiers étaient
déjà couverts au fil des tickets LL-4002 à LL-4009 (`findWithinRadius`/
`findWithinBounds` dans `ActivityRepositoryIntegrationTest`,
`ActivityServiceTest`, `ActivityControllerTest`/
`ActivityControllerIntegrationTest`). Seule la **combinaison de
filtres** manquait un test dédié avec assertions réelles sur le
contenu des résultats (les tests existants combinant plusieurs filtres,
ex. `getActivitiesWithinBounds_ShouldReturnOk_WhenStatusCategoryAndDateProvided`,
ne vérifiaient qu'un statut `200 OK`, pas que les filtres s'appliquent
bien tous ensemble). Ajouts :
* `ActivityRepositoryIntegrationTest` :
  `findWithinRadius_ShouldCombineStatusCategoryAndDateFilters_WithAndSemantics`
  et l'équivalent pour `findWithinBounds` — pour chacune, 3 (ou 4 pour
  bounds) activités créées, chacune ne correspondant qu'à 2 des 3
  critères demandés (statut/catégorie/date, plus zone pour bounds), et
  une seule correspondant aux 3 (ou 4) à la fois. Vérifie la sémantique
  **ET** entre filtres : une activité qui ne rate qu'un seul critère
  doit être exclue, pas juste celle qui ne correspond à aucun.
* `ActivityServiceTest` :
  `findNearby_ShouldPassAllThreeOptionalFiltersThrough_WhenProvidedTogether`
  et l'équivalent `findWithinBounds` — vérifie que le service ne perd
  aucun des 3 filtres optionnels en route vers le repository quand ils
  sont fournis simultanément (les tests existants ne testaient qu'un
  filtre à la fois avec les autres à `null`).
* `ActivityControllerIntegrationTest` :
  `getNearbyActivities_ShouldReturnOk_WhenStatusCategoryAndDateProvided`
  ajouté par symétrie avec l'équivalent déjà existant sur
  `/within-bounds`.
* ⚠️ Non exécuté en sandbox (même limitation qu'aux tickets backend
  précédents) — à valider avec `mvn verify`.

---
# Prochaine action

Sprint 4 : traiter LL-4015 — Mise à jour de la documentation.

## LL-4015 — Mise à jour de la documentation ✅

* **`README.md`** : nouvelle section « Recherche géographique »
  (endpoints `/nearby`/`/within-bounds`, filtres communs, liens vers
  les deux contrats d'architecture, résumé des ajouts frontend). Suit
  le style thématique déjà utilisé pour Sprint 3 (« Authentification »,
  « Géocodage ») plutôt que « Fonctionnalités (Sprint 4) », pour rester
  cohérent avec la convention la plus récente du fichier.
* **`CHANGELOG.md`** : nouvelle entrée `0.4.0 - 2026-08-14`, résumant
  l'ensemble du Sprint 4 (recherche par rayon/zone, filtres combinables,
  géolocalisation, carte dynamique, états frontend, tests
  d'intégration) et le correctif `BadSqlGrammarException` du filtre
  date.
* **`PROJECT_STATUS.md`** : déjà tenu à jour au fil de chaque ticket
  tout au long du sprint (pas de changement rétroactif nécessaire ici).
* **Documentation OpenAPI** : déjà à jour — les annotations
  `@Operation`/`@ApiResponses` sur `/nearby` (LL-4003) et
  `/within-bounds` (LL-4007) ont été tenues à jour au moment de chaque
  ticket, pas de lacune identifiée à combler rétroactivement.
* **Documentation API** (contrats d'architecture) : `GEO_SEARCH_CONTRACT.md`
  et `BOUNDING_BOX_SEARCH_CONTRACT.md` déjà à jour (tenus en continu
  depuis LL-4001/LL-4006).
* ⚠️ Note découverte en marge de cette relecture, sans lien avec ce
  ticket : petit défaut de formatage préexistant dans
  `ActivityController.java` (saut de ligne manquant entre deux
  méthodes) — consigné dans `docs/DETTE_TECHNIQUE.md` plutôt que
  corrigé ici, pour ne pas mélanger un changement de code avec un diff
  purement documentaire.

---
# Sprint 4 — terminé

Les 15 tickets (LL-4001 à LL-4015) sont traités. Voir la
« Definition of Done » et le livrable dans `docs/05_Sprints/SPRINT_4.md`
pour la liste des critères couverts. Prochaine étape à définir avec
Alex : démarrage du Sprint 5.

# Prochaine action

À définir avec Alex (Sprint 5).
