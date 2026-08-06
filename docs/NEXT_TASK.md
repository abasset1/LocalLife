État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 terminé (socle technique backend).

Sprint 1 démarré (première fonctionnalité visible — module Activity, API de consultation, carte).

Tickets terminés et validés (Sprint 0) : LL-0001 à LL-0015 — voir PROJECT_STATUS.md pour le détail.

Tickets terminés (Sprint 1) : LL-1001 (structure du module Activity : domain/application/infrastructure, aucune logique métier).

Prochaine tâche

Traiter LL-1002 — Créer l'entité Activity.

Objectifs :

Champs : id, title, description, category, latitude, longitude, startDate, endDate, status
Aucune relation avec d'autres entités

Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
