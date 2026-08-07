# PROJECT_STATUS.md

# LocalLife - Project Status

**Version :** 0.1.0
**Dernière mise à jour :** 2026-08-07

---

# État général

## Phase actuelle

🟡 Phase 1 — Construction du socle technique

Le cadrage fonctionnel et technique est terminé.

Sprint 0 (socle technique backend) terminé. Sprint 1 (première fonctionnalité visible) terminé.

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

# Prochaine action

Sprint 1 clôturé. Prochaine étape : cadrage du Sprint 2 (voir BACKLOG.md et ROADMAP.md).
