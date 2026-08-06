État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 démarré.

Tickets terminés et validés : LL-0001 (projet Spring Boot initialisé), LL-0002 (arborescence backend créée), LL-0003 (profils Spring — `local` validé ; `dev`/`test`/`prod` créés mais volontairement sans datasource pour l'instant), LL-0004 (Docker Compose PostgreSQL/PostGIS — lancement validé), LL-0005 (datasource PostgreSQL — connexion validée), LL-0006 (Flyway — migration V1 appliquée avec succès), LL-0007 (Actuator — health/info validés), LL-0008 (OpenAPI/Swagger — accessible), LL-0009 (Spotless + Checkstyle — `mvn verify` passe), LL-0010 (Logback configuré, niveaux par profil, format uniforme), LL-0011 (gestion globale des erreurs, réponse JSON standardisée), LL-0012 (Dockerfile backend — build à valider).

Deux correctifs de config appliqués suite aux tests : profil `local` actif par défaut, et `flyway-core` remplacé par `spring-boot-starter-flyway` (modularisation Spring Boot 4).

Aucun développement métier n'a encore commencé.

Prochaine tâche

Traiter LL-0013 — README Backend.

Objectifs :

Expliquer le démarrage
Expliquer les profils
Expliquer Docker
Expliquer les commandes Maven
Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
