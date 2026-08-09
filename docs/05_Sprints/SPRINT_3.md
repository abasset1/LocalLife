# Sprint 3 — Authentification, géocodage et gestion des utilisateurs

**Statut :** À faire

---
## 🎯 **Objectif**
Permettre aux utilisateurs de :
- **S’inscrire** avec un mot de passe sécurisé.
- **Se connecter/déconnecter** via JWT.
- **Accéder à des fonctionnalités protégées** (ex : soumettre une activité authentifié).
- **Gérer leur profil** (mettre à jour email/username).
- **Contribuer avec une adresse** (géocodage automatique en `latitude`/`longitude`).

**Exclusions :**
- Pas de récupération de mot de passe.
- Pas d’authentification via OAuth (Google, Facebook).
- Pas de 2FA (double authentification).

---
## 📦 **Périmètre**

### Inclus :
- **Authentification** :
  - Inscription (`POST /api/v1/auth/register`).
  - Connexion (`POST /api/v1/auth/login` → retourne un JWT).
  - Déconnexion (invalidation côté frontend).
- **Sécurité** :
  - Hachage des mots de passe (BCrypt).
  - Validation des inputs (ex : email valide, mot de passe fort).
  - Middleware pour vérifier le JWT sur les endpoints protégés.
- **Géocodage** :
  - Intégration d'une API de géocodage (ex : Nominatim) pour convertir l'adresse en `latitude`/`longitude`.
  - Mise à jour du formulaire de contribution pour accepter une **adresse** au lieu de coordonnées manuelles.
- **Enrichissement de l’entité `User`** :
  - Ajout des champs : `passwordHash`, `role` (par défaut `USER`).
  - Migration Flyway pour ces nouveaux champs.
- **Endpoints protégés** :
  - Exemple : `POST /api/v1/activities` (seulement pour les utilisateurs connectés).
- **Frontend** :
  - Page de login/register.
  - Affichage du nom d’utilisateur connecté.
  - Bouton de déconnexion.
  - Formulaire de contribution avec **champ adresse** (géocodage automatique).

### Exclus :
- Récupération de mot de passe.
- OAuth.
- 2FA.
- Gestion avancée des rôles (ex : ACL).

---
## 📋 **Tickets**

---
### LL-3001 — Étendre l’entité User
**Priorité : Haute**
**Statut :** À faire
**Dépendances :** LL-2002 (entité User du Sprint 2)

- Ajouter les champs :
  - `passwordHash` (String, non-null).
  - `role` (Enum : `USER`, `ADMIN` ; par défaut `USER`).
- **Critères d’acceptation** :
  - Migration Flyway générée et testée.
  - Compilation OK.

---
### LL-3002 — Migration Flyway pour les nouveaux champs
**Statut :** À faire
**Dépendances :** LL-3001

- Créer une migration pour ajouter `passwordHash` et `role` à la table `user`.
- **Critères** :
  - Migration appliquée automatiquement au démarrage.
  - Données existantes non perdues (valeur par défaut pour `role` : `USER`).

---
### LL-3003 — Service de hachage des mots de passe
**Statut :** À faire
**Dépendances :** LL-3001

- Intégrer **BCrypt** pour hacher les mots de passe.
- **Critères** :
  - Ne jamais stocker le mot de passe en clair.
  - Vérification du mot de passe possible (méthode `matches`).

---
 ## LL-3004 — Implémenter le login
  **Statut :** À faire
 **Dépendances :** LL-3003
 
 ### Objectif
 
 Implémenter la connexion utilisateur.
 
 ### À réaliser
 
 - vérifier email + mot de passe ;
 - utiliser BCrypt ;
-- retourner un JWT.
+- générer et retourner un vrai JWT.
+
+### JWT
+
+Le JWT doit être généré dès ce ticket.
+
+Ajouter la librairie JJWT et créer le composant/service minimal nécessaire
+à la génération du token.
+
+Le payload doit contenir :
+
+- `userId`
+- `email`
+- `role`
+
+Le ticket ne doit cependant pas implémenter :
+
+- la validation du JWT ;
+- le filtre d'authentification ;
+- la protection des endpoints ;
+- le refresh token ;
+- la gestion avancée des sessions.
+
+Ces responsabilités seront traitées par les tickets suivants.
+
+### Secret de signature
+
+Le secret JWT ne doit jamais être hardcodé dans le code source.
+
+La configuration doit utiliser une variable d'environnement :
+
+```properties
+jwt.secret=${JWT_SECRET}
+```
+
+Pour le développement local, `JWT_SECRET` peut être défini dans un fichier
+`.env` non versionné ou directement dans l'environnement d'exécution.
+
+Le vrai secret de production doit être fourni par le gestionnaire de secrets
+de l'environnement de déploiement.
+
+Ne jamais committer un secret réel.
 
 ### Critères d'acceptation
 
 - un utilisateur valide peut se connecter ;
 - un mauvais mot de passe est refusé ;
-- un JWT est retourné.
+- un JWT réel est retourné ;
+- le JWT contient `userId`, `email` et `role` ;
+- le secret de signature provient de la configuration/environnement ;
+- aucun secret réel n'est présent dans Git.
 
 ---
 
 ## LL-3005 — Génération de JWT
 **Statut :** À faire
 **Dépendances :** LL-3004

 ### Objectif
 
-Créer le service de génération JWT.
+Finaliser et isoler la responsabilité de génération JWT.
 
 ### À réaliser
 
