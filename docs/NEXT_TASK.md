État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 terminé (socle technique backend).

Sprint 1 terminé (première fonctionnalité visible — module Activity, API de consultation, carte, popup, documentation).

Sprint 2 démarré (utilisateurs et catégories).

Tickets terminés et validés (Sprint 0) : LL-0001 à LL-0015 — voir PROJECT_STATUS.md pour le détail.

Tickets terminés (Sprint 1) : LL-1001 (structure du module Activity), LL-1002 (entité Activity), LL-1003 (migration Flyway V2 — table `activity`, validée), LL-1004 (repository Activity — Spring Data JDBC, lecture seule), LL-1005 (service Activity — findAll/findById), LL-1006 (migration Flyway V3 — 5 activités de démonstration, coordonnées Marseille, statut `PUBLISHED` pour toutes — à valider après démarrage), LL-1007 (API REST de consultation), LL-1008 (carte React + Leaflet), LL-1009 (marqueurs d'activités), LL-1010 (popup activité — titre, catégorie, date au clic sur un marqueur ; compilation TypeScript et build Vite validés) et LL-1011 (documentation — README, CHANGELOG, PROJECT_STATUS mis à jour).

Tickets terminés (Sprint 2) : LL-2001 (structure du module User — domain/application/infrastructure, aucun code métier à ce stade), LL-2002 (entité User — `id`, `username`, `email`, `createdAt`, validée par le lead dev), LL-2003 (migration Flyway V4 — table `users`, renommée depuis `user` pour éviter le mot réservé PostgreSQL ; entité mise à jour avec `@Table("users")` ; à valider au démarrage réel), LL-2004 (repository User — save/findById/findByEmail, interface minimaliste comme ActivityRepository ; tests d'intégration écrits, à exécuter avec la vraie base), LL-2005 (service User — createUser/getUserById ; tests unitaires Mockito écrits, exécutables sans base de données), LL-2006 (structure du module Category + entité `Category(id, name, description)`), LL-2007 (migration Flyway V5 — table `category` ; à valider au démarrage réel), LL-2008 (repository Category — findAll/findById, lecture seule comme ActivityRepository).

Prochaine tâche

Traiter LL-2009 — Service Category.

Objectifs :

Fonctions : `getAllCategories()`, `getCategoryById()`.

Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
