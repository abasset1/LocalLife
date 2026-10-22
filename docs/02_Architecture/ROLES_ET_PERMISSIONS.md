# Rôles, permissions et gestion des comptes

Matrice de référence (sprint « Rôles, permissions et gestion des comptes », RBAC-01, issue #154). Elle décrit
ce que le **serveur** autorise ; le site et l'application mobile n'en sont que le reflet. Aucune décision d'accès
n'est prise par une interface seule.

## 1. Rôles techniques et offres commerciales : deux notions distinctes

| Notion | Valeurs | Où | Qui la change | Ce qu'elle ouvre |
|---|---|---|---|---|
| **Rôle** (accès technique) | `USER`, `PRO`, `ADMIN` | `users.role` (+ `users.pro_role_source`, V71) | `USER` à l'inscription ; `PRO` posé par la validation de l'offre Pro et retiré à sa fin, ou attribué à la main par un administrateur ; `ADMIN` uniquement par procédure d'exploitation (§ 5) | Routes protégées (§ 3) |
| **Statut** du compte | `ACTIVE`, `SUSPENDED` | `users.account_status` (V69) | Un administrateur (suspension motivée, réactivation) | `SUSPENDED` : plus de connexion ni de route authentifiée |
| **Offre** (commerciale) | `FREE`, `PREMIUM`, `PRO` | `subscription` (V67, ADR-0014) | L'utilisateur demande, l'équipe active | Avantages de l'offre ; l'offre Pro ouvre l'espace professionnel |

* Un abonnement, quel qu'il soit, ne donne **jamais** de droit d'administration.
* **L'offre Pro pilote le rôle** (décision du 22/10/2026) : sa validation donne le rôle `PRO`
  (`pro_role_source = SUBSCRIPTION`), sa fin le retire. Un administrateur peut aussi attribuer `PRO` à la main
  (`pro_role_source = ADMIN`, partenaire, gratuité) : ce rôle-là ne dépend d'aucun abonnement. Les interfaces ne
  consultent qu'un indicateur, `hasProAccess` (`GET /api/v1/users/me`).
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
| Consultation : activités, lieux, food trucks, catégories, référentiels (`GET /api/v1/activities`, `/places`, `/foodtrucks`, `/categories`...) — liste exacte au § 3 bis | ✅ | ✅ | ✅ | ✅ |
| Liste interne des sources (`GET /api/v1/sources/**`) | ❌ | ❌ | ❌ | ✅ |
| Toute route non déclarée publique | ❌ | selon la route | selon la route | selon la route |
| Inscription, connexion, mot de passe oublié, confirmation d'e-mail (`/api/v1/auth/**`) | ✅ | ✅ | ✅ | ✅ |
| Profil et préférences (`GET/PATCH /api/v1/users/me`, `/me/preferences`, `/me/alerts`, `/me/likes`, `/me/push-tokens`) | ❌ | ✅ | ✅ | ✅ |
| Rôle, statut ou offre de son propre compte | ❌ | ❌ (champs ignorés par `PATCH /me`) | ❌ | ❌ |
| Proposer une activité, modifier la sienne (`POST /activities`, `PUT /activities/{id}`) | ❌ | 🔒 offre Premium ou Pro, propriétaire | 🔒 propriétaire | ✅ toutes |
| Annuler / supprimer sa contribution (`PATCH /activities/{id}/cancel`, `DELETE /activities/{id}`) | ❌ | 🔒 propriétaire | 🔒 propriétaire | ✅ toutes |
| Mot de passe, adresse e-mail confirmée, suppression de son compte (`/api/v1/users/me/password`, `PATCH /me`, `/me/deletion`) | ❌ | ✅ | ✅ | ✅ (sauf suppression) |
| Food trucks : création et gestion (`/api/v1/pro/foodtrucks/**`, l'ancien `POST /api/v1/foodtrucks` est retiré) | ❌ | ❌ | 🔒 propriétaire, 1re publication validée | ✅ (sans valider les siens) |
| Abonnement (`/api/v1/users/me/subscription/**`) | ❌ | ✅ | ✅ | ✅ |
| Espace professionnel (`/api/v1/pro/**`) | ❌ | ❌ (403 ; l'offre Pro validée donne le rôle PRO) | ✅ | ✅ (sans valider ses propres contenus) |
| Comptes ciblés (`GET /api/v1/users/{id}`, `POST /api/v1/users`) | ❌ | ❌ | ❌ | ✅ |
| Gestion des sources (`POST/PUT/DELETE /api/v1/sources/**`) | ❌ | ❌ | ❌ | ✅ |
| Toute l'administration (`/api/v1/admin/**` : modération, lieux, imports, catégories, e-mails, suppression, professionnels, abonnements, **comptes**) | ❌ | ❌ | ❌ | ✅ |

Vérifié par les tests d'intégration (`AccountAdministrationIntegrationTest`, `ProSpaceIntegrationTest`,
`AdminActivityControllerIntegrationTest`, `AdminImportControllerIntegrationTest`, `AgendaDiscoveryIntegrationTest`,
`SourceControllerIntegrationTest`, `SubscriptionIntegrationTest`...), avec des comptes réels de chaque rôle
(`TestAccounts`).

## 3 bis. Actions par type de compte

### Visiteur (sans compte) — décidé le 21/10/2026

**Peut, gratuitement et sans compte :**

| Action | Routes |
|---|---|
| Voir la carte et la liste des activités, autour de soi ou dans la zone affichée | `GET /api/v1/activities`, `/activities/nearby`, `/activities/within-bounds` |
| Rechercher (texte, catégorie, date) | `GET /api/v1/activities/search` |
| Ouvrir la **fiche complète** d'une activité (description, horaires, lieux, médias, liens, accessibilité) | `GET /api/v1/activities/{id}` |
| Voir les lieux, leurs activités et les food trucks qui y passent | `GET /api/v1/places/within-bounds`, `/places/{id}`, `/places/{id}/foodtrucks`, `/activities/at-place/{id}` |
| Voir les food trucks | `GET /api/v1/foodtrucks`, `/foodtrucks/{id}` |
| Utiliser les filtres et les suggestions de villes | `GET /api/v1/categories`, `/activitytypes`, `/audiences`, `/organizertypes`, `/geocoding/city`, `/geocoding/city-suggestions` |
| Garder ses réglages d'affichage **sur l'appareil** (couche de carte, ville choisie à l'accueil) | aucune (stockage local) |
| Créer un compte, se connecter (mot de passe ou Google), confirmer son e-mail, renvoyer le lien, réinitialiser son mot de passe | `POST /api/v1/auth/*`, `GET /auth/username-availability` |
| Se désabonner d'une alerte e-mail depuis le lien reçu (le jeton du lien suffit) | `POST /api/v1/alerts/unsubscribe` |

**Ne peut pas (le serveur répond 401, l'interface invite à se connecter) :** aimer une activité ou voir ses
favoris, créer des alertes, enregistrer ses préférences de compte, proposer une activité, accéder à l'espace
professionnel, demander un abonnement, consulter la liste interne des sources.

**Décisions :**
* Fiche complète pour tous : pas de version réduite pour les visiteurs (la consultation des fiches est gratuite).
  `PremiumAccessPolicy` reste un point d'extension, aujourd'hui toujours positif.
* Favoris : compte requis (synchronisés entre site et application), pas de favoris locaux.
* `GET /api/v1/sources/**` réservé à `ADMIN` (configuration interne des imports ; les fiches gardent le nom de
  leur source).
* **Sécurité fermée par défaut** : seules les routes listées dans `SecurityConfig.PUBLIC_GET` et `PUBLIC_POST`
  sont ouvertes sans compte (plus la documentation de l'API et `/actuator/health`). Toute autre route, y compris une
  route nouvelle non déclarée, exige une connexion. Vérifié par `VisitorAccessIntegrationTest`.

### Utilisateur connecté (rôle USER, adresse confirmée) — décidé le 21/10/2026

**Peut, en plus de tout ce que fait un visiteur :**

| Action | Routes | Règle |
|---|---|---|
| Voir et modifier son nom d'utilisateur | `GET/PATCH /api/v1/users/me` | Immédiat |
| Changer d'adresse e-mail | `PATCH /api/v1/users/me`, `POST /api/v1/auth/confirm-email-change` | **Appliqué seulement au clic sur le lien envoyé à la nouvelle adresse** (valable 24 h) ; l'ancienne adresse est prévenue ; `pendingEmail` montre la demande en cours |
| Changer son mot de passe (ou en définir un, compte Google) | `POST /api/v1/users/me/password` | Mot de passe actuel exigé s'il en existe un ; **les autres sessions sont fermées** (`users.tokens_valid_after`), la session en cours reçoit un nouveau jeton. Une réinitialisation par e-mail ferme aussi toutes les sessions |
| Supprimer son compte | `POST /api/v1/users/me/deletion` | Mot de passe exigé (ou `SUPPRIMER` pour un compte Google). Activités **publiées** conservées sans auteur ; contributions non publiées, favoris, alertes, préférences, connexions Google, demandes de gestion de lieu supprimés ; abonnement terminé, historique conservé sans lien vers la personne ; lieux et food trucks créés conservés sans propriétaire. Interdit à un administrateur |
| Préférences de carte, favoris, alertes (10 au plus, rayon ≤ 50 km) | `/api/v1/users/me/preferences`, `/likes`, `/alerts` | — |
| Passer à **Premium** | `POST /api/v1/users/me/subscription/subscribe` | Activé tout de suite (pas de paiement branché) |
| Demander l'offre **Pro** | même route | **Demande examinée par l'équipe** (Administration → Professionnels → Abonnements) ; l'offre en cours reste active jusqu'à l'approbation |
| Retirer sa demande, résilier | `/me/subscription/withdraw`, `/cancel` | — |
| Voir, annuler, supprimer ses contributions existantes | `GET /activities/mine`, `PATCH /{id}/cancel`, `DELETE /{id}` | Toujours permis à l'auteur, même sans Premium |

**Ne peut pas** (sans offre Premium ou Pro) : **proposer une activité ni en modifier une** (403, message invitant à
passer à Premium). `GET /api/v1/users/me` expose `canContribute` pour que les interfaces orientent vers l'abonnement.

**Règles de contenu :**
* Une activité proposée part en modération (`PENDING`).
* **Une activité publiée puis modifiée par son auteur repart en modération** et quitte la carte jusqu'à validation
  (auparavant, elle restait en ligne : un contenu validé pouvait être remplacé). Une activité refusée et corrigée
  aussi. Un administrateur modifie sans changer le statut.

Vérifié par `AccountSelfServiceIntegrationTest`, `ContributionManagementIntegrationTest`,
`SubscriptionIntegrationTest`, `AuthenticationFlowIntegrationTest` et `ActivityControllerTest`.

### Professionnel (rôle PRO, offre Pro ou attribution) — décidé le 22/10/2026

**Accès.** L'offre Pro, demandée par l'utilisateur et validée par l'équipe, donne le rôle `PRO` ; sa fin
(résiliation échue, retrait par l'équipe, expiration) le retire. Un administrateur peut attribuer `PRO` à la main,
indépendamment de tout abonnement ; il ne peut pas retirer un rôle qui découle d'une offre en cours (409 : retirer
l'abonnement). `ProAccessPolicy` décide à chaque requête ; `hasProAccess` le reflète pour les interfaces.

**Peut, en plus de tout ce que fait un utilisateur connecté :**

| Action | Routes | Règle |
|---|---|---|
| Proposer et modifier ses activités | `POST/PUT /api/v1/activities` | Comme Premium : modération inchangée |
| Rattacher une activité à un lieu qu'il gère | champ `hostPlaceId` | Lieu **publié** qu'il gère (400 sinon) ; la fiche affiche « proposé par » tant qu'il le gère |
| Demander la gestion d'un lieu existant | `/api/v1/pro/places/{id}/claims`, `/pro/claims/**` | Décision de l'équipe, **notifiée par e-mail** (acceptée, refusée, précisions demandées) |
| Créer un lieu | `/api/v1/pro/places` | Brouillon modifiable puis modération ; un administrateur ne publie jamais un lieu qu'il a créé |
| Modifier un lieu **publié** qu'il gère (importé ou créé) | `POST /api/v1/pro/places/{id}/changes` | **Proposition** (adresse, position, description, site, téléphone, e-mail) appliquée seulement après validation par l'équipe ; la fiche reste en ligne en attendant ; une modification validée **prime sur les réimports** (`location.manager_edited_at`) ; e-mail à la décision |
| Renoncer à un lieu | `POST /api/v1/pro/places/{id}/renounce` | Ses propositions en attente sont retirées. Plusieurs gestionnaires par lieu restent possibles |
| Food trucks et emplacements | `/api/v1/pro/foodtrucks/**`, `/pro/presences/**` | **La première publication est validée par l'équipe** (`PENDING`, puis publié ou refusé avec motif, e-mail) ; ensuite il publie, suspend, republie et gère ses emplacements sans validation. L'équipe peut suspendre à tout moment |
| Offre Pro | `/api/v1/users/me/subscription/**` | Premium est refusé tant qu'une offre Pro est en cours (409) ; résilier une offre Pro sans échéance la garde **jusqu'à la fin du mois** |

**Quand l'accès prend fin** (offre terminée ou retirée, rôle retiré) : ses food trucks publiés sont masqués
(`suspended_for_access_at`), ses droits de gestion révoqués (`revoked_reason = ACCESS_LOST`), ses demandes de gestion
et propositions en cours retirées. Il garde la consultation de ses food trucks et peut les archiver. **Si l'accès
revient, food trucks et droits de gestion reviennent** (pas les demandes retirées). La fin d'une période est
effective à l'échéance exacte ; le masquage des contenus suit au passage horaire de l'expiration. À la suppression
du compte, tout est retiré de la même façon, sans retour possible.

**Ne peut pas :** valider ses propres demandes, propositions ou food trucks (même administrateur), modifier
directement un lieu publié, publier un food truck sans validation la première fois.

**Côté équipe** (`/api/v1/admin/**`) : file des food trucks à valider (`/admin/foodtrucks?status=PENDING`,
`/{id}/approve`, `/{id}/reject`), propositions de modification (`/admin/place-changes/**`), gestionnaires d'un lieu
(`/admin/places/{id}/managers`, retrait motivé `/managers/{userId}/revoke`, notifié par e-mail).

Vérifié par `ProAccountRulesIntegrationTest`, `ProFoodTruckIntegrationTest`, `ProSpaceIntegrationTest` et
`SubscriptionIntegrationTest`.

### Administrateur

À définir dans l'étape suivante ; l'état actuel est décrit par la matrice du § 3.

## 4. Gestion des comptes (API d'administration)

Réservée à `ADMIN`. Réponses sans secret : jamais de hash de mot de passe, de JWT ni de jeton de vérification.

| Méthode et chemin | Rôle | Codes |
|---|---|---|
| `GET /api/v1/admin/accounts?q=&role=&status=&page=&size=` | Liste paginée (≤ 100), plus récents d'abord. `q` : partie de l'e-mail ou du nom, ou identifiant exact | 200 ; 400 filtre ou pagination invalide |
| `GET /api/v1/admin/accounts/{id}` | Un compte | 200 ; 404 |
| `PATCH /api/v1/admin/accounts/{id}/role` `{"role":"PRO"}` | Bascule `USER` ↔ `PRO` (attribution manuelle, indépendante de l'offre) | 200 ; 400 rôle inconnu ; 403 `ADMIN` demandé, compte administrateur ou propre compte ; 404 ; 409 retrait d'un rôle issu d'une offre Pro en cours |
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
