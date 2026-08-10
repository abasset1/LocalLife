# PROJECT_STATUS.md

# LocalLife - Project Status

**Version :** 0.1.0
**Dernière mise à jour :** 2026-08-07

---

# État général

## Phase actuelle

🟡 Phase 1 — Construction du socle technique

Le cadrage fonctionnel et technique est terminé.

Sprint 0 (socle technique backend) terminé. Sprint 1 (première fonctionnalité visible) terminé. Sprint 2 (utilisateurs et catégories) terminé. Sprint 3 (authentification, géocodage) démarré.

---

# Avancement

| Domaine                    | État         |
| -------------------------- | ------------ |
| Vision produit             | ✅ Terminé    |
| Product Bible              | ✅ Terminé    |
| MVP                        | ✅ Terminé    |
| User Stories               | ✅ Terminé    |
| Backlog fonctionnel        | ✅ Terminé    |
| Roadmap                    | ✅ Terminé    |
| Architecture fonctionnelle | ✅ Terminé    |
| Architecture technique     | ✅ Terminé    |
| Modèle de données          | ✅ Terminé    |
| API MVP                    | ✅ Terminé    |
| Repository                 | ✅ Initialisé |
| Développement Backend      | 🟡 En cours   |
| Développement Frontend     | 🟡 En cours   |
| Collecteurs                | ⏳ À faire    |
| Infrastructure             | ⏳ À faire    |

---

# Décisions validées

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

# Priorité actuelle

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

# Sprint en cours

## Sprint 0

Statut : ✅ Terminé.

Tickets terminés :

