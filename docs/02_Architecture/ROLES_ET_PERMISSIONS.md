# Rôles, permissions et gestion des comptes

Matrice de référence (sprint « Rôles, permissions et gestion des comptes », RBAC-01, issue #154). Elle décrit
ce que le **serveur** autorise ; le site et l'application mobile n'en sont que le reflet. Aucune décision d'accès
n'est prise par une interface seule.

## 1. Rôles techniques et offres commerciales : deux notions distinctes

| Notion | Valeurs | Où | Qui la change | Ce qu'elle ouvre |
|---|---|---|---|---|
| **Rôle** (accès technique) | `USER`, `PRO`, `ADMIN` | `users.role` | `USER` à l'inscription ; `USER` ↔ `PRO` par un administrateur ; `ADMIN` uniquement par procédure d'exploitation (§ 5) | Routes protégées (§ 3) |
| **Statut** du compte | `ACTIVE`, `SUSPENDED` | `users.account_status` (V69) | Un administrateur (suspension motivée, réactivation) | `SUSPENDED` : plus de connexion ni de route authentifiée |
| **Offre** (commerciale) | `FREE`, `PREMIUM`, `PRO` | `subscription` (V67, ADR-0014) | L'utilisateur demande, l'équipe active | Avantages de l'offre ; l'offre Pro ouvre l'espace professionnel |

* Un abonnement, quel qu'il soit, ne donne **jamais** de droit d'administration.
* Le rôle `PRO` et l'offre Pro ouvrent tous deux l'espace professionnel (`ProAccessPolicy`), mais restent
  indépendants : attribuer ou retirer le rôle `PRO` ne touche pas à l'abonnement, et inversement.
* La carte, les listes et les fiches restent consultables gratuitement, sans compte et quelle que soit l'offre.
* Pas de rôle `MODERATOR` ni `SUPERADMIN` : à introduire dans un sprint dédié, avec sa propre matrice et sa migration.

## 2. Comment le serveur décide

1. `JwtFilter` vérifie la signature et l'expiration du jeton (24 h), puis **relit en base** le rôle et le statut du
   compte à chaque requête. Le rôle inscrit dans le jeton n'est plus utilisé pour décider d'un accès (avant ce
   sprint, un administrateur rétrogradé gardait ses droits jusqu'à l'expiration de son jeton).
2. Compte introuvable ou suspendu : **401** (message explicite). Les clients effacent alors le jeton et continuent en
   visiteur.
3. Règles par chemin dans `SecurityConfig` (`authenticated()` / `hasRole("ADMIN")`, filet de sécurité
   `/api/v1/admin/** → ADMIN`), puis règles métier dans les services (propriétaire d'une activité, accès pro, un
   administrateur ne modère pas sa propre demande...).

## 3. Matrice des permissions

Légende : ✅ autorisé · ❌ refusé (401 sans jeton, 403 avec un rôle insuffisant) · 🔒 autorisé sous condition métier
vérifiée côté serveur.

| Domaine (endpoints) | Visiteur | USER | PRO | ADMIN |
|---|---|---|---|---|
| Consultation : activités, lieux, food trucks, catégories, référentiels (`GET /api/v1/activities`, `/places`, `/foodtrucks`, `/categories`...) | ✅ | ✅ | ✅ | ✅ |
| Inscription, connexion, mot de passe oublié, confirmation d'e-mail (`/api/v1/auth/**`) | ✅ | ✅ | ✅ | ✅ |
| Profil et préférences (`GET/PATCH /api/v1/users/me`, `/me/preferences`, `/me/alerts`, `/me/likes`, `/me/push-tokens`) | ❌ | ✅ | ✅ | ✅ |
| Rôle, statut ou offre de son propre compte | ❌ | ❌ (champs ignorés par `PATCH /me`) | ❌ | ❌ |
| Proposer une activité, modifier / annuler / supprimer la sienne (`POST /activities`, `PUT/DELETE /activities/{id}`) | ❌ | 🔒 propriétaire | 🔒 propriétaire | ✅ toutes |
| Créer un food truck (`POST /api/v1/foodtrucks`) | ❌ | 🔒 accès pro | ✅ | ✅ |
| Abonnement (`/api/v1/users/me/subscription/**`) | ❌ | ✅ | ✅ | ✅ |
| Espace professionnel (`/api/v1/pro/**`) | ❌ | 🔒 offre Pro | ✅ | ✅ (sans modérer ses propres demandes) |
| Comptes ciblés (`GET /api/v1/users/{id}`, `POST /api/v1/users`) | ❌ | ❌ | ❌ | ✅ |
| Gestion des sources (`POST/PUT/DELETE /api/v1/sources/**`) | ❌ | ❌ | ❌ | ✅ |
| Toute l'administration (`/api/v1/admin/**` : modération, lieux, imports, catégories, e-mails, suppression, professionnels, abonnements, **comptes**) | ❌ | ❌ | ❌ | ✅ |

Vérifié par les tests d'intégration (`AccountAdministrationIntegrationTest`, `ProSpaceIntegrationTest`,
`AdminActivityControllerIntegrationTest`, `AdminImportControllerIntegrationTest`, `AgendaDiscoveryIntegrationTest`,
`SourceControllerIntegrationTest`, `SubscriptionIntegrationTest`...), avec des comptes réels de chaque rôle
(`TestAccounts`).

## 4. Gestion des comptes (API d'administration)

