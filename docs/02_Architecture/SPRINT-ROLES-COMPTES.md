# Sprint — Rôles, permissions et gestion des comptes

**Statut :** spécification prête pour développement  
**Périmètre :** backend Spring Boot, site React, application mobile  
**Branche de préparation :** `docs/sprint-roles-comptes`

## 1. Audit de l'existant

Audit documentaire et ciblé du dépôt `abasset1/LocalLife` (branche `main`) : README, modèle de rôle, contrôleur administratif des utilisateurs, entité utilisateur, configuration Spring Security, écran d'administration web, espace Professionnels, modèle d'abonnement et ADR-0014.

### Éléments déjà présents

- Authentification JWT et confirmation d'adresse e-mail.
- Rôles techniques `USER`, `PRO`, `ADMIN` dans `Role`.
- Inscription classique attribuant `USER` par défaut.
- `GET /api/v1/admin/users?email=...` pour rechercher un compte par e-mail.
- `PATCH /api/v1/admin/users/{id}/role` pour basculer `USER` ↔ `PRO`; le contrôleur refuse les autres valeurs et les comptes `ADMIN`.
- Les chemins `/api/v1/admin/**` sont réservés à `ADMIN` par la configuration de sécurité.
- La gestion professionnelle, la modération des demandes de lieux, les food trucks et les abonnements disposent déjà de fonctions d'administration.
- Les offres commerciales sont déjà modélisées séparément des rôles : `FREE`, `PREMIUM`, `PRO` et les droits d'accès correspondants sont gérés par les abonnements (`V67`, ADR-0014). La carte, la liste et les fiches restent gratuites.

### Écarts observés dans les fichiers consultés

1. Le modèle de rôle ne contient que `USER`, `PRO`, `ADMIN` : aucun rôle `MODERATOR` ou `SUPERADMIN` n'est actuellement défini.
2. L'interface web « Professionnels » recherche un compte par adresse e-mail et permet de lui attribuer/retirer `PRO`; ce n'est pas encore une console générale de gestion des comptes.
3. L'entité `User` consultée contient l'identifiant, le nom d'utilisateur, l'e-mail, le hash du mot de passe, le rôle, la date de création et l'état de confirmation de l'e-mail. Aucun statut de compte (actif/suspendu) ni date de dernière connexion n'apparaît dans cette entité.
4. Le rôle `ADMIN` regroupe les capacités administratives existantes. Il n'existe pas encore de séparation dédiée entre modération et administration globale.
5. L'abonnement Pro et le rôle `PRO` coexistent dans l'historique du produit. Le sprint doit préserver la distinction entre rôle d'accès technique et offre commerciale, sans faire dépendre la consultation publique de l'abonnement.
6. Cet audit est un audit ciblé des fichiers cités, pas une revue exhaustive de chaque endpoint, migration et test. Les critères ci-dessous exigent donc une vérification par tests avant clôture.

## 2. Objectif du sprint

Fournir une gestion cohérente, traçable et sécurisée des comptes depuis le site et l'application mobile, en réutilisant le backend existant et sans casser les rôles, abonnements ou données professionnels déjà en production.

## 3. Décisions de conception

- **Séparer rôle et offre** :
  - Rôles : `USER`, `PRO`, `ADMIN` dans le périmètre initial.
  - Offres : `FREE`, `PREMIUM`, `PRO`, déterminées par le système d'abonnement existant.
  - Un abonnement payant ne confère jamais un rôle administratif.
- **Ne pas ajouter `MODERATOR` ou `SUPERADMIN` dans ce sprint** sans matrice de permissions exhaustive et migration sécurisée. Les prévoir dans un sprint ultérieur si la délégation des responsabilités devient nécessaire.
- L'utilisateur peut modifier son profil et ses préférences, mais ne peut pas changer son rôle, son statut ou son offre en appelant directement l'API.
- Les changements de rôle, de statut et les actions de support doivent être autorisés côté serveur, journalisés et testés.
- Ne jamais retourner le hash du mot de passe dans les DTO ou journaux.
- Protéger les opérations administratives et éviter qu'un compte puisse se promouvoir lui-même.
- La consultation de la carte, des listes et des fiches reste gratuite, quelle que soit l'offre.

