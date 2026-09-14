# ADR-0002 — Authentification multi-provider : Google Sign-In (LL-EF-011)

## Contexte

Le ticket LL-EF-011 (`docs/05_Sprints/SPRINT_EVOL_FIX.md`) demande de
permettre la création de compte / connexion via Google, tout en explicitant
en section « Architecture » de prévoir dès maintenant une architecture
permettant plusieurs providers (`LOCAL`, `GOOGLE`, potentiellement
d'autres plus tard), sans faire dépendre l'entité `User` du fonctionnement
spécifique de Google.

Ce document trace la décision retenue, conformément à `AI_RULES.md`
(« aucun changement d'architecture sans ADR »).

## Décision

**Table séparée `user_identities`** (migration
`V28__create_user_identities_table.sql`) plutôt que des colonnes
directement sur `users` (ex. `google_sub`, `google_email`) :

```
user_identities(id, user_id, provider, provider_user_id, created_at)
UNIQUE (provider, provider_user_id)
```

- `provider` ne contient que les providers externes (`GOOGLE` pour
  l'instant) — `LOCAL` n'y figure pas : l'authentification classique
  reste portée par `users.password_hash`, déjà en place avant ce ticket,
  aucune raison de la dupliquer dans cette table.
- `provider_user_id` est l'identifiant stable côté provider (le claim
  `sub` du token OpenID Connect pour Google), jamais l'email — l'email
  associé à un compte Google peut changer, le `sub` non.
- La contrainte d'unicité `(provider, provider_user_id)` empêche
  nativement qu'une même identité externe soit liée à deux comptes
  LocalLife différents.
- Un compte peut donc n'avoir aucune ligne dans `user_identities`
  (compte classique pur), une seule (créé ou lié via Google), ou
  plusieurs providers différents à l'avenir sans aucune migration
  supplémentaire sur `users`.

**Vérification du token Google** via `NimbusJwtDecoder`
(`spring-security-oauth2-jose`, apporté par un nouveau composant
`GoogleAuthService`) plutôt qu'une implémentation manuelle de la
vérification de signature/JWKS : approche officiellement recommandée par
Spring Security pour valider un JWT émis par un tiers (OIDC), déjà
éprouvée et maintenue (rotation de clé Google gérée automatiquement),
plutôt que de réimplémenter ce mécanisme à la main comme le fait
`JwtService` pour nos propres tokens (HMAC symétrique, pas de rotation de
clé côté serveur à gérer — un besoin structurellement différent).
`GoogleAuthService` vérifie explicitement signature, émetteur
(`https://accounts.google.com`), audience (notre `google.client-id`) et
`email_verified` — voir sa javadoc pour le détail de chaque vérification
et pourquoi elle est nécessaire.

**Compte existant lié plutôt que doublonné** : si aucune `UserIdentity`
Google n'existe pour le `sub` reçu mais qu'un compte `users` existe déjà
pour l'email vérifié par Google, la nouvelle identité est associée à ce
compte existant (`AuthService#loginOrRegisterWithGoogle`) plutôt que de
créer un second compte — critère d'acceptation explicite du ticket. Fiable
uniquement parce que l'email provient du token vérifié, jamais d'un champ
transmis séparément par le frontend (voir plus bas, section Sécurité).

**`password_hash` vide plutôt que nullable** pour un compte créé
exclusivement via Google : la colonne reste `NOT NULL` (aucune migration
sur ce point, voir `V6__add_password_hash_and_role_to_users.sql`) ;
`AuthService#login` a été mis à jour pour rejeter explicitement un login
classique quand `passwordHash` est vide, avant tout appel à
`PasswordHashingService#matches` — sans cette garde, `BCryptPasswordEncoder`
lève une exception interne sur une chaîne vide (pas un hash BCrypt
valide), ce qui aurait produit un 500 au lieu d'un 401 propre pour toute
tentative de mot de passe sur un compte Google-only.

## Sécurité (exigences du ticket, et comment elles sont couvertes)

| Exigence | Couverture |
| --- | --- |
| Protocole OAuth 2.0 / OpenID Connect | ID token Google vérifié via OIDC standard (`NimbusJwtDecoder` + validation émetteur/audience). |
| Vérifier l'identité côté backend | `GoogleAuthService#verify` : signature, émetteur, audience, expiration — jamais un simple décodage du JWT sans vérification. |
| Ne jamais faire confiance à un email fourni par le frontend | `GoogleLoginRequest` ne transporte que le token brut ; email/nom ne sont lus que depuis le token une fois sa signature vérifiée. |
| Ne jamais stocker le mot de passe Google | Aucun mot de passe Google n'est jamais transmis à LocalLife (flux OIDC standard) ; rien de tel n'est stocké. |
| Tokens Google non exposés inutilement | Le token Google n'est utilisé qu'en mémoire le temps de la requête `POST /api/v1/auth/google` ; seul le JWT LocalLife (déjà émis pour l'auth classique) est retourné au frontend. |
| Permissions limitées | Scopes `openid email profile` uniquement côté frontend (Google Identity Services) — pas d'accès à d'autres données Google. |

## Alternatives envisagées

| Option | Rejetée car |
| --- | --- |
| Colonnes `google_sub`/`google_email` directement sur `users` | Fait dépendre `users` du fonctionnement spécifique de Google (violerait explicitement la demande du ticket) ; un provider supplémentaire impliquerait une nouvelle migration sur `users` à chaque fois. |
| Vérification manuelle du JWT Google (parsing JWKS à la main) | Réimplémente un mécanisme déjà résolu et maintenu par Spring Security ; risque d'erreur plus élevé sur un point sensible (validation de signature) pour aucun bénéfice. |
| Compte Google et compte classique toujours distincts (jamais liés) | Contredit le critère d'acceptation explicite du ticket (« si un compte existe déjà [...] l'utilisateur est connecté à celui-ci »), et surprendrait un utilisateur s'étant déjà inscrit classiquement avec la même adresse. |

## Conséquences

- Nouvelle dépendance : `spring-security-oauth2-jose` (version gérée par
  `spring-boot-starter-parent`, pas de version explicite à maintenir).
- Nouvelle variable d'environnement obligatoire pour ce flux :
  `GOOGLE_CLIENT_ID` (voir `.env.example`) — absente, le flux échoue
  explicitement (audience vide) sans empêcher le démarrage de
  l'application ni affecter l'authentification classique.
- `AuthService` dépend désormais de `GoogleAuthService`/
  `UserIdentityRepository` en plus de ses dépendances existantes.
- Non couvert par ce ticket, laissé pour un ticket ultérieur si besoin :
  associer un provider Google à un compte déjà connecté (depuis
  `ProfilePage`) — le flux actuel ne couvre que la connexion/inscription
  initiale.
