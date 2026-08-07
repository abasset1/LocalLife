État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 terminé (socle technique backend).

Sprint 1 démarré (première fonctionnalité visible — module Activity, API de consultation, carte).

Tickets terminés et validés (Sprint 0) : LL-0001 à LL-0015 — voir PROJECT_STATUS.md pour le détail.

Tickets terminés (Sprint 1) : LL-1001 (structure du module Activity), LL-1002 (entité Activity), LL-1003 (migration Flyway V2 — table `activity`, validée), LL-1004 (repository Activity — Spring Data JDBC, lecture seule), LL-1005 (service Activity — findAll/findById), LL-1006 (migration Flyway V3 — 5 activités de démonstration, coordonnées Marseille, statut `PUBLISHED` pour toutes — à valider après démarrage), LL-1007 (API REST de consultation) et LL-1008 (carte React + Leaflet).

Prochaine tâche

Traiter LL-1009 — Affichage des activités.

Objectifs :

Afficher les activités retournées par l'API.
Chaque activité apparaît sous forme de marqueur sur la carte.

Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