* LL-0001 — Initialiser le projet Spring Boot ✅
* LL-0002 — Créer l'arborescence backend ✅
* LL-0003 — Configurer les profils Spring ✅ (démarrage validé pour `local` ; `dev`/`test`/`prod` créés mais volontairement sans datasource pour l'instant)
* LL-0004 — Docker Compose (PostgreSQL/PostGIS) ✅ (lancement validé)
* LL-0005 — Configuration PostgreSQL (datasource, profil local) ✅ (connexion validée via HikariCP)
* LL-0006 — Installer Flyway (première migration vide) ✅ (migration V1 appliquée avec succès, validé)
* LL-0007 — Actuator (health, info) ✅ (health UP, info OK, validé)
* LL-0008 — OpenAPI (Swagger) ✅ (Swagger UI accessible, validé)
* LL-0009 — Qualité de code (Spotless, Checkstyle) ✅ (`mvn verify` passe, validé)
* LL-0010 — Logging (Logback, niveaux par profil, format uniforme) ✅
* LL-0011 — Gestion des erreurs (exception globale, réponse JSON standardisée) ✅
* LL-0012 — Docker Backend (Dockerfile) ✅ (build et run validés — nécessite `--network` + `SPRING_DATASOURCE_URL` pointant vers le conteneur Postgres, `localhost` ne fonctionne pas entre deux conteneurs)
* LL-0013 — README Backend (démarrage, profils, Docker, commandes Maven) ✅
* LL-0014 — Pipeline GitHub Actions (build, tests) ✅ (CI validé après push)
* LL-0015 — Vérification finale ✅ (voir contrôle ci-dessous)

Deux correctifs de config appliqués suite aux tests LL-0004→LL-0009 (hors numérotation de ticket) :
* `spring.profiles.active=local` ajouté en profil par défaut (le contexte Spring nécessite une datasource pour démarrer).
* `flyway-core` remplacé par `spring-boot-starter-flyway` (Spring Boot 4 a modularisé l'auto-configuration).

Décision : les profils `dev`/`test`/`prod` restent sans datasource pour l'instant (LL-0003 ne demandait que leur création). Ils échouent volontairement au démarrage tant qu'aucun environnement réel n'existe — évite une config factice inutile (cf. DEVELOPMENT_PHILOSOPHY : MVP prime sur perfection).

### LL-0015 — Contrôle final

| Point à contrôler | Résultat |
| --- | --- |
| Build | ✅ `mvn verify` passe (compilation, tests, Spotless, Checkstyle) |
| Docker | ✅ `docker build` réussit, `docker run` se connecte à Postgres via le réseau Compose |
| PostgreSQL | ✅ HikariPool se connecte au conteneur `locallife-postgres` (profil `local`) |
| Flyway | ✅ Migration `V1__init` appliquée avec succès (`flyway_schema_history` créée) |
| Swagger | ✅ `swagger-ui.html` accessible et fonctionnel |
| Actuator | ✅ `/actuator/health` → `UP`, `/actuator/info` → métadonnées de build |

**Sprint 0 terminé.** Le socle technique (Spring Boot, PostgreSQL/PostGIS, Docker, Flyway, Actuator, OpenAPI, qualité de code, logging, gestion d'erreurs, CI) est en place et validé de bout en bout. Le développement métier peut démarrer.

Objectif :

Mettre en place toute l'infrastructure permettant de commencer le développement métier.

Livrables :

* Backend initialisé
* Frontend initialisé
* Docker Compose
* PostgreSQL/PostGIS
* Flyway
* CI/CD
* Arborescence définitive

---

# Risques identifiés

* Ajouter des fonctionnalités avant validation du MVP.
* Complexifier l'architecture prématurément.
* Mélanger logique métier et logique technique.
* Développer plusieurs clients (web/mobile) avant validation.

---

# Principes du projet

* Simplicité avant optimisation.
* Documentation avant implémentation.
* API First.
* Architecture modulaire.
* Évolutions incrémentales.
* Chaque fonctionnalité doit apporter une valeur utilisateur.

---

# Définition d'une tâche terminée

Une tâche est considérée comme terminée lorsque :

* le développement est terminé ;
* les tests sont réalisés ;
* la documentation est mise à jour ;
* les critères d'acceptation sont validés.

---

# Sprint 1

Statut : ✅ Terminé.

Tickets terminés :

* LL-1001 — Créer le module Activity (structure domain/application/infrastructure) ✅
* LL-1002 — Créer l'entité Activity ✅
* LL-1003 — Migration Flyway (table activity) ✅ (migration V2 validée)
* LL-1004 — Repository Activity (Spring Data JDBC, lecture seule) ✅
* LL-1005 — Service Activity (findAll, findById) ✅
* LL-1006 — Données de démonstration (5 activités) ✅ (exécution de la migration à valider par toi)
* LL-1007 — API REST de consultation des activités ✅
* LL-1008 — Première carte React + Leaflet (affichage, zoom, déplacement) ✅
* LL-1009 — Affichage des activités sous forme de marqueurs ✅
* LL-1010 — Popup activité ✅ (clic sur un marqueur → titre, catégorie, date ; `npx tsc --noEmit` et `npx vite build` validés)
* LL-1011 — Documentation ✅ (README, CHANGELOG, PROJECT_STATUS mis à jour)

**Sprint 1 terminé.** La première fonctionnalité visible de LocalLife (carte interactive, activités de démonstration, consultation via popup) est en place et validée de bout en bout. Le prochain sprint reste à cadrer.

---

# Sprint 2

Statut : ✅ Terminé.

Objectif : gestion des utilisateurs et des catégories (création de compte simple, consultation des catégories, contribution basique — sans authentification, rôles, modération ni notifications).

Tickets terminés :

* LL-2001 — Créer le module User (structure domain/application/infrastructure) ✅ — pas de logique métier, aucun code Java ajouté à ce stade, rien à compiler.
* LL-2002 — Créer l'entité User ✅ — record `User(id, username, email, createdAt)`, même convention que `Activity` (Spring Data `@Id`). Validée par le lead dev (sera complétée plus tard).
* LL-2003 — Migration Flyway pour User ✅ — `V4__create_users_table.sql`, table **`users`** (et non `user`, mot réservé PostgreSQL). Entité mise à jour avec `@Table("users")` pour faire correspondre le mapping.
* LL-2004 — Repository User ✅ — `save()`, `findById()`, `findByEmail()` (même philosophie minimaliste que `ActivityRepository` : interface étend `Repository`, pas `CrudRepository`). Tests d'intégration écrits (`UserRepositoryIntegrationTest`, même style que `ActivityControllerIntegrationTest`, `@Transactional` pour rollback auto).
* LL-2005 — Service User ✅ — `createUser(username, email)` et `getUserById(id)`, simple délégation vers le repository (même convention qu'`ActivityService`). Tests unitaires écrits avec Mockito (`UserServiceTest`, repository mocké — ne nécessitent pas de base de données, exécutables directement).
* LL-2006 — Créer le module Category ✅ — structure (domain/application/infrastructure) et entité `Category(id, name, description)` créées en une fois, comme demandé par le ticket. Aucun mot réservé PostgreSQL à gérer ici (contrairement à `user`) : nommage de table par défaut (`category`) conservé.
* LL-2007 — Migration Flyway pour Category ✅ — `V5__create_category_table.sql`, table `category` (colonnes `id`, `name`, `description`).
* LL-2008 — Repository Category ✅ — `findAll()`, `findById()` uniquement (même philosophie en lecture seule qu'`ActivityRepository`). Pas de test dédié à ce stade, comme pour `ActivityRepository` (testé indirectement via l'API en LL-1007).
* LL-2009 — Service Category ✅ — `getAllCategories()` et `getCategoryById(id)`, simple délégation vers le repository (même convention qu'`ActivityService`). Pas de test dédié à ce stade, comme pour `ActivityService` (testé indirectement via l'API en LL-1007).
* LL-2010 — API REST pour User ✅ — `POST /api/v1/users` (201 Created, corps `CreateUserRequest(username, email)`) et `GET /api/v1/users/{id}` (200 ou 404), même style qu'`ActivityController`. Tests unitaires écrits avec Mockito (`UserControllerTest`, service mocké — exécutables sans base de données).
* LL-2011 — API REST pour Category ✅ — `GET /api/v1/categories` (liste), même style qu'`ActivityController`. Tests unitaires écrits avec Mockito (`CategoryControllerTest`, service mocké — exécutables sans base de données).
* **Hors périmètre initial du Sprint 2, décidé par Alex** — Endpoint de création d'activité : `POST /api/v1/activities` ajouté pour débloquer LL-2012, qui nécessitait un moyen d'envoyer une contribution au backend (le module Activity était strictement en lecture seule depuis le Sprint 1). Corps de requête : `title`, `description`, `category`, `latitude`, `longitude` — exactement les champs listés dans LL-2012. ⚠️ Décisions prises unilatéralement, à valider avec toi :
  - Statut par défaut : `PENDING` (pas de système de modération existant à ce stade — LL-2012 l'exclut explicitement, donc ce choix reste provisoire).
  - `startDate` = date de soumission (aucune date n'est demandée dans le formulaire de contribution) ; `endDate` = `null`.
  - Tests unitaires ajoutés dans `ActivityControllerTest` existant, sans toucher aux tests déjà en place.
* LL-2012 — Formulaire de contribution ✅ — formulaire ajouté dans `App.tsx` (titre, description, catégorie, latitude, longitude — champs texte/nombre simples), `POST /api/v1/activities` appelé à la soumission, activité créée ajoutée directement à la carte sans recharger. `npx tsc --noEmit` et `npx vite build` validés.
* LL-2013 — Documentation ✅ — README, CHANGELOG et PROJECT_STATUS mis à jour pour refléter le Sprint 2 terminé.

**Sprint 2 terminé.** Gestion des utilisateurs (création, consultation) et des catégories (consultation) en place, ainsi qu'un formulaire de contribution d'activité fonctionnel de bout en bout (frontend + endpoint backend ajouté hors périmètre initial, décisions à valider avec toi — voir ci-dessus).

⚠️ **Points restant à valider par toi avant de considérer le Sprint 2 pleinement clos en conditions réelles :**
- Aucune migration ni aucun test nécessitant la vraie base de données (`users`, `category`, tests d'intégration User) n'a pu être exécuté en sandbox (Docker/PostgreSQL indisponibles ici).
- Les décisions provisoires sur `POST /api/v1/activities` (statut `PENDING`, gestion des dates) à confirmer ou ajuster.

---

# Sprint 3

Statut : 🟡 En cours.

Objectif : authentification (inscription, connexion JWT), endpoints protégés, gestion du profil, géocodage d'adresse pour le formulaire de contribution.

Tickets terminés :

* LL-3001 — Étendre l'entité User ✅ — ajout de `passwordHash` (String) et `role` (nouvel enum `Role { USER, ADMIN }`, package `user.domain`). ⚠️ Point de granularité : les critères du ticket mentionnent aussi "Migration Flyway" alors qu'un ticket séparé LL-3002 y est dédié — j'ai traité LL-3001 comme une extension de l'entité uniquement, la migration étant réservée à LL-3002 (cohérent avec la dépendance `LL-3002 → LL-3001`). Tous les appels au constructeur `User` (service + 3 fichiers de test) ont été mis à jour pour compiler : `passwordHash` vaut `"hash"` dans les tests (placeholder) et `null` dans `UserService.createUser` (flux hérité du Sprint 2, sans mot de passe — sera probablement remplacé par `AuthService.register` en LL-3004) ; `role` vaut `Role.USER` par défaut partout.
* LL-3002 — Migration Flyway pour les nouveaux champs ✅ — `V6__add_password_hash_and_role_to_users.sql`. ⚠️ Deux points à noter :
  - Le ticket mentionne la table `user` (sans "s") — la vraie table s'appelle **`users`** (décision LL-2003, mot réservé PostgreSQL). J'ai utilisé le vrai nom.
  - `password_hash` reçoit un défaut `''` (chaîne vide) pour ne pas casser les lignes existantes, comme demandé pour `role` (`'USER'`). Ça laisse les comptes créés avant l'authentification (via l'ancien `POST /api/v1/users` du Sprint 2) avec un mot de passe vide/invalide — à traiter dans un futur ticket (réinitialisation ou migration de données), pas dans le périmètre de LL-3002.
  - ⚠️ Non exécutée en sandbox (Docker/PostgreSQL indisponibles ici) — à valider par toi au démarrage.
* LL-3003 — Service de hachage des mots de passe ✅ — `PasswordHashingService` (package `user.application`), méthodes `hash()` et `matches()` s'appuyant sur `BCryptPasswordEncoder`. ⚠️ Nouvelle dépendance ajoutée au `pom.xml` : `spring-security-crypto` (pas la stack complète `spring-boot-starter-security`, pour rester minimal — pas de filtres web ni de config de sécurité, juste BCrypt). Tests unitaires écrits (`PasswordHashingServiceTest`, 3 cas : hash ≠ clair, `matches` vrai, `matches` faux) — aucune dépendance externe, exécutables directement sans mock ni base de données.
* LL-3004 — Implémenter le login ✅ *(fait directement sur le dépôt distant, hors de cette conversation — documenté ici a posteriori)* — `AuthService.login(LoginRequest)` : vérifie email + mot de passe (BCrypt), génère un JWT réel via `JwtService` (dépendance `jjwt` ajoutée au `pom.xml`), retourne `LoginResponse(token)`. Secret JWT configuré via `jwt.secret=${JWT_SECRET:default_secret_for_dev_only_change_in_production}` (`application.properties`) — jamais commité en clair, avec un défaut explicite pour le dev local. Plusieurs commits de correctifs associés (Checkstyle, dépendances Maven, `spotless:apply`).
* LL-3005 — Génération de JWT ✅ *(fait directement sur le dépôt distant)* — `JwtService.generateToken(userId, email, role)` isolé dans `auth.application`, claims `userId`/`email`/`role`, expiration 24h, signature HS256. Tests dédiés (`JwtServiceTest`).
* LL-3006 — Middleware de vérification JWT ✅ *(fait directement sur le dépôt distant)* — `JwtFilter` (extrait et valide le JWT de l'en-tête `Authorization`, remplit le `SecurityContext` via `JwtAuthentication`), `SecurityConfig` (active le filtre, CSRF désactivé, sessions stateless). ⚠️ Comme documenté dans le code : tous les endpoints restent accessibles sans JWT pour l'instant (`anyRequest().permitAll()`) — la protection effective est volontairement différée à LL-3008, conformément à `SPRINT_3.md`.
* LL-3007 — Endpoints d'authentification ✅ — `AuthController` complété avec `POST /api/v1/auth/register` (corps `RegisterRequest(username, email, password)`, retourne `201 Created` + `UserResponse` **sans** `passwordHash`) ; `POST /api/v1/auth/login` déjà présent depuis LL-3004, conservé tel quel (`{"token": "<JWT>"}`).
  - Validation des entrées dans `AuthService.register()` : nom d'utilisateur non vide, format email (regex simple), mot de passe ≥ 8 caractères, email déjà utilisé → `IllegalArgumentException` avec message clair, sans fuite (pas de détail sur la nature exacte du conflit au-delà de "déjà utilisé").
  - **Correctif au passage** : le `GlobalExceptionHandler` générique renvoyait 500 pour toute erreur, y compris un mauvais mot de passe au login — ce n'était pas conforme à un comportement REST correct. `AuthController` gère maintenant ses propres erreurs (`IllegalArgumentException` → 400 pour `register`, 401 pour `login`), sans toucher au `GlobalExceptionHandler` partagé (repris via `ErrorResponse` pour rester cohérent).
  - Nouveaux DTOs : `RegisterRequest`, `UserResponse` (projection sûre de `User`, sans `passwordHash`).
  - Tests écrits : `AuthServiceTest` (couvre aussi `login()`, qui n'avait jamais été testé malgré l'exigence de LL-3004 — 8 tests au total : succès/erreurs pour `register` et `login`) et `AuthControllerTest` (4 tests, contrôleur mocké).

---

# Prochaine action

Traiter LL-3008 — Protéger les endpoints existants (`POST /api/v1/activities` pour les utilisateurs connectés, `POST /api/v1/users` à désactiver ou protéger pour les `ADMIN`).
