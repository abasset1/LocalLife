# PROJECT_STATUS.md

# LocalLife - Project Status

**Version :** 0.1.0
**Dernière mise à jour :** 2026-08-06

---

# État général

## Phase actuelle

🟡 Phase 1 — Construction du socle technique

Le cadrage fonctionnel et technique est terminé.

Le développement n'a pas encore commencé.

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
| Développement Frontend     | ⏳ À faire    |
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

Statut : 🟡 En cours.

Tickets terminés :

* LL-0001 — Initialiser le projet Spring Boot ✅
* LL-0002 — Créer l'arborescence backend ✅
* LL-0003 — Configurer les profils Spring ✅ (démarrage validé pour `local` ; `dev`/`test`/`prod` restent à tester)
* LL-0004 — Docker Compose (PostgreSQL/PostGIS) ✅ (lancement validé)
* LL-0005 — Configuration PostgreSQL (datasource, profil local) ✅ (connexion validée via HikariCP)
* LL-0006 — Installer Flyway (première migration vide) ✅ (migration V1 appliquée avec succès, validé)
* LL-0007 — Actuator (health, info) ✅ (health UP, info OK, validé)
* LL-0008 — OpenAPI (Swagger) ✅ (Swagger UI accessible, validé)
* LL-0009 — Qualité de code (Spotless, Checkstyle) ✅ (`mvn verify` passe, validé)
* LL-0010 — Logging (Logback, niveaux par profil, format uniforme) ✅

Deux correctifs de config appliqués suite aux tests LL-0004→LL-0009 (hors numérotation de ticket) :
* `spring.profiles.active=local` ajouté en profil par défaut (le contexte Spring nécessite une datasource pour démarrer).
* `flyway-core` remplacé par `spring-boot-starter-flyway` (Spring Boot 4 a modularisé l'auto-configuration).

Décision : les profils `dev`/`test`/`prod` restent sans datasource pour l'instant (LL-0003 ne demandait que leur création). Ils échouent volontairement au démarrage tant qu'aucun environnement réel n'existe — évite une config factice inutile (cf. DEVELOPMENT_PHILOSOPHY : MVP prime sur perfection).

Prochain ticket : LL-0011 — Gestion des erreurs (exception globale, réponse JSON standardisée).

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

# Prochaine action

Traiter LL-0011 — Gestion des erreurs (exception globale, réponse JSON standardisée).
