État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 démarré.

Tickets terminés : LL-0001 (projet Spring Boot initialisé), LL-0002 (arborescence backend créée), LL-0003 (profils Spring configurés : local, dev, test, prod — démarrage à valider), LL-0004 (Docker Compose PostgreSQL/PostGIS créé — lancement à valider), LL-0005 (datasource PostgreSQL configurée sur le profil local), LL-0006 (Flyway installé, première migration vide — exécution automatique à valider), LL-0007 (Actuator health/info exposés), LL-0008 (OpenAPI/Swagger installé — accessibilité à valider).

Aucun développement métier n'a encore commencé.

Prochaine tâche

Traiter LL-0009 — Qualité de code.

Objectifs :

Configurer Spotless
Configurer Checkstyle
Le build doit échouer en cas de non-conformité
Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