-- ajouter jjwt ;
-- créer le service JWT ;
-- définir les claims ;
-- configurer le secret.
+- reprendre le composant de génération créé dans LL-3004 ;
+- isoler clairement la responsabilité de génération du JWT ;
+- ajouter les tests unitaires dédiés à la génération ;
+- vérifier les claims ;
+- vérifier la signature ;
+- vérifier la configuration du secret.
+
+### Important
+
+LL-3005 ne doit pas créer une deuxième implémentation JWT.
+
+La génération fonctionnelle du JWT a déjà été introduite dans LL-3004
+afin que le critère d'acceptation du login soit immédiatement satisfait.
+
+LL-3005 sert à finaliser, tester et isoler cette responsabilité.
 
 ### Critères d'acceptation
 
-- JWT signé ;
-- claims : userId, email, role ;
-- secret configurable.
+- JWT signé correctement ;
+- claims `userId`, `email` et `role` présents ;
+- secret configurable ;
+- tests unitaires couvrant la génération ;
+- aucune duplication de logique avec `AuthService`.

---
### LL-3006 — Middleware de vérification JWT
**Statut :** À faire
**Dépendances :** LL-3005

- Créer un filtre Spring (`JwtFilter`) pour :
  - Extraire le JWT de l’en-tête `Authorization`.
  - Valider le token (signature, expiration).
  - Remplir le `SecurityContext` avec les infos de l’utilisateur.
- **Critères** :
  - Endpoints protégés accessibles uniquement avec un JWT valide.
  - Réponse `401 Unauthorized` si token invalide/manquant.

---
### LL-3007 — Endpoints d’authentification
**Statut :** À faire
**Dépendances :** LL-3004, LL-3005

- Créer un contrôleur `AuthController` avec :
  - `POST /api/v1/auth/register` :
    - Body : `{ "username", "email", "password" }`.
    - Retourne : `201 Created` + user (sans `passwordHash`).
  - `POST /api/v1/auth/login` :
    - Body : `{ "email", "password" }`.
    - Retourne : `{ "token": "<JWT>" }`.
- **Critères** :
  - Validation des inputs (ex : email format valide, mot de passe ≥ 8 caractères).
  - Messages d’erreur clairs (sans fuites de sécurité).

---
### LL-3008 — Protéger les endpoints existants
**Statut :** À faire
**Dépendances :** LL-3006

- Protéger :
  - `POST /api/v1/activities` (seulement pour les utilisateurs connectés).
  - `POST /api/v1/users` (désactiver ou protéger pour les `ADMIN`).
- **Critères** :
  - Tests manuels : accès refusé sans JWT, autorisé avec JWT valide.

---
### LL-3009 — Frontend : Pages de login/register
**Statut :** À faire
**Dépendances :** LL-3007

- Créer :
  - Page `/login` (formulaire email + mot de passe).
  - Page `/register` (formulaire username + email + mot de passe).
  - Redirection vers `/` après connexion réussie.
- **Critères** :
  - Stockage du JWT dans le `localStorage` ou `sessionStorage`.
  - Gestion des erreurs (ex : "Email ou mot de passe incorrect").

---
### LL-3010 — Frontend : Affichage de l’utilisateur connecté
**Statut :** À faire
**Dépendances :** LL-3009

- Afficher en haut de l’écran :
  - Nom de l’utilisateur connecté (ex : "Bonjour, Alex").
  - Bouton "Déconnexion" (supprime le JWT du storage).
- **Critères** :
  - Mise à jour dynamique (sans rechargement de page).

---
### LL-3011 — Frontend : Appels API avec JWT
**Statut :** À faire
**Dépendances :** LL-3009

- Configurer Axios (ou fetch) pour :
  - Ajouter automatiquement le JWT dans l’en-tête `Authorization: Bearer <token>`.
  - Gérer les erreurs `401` (rediriger vers `/login`).
- **Critères** :
  - Tous les appels aux endpoints protégés incluent le JWT.

---
### LL-3012 — Backend : Intégration du géocodage
**Statut :** À faire
**Dépendances :** LL-2012 (formulaire de contribution du Sprint 2)

- Intégrer une API de géocodage (ex : [Nominatim](https://nominatim.openstreetmap.org/)) pour convertir l'adresse en `latitude`/`longitude`.
- **Fonctionnalité** :
  - Le backend reçoit une **adresse** depuis le frontend.
  - Le backend appelle l'API de géocodage pour obtenir les coordonnées.
  - Sauvegarde en base : `latitude`, `longitude` (pas l'adresse).
- **Critères** :
  - Gestion des erreurs (ex : adresse non trouvée).
  - Ne pas sauvegarder l'adresse en base (seulement les coordonnées).

---
### LL-3013 — Frontend : Mise à jour du formulaire de contribution
**Statut :** À faire
**Dépendances :** LL-3012

- Modifier le formulaire pour :
  - Remplacer les champs `latitude`/`longitude` par un champ **`adresse`** (texte).
  - Afficher un message si le géocodage échoue.
- **Critères** :
  - L'utilisateur ne saisi **que l'adresse**.
  - Le frontend envoie **uniquement l'adresse** au backend.

---
### LL-3014 — Tests d’intégration
**Statut :** À faire
**Dépendances :** LL-3007, LL-3008, LL-3012

- Tester :
  - Inscription → connexion → accès à un endpoint protégé.
  - Soumission d'une activité avec une **adresse** → vérification que `latitude`/`longitude` sont sauvegardées.
  - JWT expiré → accès refusé.
- **Critères** :
  - Couverture des cas principaux (succès + échecs).

---
### LL-3015 — Mise à jour de la documentation
**Statut :** À faire
**Dépendances :** Tous les tickets ci-dessus

- Mettre à jour :
  - `README.md` (ajouter sections "Authentification" et "Géocodage").
  - `CHANGELOG.md` (nouveautés du Sprint 3).
  - `PROJECT_STATUS.md` (statut des sprints).

---
---
## 🔗 **Dépendances**
