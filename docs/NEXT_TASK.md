État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 terminé (socle technique backend).

Sprint 1 démarré (première fonctionnalité visible — module Activity, API de consultation, carte).

Tickets terminés et validés (Sprint 0) : LL-0001 à LL-0015 — voir PROJECT_STATUS.md pour le détail.

Tickets terminés (Sprint 1) : LL-1001 (structure du module Activity), LL-1002 (entité Activity), LL-1003 (migration Flyway V2 — table `activity`, validée), LL-1004 (repository Activity — Spring Data JDBC, lecture seule), LL-1005 (service Activity — findAll/findById, simple délégation).

Prochaine tâche

Traiter LL-1006 — Données de démonstration.

Objectifs :

Au moins cinq activités (concert, marché, food truck, exposition, cinéma)

Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
