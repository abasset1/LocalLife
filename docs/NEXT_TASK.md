État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 terminé (socle technique backend).

Tickets terminés et validés : LL-0001 (projet Spring Boot initialisé), LL-0002 (arborescence backend créée), LL-0003 (profils Spring — `local` validé ; `dev`/`test`/`prod` créés mais volontairement sans datasource pour l'instant), LL-0004 (Docker Compose PostgreSQL/PostGIS — lancement validé), LL-0005 (datasource PostgreSQL — connexion validée), LL-0006 (Flyway — migration V1 appliquée avec succès), LL-0007 (Actuator — health/info validés), LL-0008 (OpenAPI/Swagger — accessible), LL-0009 (Spotless + Checkstyle — `mvn verify` passe), LL-0010 (Logback configuré, niveaux par profil, format uniforme), LL-0011 (gestion globale des erreurs, réponse JSON standardisée), LL-0012 (Dockerfile backend — build et run validés), LL-0013 (README Backend), LL-0014 (pipeline GitHub Actions — build + tests, CI validé après push), LL-0015 (vérification finale — build, Docker, PostgreSQL, Flyway, Swagger, Actuator tous confirmés OK).

Deux correctifs de config appliqués suite aux tests : profil `local` actif par défaut, et `flyway-core` remplacé par `spring-boot-starter-flyway` (modularisation Spring Boot 4).

Aucun développement métier n'a encore commencé.

Prochaine tâche

D'après `docs/04_Project/ROADMAP.md`, le Sprint 1 couvre les modules Activity/Place/Category et une API REST minimale. En attente d'un fichier `SPRINT_1.md` détaillant les tickets avant de démarrer.

Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
