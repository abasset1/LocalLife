État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 terminé (socle technique backend).

Sprint 1 terminé (première fonctionnalité visible — module Activity, API de consultation, carte, popup, documentation).

Sprint 2 démarré (utilisateurs et catégories).

Tickets terminés et validés (Sprint 0) : LL-0001 à LL-0015 — voir PROJECT_STATUS.md pour le détail.

Tickets terminés (Sprint 1) : LL-1001 (structure du module Activity), LL-1002 (entité Activity), LL-1003 (migration Flyway V2 — table `activity`, validée), LL-1004 (repository Activity — Spring Data JDBC, lecture seule), LL-1005 (service Activity — findAll/findById), LL-1006 (migration Flyway V3 — 5 activités de démonstration, coordonnées Marseille, statut `PUBLISHED` pour toutes — à valider après démarrage), LL-1007 (API REST de consultation), LL-1008 (carte React + Leaflet), LL-1009 (marqueurs d'activités), LL-1010 (popup activité — titre, catégorie, date au clic sur un marqueur ; compilation TypeScript et build Vite validés) et LL-1011 (documentation — README, CHANGELOG, PROJECT_STATUS mis à jour).

Tickets terminés (Sprint 2) : LL-2001 (structure du module User), LL-2002 (entité User), LL-2003 (migration Flyway, table `users`), LL-2004 (repository User), LL-2005 (service User, tests unitaires Mockito écrits), LL-2006 (module Category : structure + entité), LL-2007 (migration Flyway, table `category`), LL-2008 (repository Category), LL-2009 (service Category), LL-2010 (API REST User — POST /api/v1/users et GET /api/v1/users/{id}, tests unitaires Mockito écrits).

Prochaine tâche

Traiter LL-2011 — API REST pour Category.

Objectifs :

Endpoint : `GET /api/v1/categories` (lister les catégories).

Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
