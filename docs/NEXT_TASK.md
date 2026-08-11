État actuel

Le cadrage fonctionnel et technique est terminé.

Sprint 0 terminé (socle technique backend).

Sprint 1 terminé (première fonctionnalité visible — module Activity, API de consultation, carte, popup, documentation).

Sprint 2 terminé (utilisateurs, catégories, formulaire de contribution).

Sprint 3 démarré (authentification, géocodage, gestion des utilisateurs — voir docs/05_Sprints/SPRINT_3.md).

Tickets terminés et validés (Sprint 0) : LL-0001 à LL-0015 — voir PROJECT_STATUS.md pour le détail.

Tickets terminés (Sprint 1) : LL-1001 (structure du module Activity), LL-1002 (entité Activity), LL-1003 (migration Flyway V2 — table `activity`, validée), LL-1004 (repository Activity — Spring Data JDBC, lecture seule), LL-1005 (service Activity — findAll/findById), LL-1006 (migration Flyway V3 — 5 activités de démonstration, coordonnées Marseille, statut `PUBLISHED` pour toutes — à valider après démarrage), LL-1007 (API REST de consultation), LL-1008 (carte React + Leaflet), LL-1009 (marqueurs d'activités), LL-1010 (popup activité — titre, catégorie, date au clic sur un marqueur ; compilation TypeScript et build Vite validés) et LL-1011 (documentation — README, CHANGELOG, PROJECT_STATUS mis à jour).

Tickets terminés (Sprint 2) : LL-2001 (structure du module User), LL-2002 (entité User), LL-2003 (migration Flyway, table `users`), LL-2004 (repository User), LL-2005 (service User, tests unitaires Mockito écrits), LL-2006 (module Category : structure + entité), LL-2007 (migration Flyway, table `category`), LL-2008 (repository Category), LL-2009 (service Category), LL-2010 (API REST User — POST /api/v1/users et GET /api/v1/users/{id}, tests unitaires Mockito écrits), LL-2011 (API REST Category — GET /api/v1/categories, tests unitaires Mockito écrits), LL-2012 (formulaire de contribution frontend), LL-2013 (documentation).

Hors périmètre initial, ajouté à la demande d'Alex : `POST /api/v1/activities` (repository, service, controller) pour débloquer LL-2012 — voir PROJECT_STATUS.md pour le détail et les décisions à valider (statut par défaut `PENDING`, pas de date dans le formulaire).

Tickets terminés (Sprint 3) : LL-3001 (extension de l'entité User — `passwordHash`, `role` ; voir PROJECT_STATUS.md pour le point de granularité avec LL-3002), LL-3002 (migration Flyway V6 — table `users` réellement utilisée, malgré le libellé "user" du ticket ; `password_hash` défaut `''`, `role` défaut `'USER'` ; à valider au démarrage réel), LL-3003 (service de hachage BCrypt — `PasswordHashingService.hash()`/`matches()` ; dépendance `spring-security-crypto` ajoutée au pom.xml ; tests unitaires écrits, sans mock ni base de données), LL-3004 (login avec JWT réel, fait directement sur le dépôt distant), LL-3005 (génération JWT isolée dans JwtService, fait directement sur le dépôt distant), LL-3006 (middleware JwtFilter + SecurityConfig, protection effective différée à LL-3008 comme prévu, fait directement sur le dépôt distant), LL-3007 (endpoint POST /api/v1/auth/register, correctif des codes HTTP d'erreur login/register, tests AuthServiceTest + AuthControllerTest écrits — voir PROJECT_STATUS.md pour le détail), LL-3008 (protection de `POST /api/v1/activities` — authentifié — et `POST /api/v1/users` — rôle ADMIN — dans `SecurityConfig`, réponses JSON standardisées 401/403 ; voir PROJECT_STATUS.md pour le détail et la décision à valider), LL-3009 (pages `/login` et `/register`, `react-router-dom` ajouté, JWT stocké dans `localStorage` ; `tsc --noEmit` et `vite build` validés — voir PROJECT_STATUS.md pour les décisions à valider, notamment le fait que le formulaire de contribution est temporairement cassé jusqu'à LL-3011), LL-3010 (en-tête affiche "Bonjour, {email}" + bouton Déconnexion, mise à jour dynamique sans rechargement ; email affiché plutôt qu'un username absent du JWT — voir PROJECT_STATUS.md pour la décision à valider et la fuite pré-existante signalée sur `GET /api/v1/users/{id}`), LL-3011 (module `apiFetch` avec JWT auto + redirection `/login` sur 401 ; utilisé uniquement pour l'appel protégé `POST /api/v1/activities`, pas pour le `GET` public — répare le formulaire de contribution cassé depuis LL-3008/3009 ; voir PROJECT_STATUS.md pour les décisions à valider), LL-3012 (géocodage backend via Nominatim, `POST /api/v1/activities` reçoit désormais une `address` au lieu de `latitude`/`longitude` ; erreurs gérées 400/503 ; `GeocodingServiceTest` avec `MockRestServiceServer`, pas de dépendance réseau ; ⚠️ non compilé en sandbox faute d'accès à `repo.maven.apache.org` ; le formulaire de contribution est de nouveau cassé jusqu'à LL-3013 — voir PROJECT_STATUS.md), LL-3013 (formulaire de contribution : champ unique `adresse` au lieu de `latitude`/`longitude`, message d'erreur backend affiché directement — répare le formulaire cassé depuis LL-3012 ; `tsc --noEmit` et `vite build` validés).

Prochaine tâche

Traiter LL-3014 — Tests d'intégration.

Objectifs :

Tester de bout en bout : inscription → connexion → accès à un endpoint protégé ; soumission d'une activité avec une adresse → vérification que latitude/longitude sont bien sauvegardées (géocodage) ; JWT expiré → accès refusé. Couvrir les cas principaux (succès + échecs). Dépend de LL-3007, LL-3008, LL-3012 (tous déjà faits).

Règles importantes
Ne pas ajouter de fonctionnalités hors MVP.
Conserver une architecture monolithique modulaire.
Toutes les évolutions passent par des User Stories.
Les collecteurs ne doivent jamais écrire directement en base.
La documentation doit être mise à jour à chaque évolution importante.