Réservée à `ADMIN`. Réponses sans secret : jamais de hash de mot de passe, de JWT ni de jeton de vérification.

| Méthode et chemin | Rôle | Codes |
|---|---|---|
| `GET /api/v1/admin/accounts?q=&role=&status=&page=&size=` | Liste paginée (≤ 100), plus récents d'abord. `q` : partie de l'e-mail ou du nom, ou identifiant exact | 200 ; 400 filtre ou pagination invalide |
| `GET /api/v1/admin/accounts/{id}` | Un compte | 200 ; 404 |
| `PATCH /api/v1/admin/accounts/{id}/role` `{"role":"PRO"}` | Bascule `USER` ↔ `PRO` | 200 ; 400 rôle inconnu ; 403 `ADMIN` demandé, compte administrateur ou propre compte ; 404 |
| `POST /api/v1/admin/accounts/{id}/suspend` `{"reason":"…"}` | Suspension, motif obligatoire (3 à 500 caractères) | 200 ; 400 motif ; 403 compte administrateur ou propre compte ; 404 ; 409 déjà suspendu |
| `POST /api/v1/admin/accounts/{id}/reactivate` `{"reason":"…"}` | Réactivation, motif facultatif | 200 ; 403 propre compte ; 404 ; 409 déjà actif |
| `GET /api/v1/admin/accounts/audit?targetUserId=&page=&size=` | Journal, lecture seule | 200 |

Compatibilité : `GET /api/v1/admin/users?email=` (recherche exacte, écran Professionnels) est inchangé.
`PATCH /api/v1/admin/users/{id}/role` partage désormais les règles et le journal de la console : **les transitions
interdites (rôle `ADMIN` demandé, compte administrateur) répondent 403 au lieu de 400** ; un rôle inconnu reste 400.

Les modèles DTO : `AccountRepository.Account` (id, username, email, role, emailVerified, createdAt, status,
statusReason, statusChangedAt) et `UserResponse` (contrat historique, sans hash).

### Suspension

* Effet immédiat : le statut est relu à chaque requête, un jeton déjà émis cesse de fonctionner (401) ; la connexion
  par mot de passe ou Google répond **423** (`Locked`), distinct du 403 « adresse non confirmée ». Ce refus n'est
  donné qu'après vérification des identifiants : il ne permet pas d'énumérer les comptes.
* Aucune donnée n'est supprimée ; la réactivation rend tous les droits (rôle inchangé).
* Le statut n'est pas porté par l'entité `User` : aucune sauvegarde ordinaire d'un compte (profil, mot de passe,
  confirmation d'e-mail, Google) ne peut réactiver un compte suspendu. Seul `AccountRepository.updateStatus` l'écrit.
* Un compte administrateur ne se suspend pas et ne se rétrograde pas par l'API : l'application ne peut donc pas faire
  perdre le dernier accès administrateur.

### Journal d'audit (`account_audit_event`, V69)

Chaque tentative de changement de rôle, de suspension ou de réactivation, **réussie ou refusée**, est enregistrée :
date UTC, acteur (identifiant et e-mail au moment de l'action), compte cible, action, ancienne et nouvelle valeur
(rôle ou statut), motif, résultat (`SUCCESS`/`REFUSED`) et explication du refus. Aucun secret. Un déclencheur
PostgreSQL refuse toute modification ou suppression d'une entrée ; l'API n'expose qu'une lecture.

## 5. Gestion des administrateurs (procédure d'exploitation)

L'API ne crée, ne promeut, ne rétrograde ni ne suspend jamais un administrateur.

* **Premier administrateur** : `AdminBootstrapRunner`, au démarrage, si `LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL` et
  `LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD` sont définies et qu'aucun `ADMIN` n'existe (aucun compte par défaut, aucun mot
  de passe en dur).
* **Ajouter ou retirer un administrateur ensuite** : opération en base, par une personne ayant l'accès
  d'exploitation, tracée à la main dans le journal :

  ```sql
  BEGIN;
  UPDATE users SET role = 'ADMIN' WHERE email = 'personne@exemple.fr';   -- ou 'USER' pour retirer
  INSERT INTO account_audit_event (occurred_at, actor_email, target_user_id, action, old_value, new_value,
                                   reason, outcome)
  SELECT now() AT TIME ZONE 'UTC', 'exploitation:<votre nom>', id, 'ROLE_CHANGE', 'USER', 'ADMIN',
         '<motif>', 'SUCCESS' FROM users WHERE email = 'personne@exemple.fr';
  COMMIT;
  ```

  L'effet est immédiat (rôle relu à chaque requête). Garder au moins un administrateur actif.
* Un futur rôle `SUPERADMIN` pourra remplacer cette procédure après décision explicite.

## 6. Interfaces

* **Site** : Administration → **Comptes** (recherche, filtres rôle et statut, pagination, attribuer/retirer PRO,
  suspendre avec motif, réactiver, historique par compte). L'écran d'administration n'est accessible qu'à un
  administrateur, mais l'API reste seule juge.
* **Application mobile** (MOB-01) : décision de **ne pas** embarquer d'administration native. Un administrateur voit,
  dans Compte, une entrée « Administration (site web) » qui ouvre la console du site (même domaine que l'API). Elle
  n'apparaît que si `GET /api/v1/users/me` (rôle relu en base) répond `ADMIN` ; aucune logique de permission n'est
  dupliquée dans l'application. Un compte suspendu reçoit 401 et sa session est fermée ; la connexion affiche le
  message du serveur (423).