## 4. User stories et tâches

### RBAC-01 — Inventaire des autorisations et contrats API
- Cartographier les contrôleurs, règles Spring Security et vérifications métier pour les routes utilisateurs, professionnels et administratives.
- Documenter les actions permises à `USER`, `PRO` et `ADMIN`.
- Vérifier que les rôles du JWT et les droits relus en base restent cohérents après modification d'un rôle.
- Livrable : matrice de permissions mise à jour dans la documentation.

**Acceptation**
- Aucun endpoint sensible n'est protégé uniquement par l'interface.
- Les tests vérifient les réponses 401 (non authentifié) et 403 (rôle insuffisant).

### ACC-01 — API de gestion générale des comptes
- Étendre l'API d'administration existante pour rechercher et parcourir les comptes avec pagination et filtres raisonnables (e-mail/nom d'utilisateur, rôle, statut si introduit).
- Fournir un DTO de gestion explicitement défini : id, username, email, role, emailVerified, createdAt et statut de compte lorsqu'il existe.
- Ne pas exposer passwordHash, tokens de vérification ou données secrètes.
- Conserver la compatibilité de `GET /api/v1/admin/users?email=...` et `PATCH /api/v1/admin/users/{id}/role`, ou fournir une transition documentée et testée.

**Acceptation**
- Un administrateur peut rechercher un compte et consulter une liste paginée.
- Un utilisateur non authentifié reçoit 401 ; un utilisateur non administrateur reçoit 403.
- Les réponses ne contiennent aucun secret.
- Les paramètres de recherche sont validés et les erreurs sont stables et documentées.

### ACC-02 — Statut de compte et suspension
- Ajouter un statut de compte explicite, distinct du rôle et de l'abonnement, avec migration Flyway rétrocompatible et valeur par défaut `ACTIVE`.
- Prévoir au minimum `ACTIVE` et `SUSPENDED`; la suppression définitive et la politique de conservation des données sont hors périmètre.
- Ajouter des opérations administratives suspendre/réactiver, avec motif obligatoire pour la suspension et journalisation.
- Refuser la connexion et/ou les opérations authentifiées d'un compte suspendu de façon cohérente. Vérifier le statut côté serveur à chaque requête protégée ou via un mécanisme d'invalidation fiable ; ne pas se fier uniquement à un JWT déjà émis.
- Protéger les comptes administrateurs contre les opérations interdites par la politique retenue et empêcher la perte accidentelle du dernier accès administrateur opérationnel.

**Acceptation**
- Migration testée sur base vide et base existante.
- Un compte suspendu ne peut plus utiliser les routes authentifiées ; un compte réactivé retrouve ses droits normaux.
- Les données du compte ne sont pas supprimées par une suspension.
- Les actions sensibles sont traçables avec auteur, cible, date et motif.

### ACC-03 — Gestion des rôles sans élévation de privilèges
- Garder `USER` comme rôle attribué à l'inscription.
- Encadrer l'attribution/retrait de `PRO` par les règles métier existantes et éviter de confondre ce rôle avec l'abonnement Pro.
- Interdire à l'API de gestion courante de créer ou promouvoir un `ADMIN`.
- Réserver la gestion des administrateurs à une procédure d'exploitation sécurisée distincte, documentée et testée, jusqu'à décision explicite sur un futur rôle `SUPERADMIN`.
- Vérifier les effets immédiats d'un changement de rôle sur le web, le mobile et les endpoints protégés.

**Acceptation**
- Un utilisateur ne peut pas modifier son propre rôle.
- `ADMIN` ne peut pas être attribué via le endpoint de gestion des comptes ordinaires.
- Un rôle inconnu ou une transition interdite renvoie 400/403 selon le contrat documenté.
- Les changements sont persistés et les permissions sont réévaluées côté serveur.

### WEB-01 — Console de gestion des comptes sur le site
- Ajouter une section « Comptes » dans l'administration existante, sans remplacer les sections Agendas, Modération, Lieux, Professionnels, Catégories, Imports, E-mails et Suppression.
- Afficher recherche, filtres, pagination, rôle, confirmation d'e-mail et statut.
- Fournir les actions autorisées selon les règles API : consulter, attribuer/retirer PRO, suspendre/réactiver.
- Demander confirmation avant une action sensible et afficher le résultat ou l'erreur de façon accessible.

**Acceptation**
- L'écran fonctionne sur mobile et ordinateur dans le navigateur.
- Aucun contrôle administratif n'est visible ou actionnable pour un utilisateur non administrateur.
- L'API reste l'autorité de décision, même si l'interface est contournée.

### MOB-01 — Accès de gestion adapté dans l'application mobile
- Ajouter une entrée « Administration des comptes » uniquement si le produit décide explicitement de permettre l'administration depuis mobile ; sinon, fournir un lien sûr vers le site web.
- Ne pas embarquer de secret administratif ni faire confiance à un rôle stocké uniquement dans le client.
- Afficher les erreurs d'autorisation et les états de chargement.
- Ne pas réimplémenter une seconde logique de permissions côté mobile.

**Acceptation**
- Les appels utilisent le même contrat API que le site.
- Un utilisateur ordinaire ne peut pas accéder aux fonctions d'administration via navigation directe.
- Le choix d'administration native ou de renvoi vers le site est documenté avant implémentation.

### AUD-01 — Journal d'audit des actions de compte
- Enregistrer au minimum : acteur, compte cible, action, ancienne/nouvelle valeur non secrète, motif, date UTC et résultat.
- Journaliser les changements de rôle et les suspensions/réactivations.
- Rendre le journal accessible aux administrateurs autorisés, sans permettre de modifier ou supprimer les entrées depuis l'application.
- Ne pas journaliser les mots de passe, JWT, tokens de vérification ou secrets.

**Acceptation**
- Chaque action sensible réussie ou refusée est traçable sans exposer de secret.
- Tests couvrant la persistance et le contrôle d'accès au journal.

### QA-01 — Tests de non-régression
- Tests unitaires des transitions de rôle/statut et des règles d'autorisation.
- Tests d'intégration des endpoints administratifs avec aucun JWT, rôle USER, rôle PRO et rôle ADMIN.
- Tests de migration Flyway, DTO sans secrets et compatibilité des clients existants.
- Tests web/mobile des états vide, chargement, succès et erreur.
- Vérifier que les abonnements et la consultation gratuite restent inchangés.

## 5. Hors périmètre

- Paiement réel ou modification de Stripe et du modèle d'abonnement.
- Nouveaux avantages Premium.
- Suppression définitive des comptes, export RGPD et politique complète de conservation.
- Système de permissions configurable par écran ou par action.
- Rôle `MODERATOR` ou `SUPERADMIN` tant que leur matrice de droits n'a pas été décidée.
- Refonte de l'authentification ou remplacement du JWT.

## 6. Ordre recommandé

1. RBAC-01 — cartographie et contrats.
2. ACC-01 et ACC-03 — API de liste/recherche et transitions de rôle.
3. ACC-02 et AUD-01 — statut, suspension et journal d'audit.
4. WEB-01 — console web.
5. MOB-01 — administration mobile ou renvoi vers le site, selon décision produit.
6. QA-01 — suite de tests complète et validation des régressions.

## 7. Définition de terminé

- Les critères d'acceptation de toutes les tâches incluses sont vérifiés.
- Migrations, API, documentation et tests sont livrés ensemble.
- Les CI backend, frontend et mobile pertinentes passent.
- Les accès ont été testés avec les rôles USER, PRO et ADMIN.
- Aucun secret n'est exposé dans les réponses ou journaux.
- La consultation de la carte, des listes et des fiches reste gratuite.
- La documentation distingue explicitement les rôles techniques et les offres commerciales.
