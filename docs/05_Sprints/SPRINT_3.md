# Sprint 3 — Authentification et gestion des utilisateurs

**Statut :** À faire

---
## 🎯 **Objectif**
Permettre aux utilisateurs de :
- **S’inscrire** avec un mot de passe sécurisé.
- **Se connecter/déconnecter** via JWT.
- **Accéder à des fonctionnalités protégées** (ex : soumettre une activité authentifié).
- **Gérer leur profil** (mettre à jour email/username).

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
- **Enrichissement de l’entité `User`** :
  - Ajout des champs : `passwordHash`, `role` (par défaut `USER`).
  - Migration Flyway pour ces nouveaux champs.
- **Endpoints protégés** :
  - Exemple : `POST /api/v1/activities` (seulement pour les utilisateurs connectés).
- **Frontend** :
  - Page de login/register.
  - Affichage du nom d’utilisateur connecté.
  - Bouton de déconnexion.

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

- Intégrer **BCrypt** (ou Argon2) pour hacher les mots de passe.
- **Critères** :
  - Ne jamais stocker le mot de passe en clair.
  - Vérification du mot de passe possible (méthode `matches`).

---
### LL-3004 — Service d’authentification
**Statut :** À faire
**Dépendances :** LL-3003

- Créer un service `AuthService` avec :
  - `register(username, email, password)` → crée un utilisateur + hache le mot de passe.
  - `login(email, password)` → retourne un JWT si valide.
- **Critères** :
  - Tests unitaires pour les cas :
    - Mot de passe incorrect.
    - Email inexistant.
    - Succès.

---
### LL-3005 — Génération de JWT
**Statut :** À faire
**Dépendances :** LL-3004

- Utiliser une librairie JWT (ex : `jjwt` pour Java).
- **Contenu du token** :
  - `userId`
  - `email`
  - `role`
  - Date d’expiration (ex : 24h).
- **Critères** :
  - Token valide et vérifiable.
  - Clé secrète stockée dans les `application.properties` (ou variables d’environnement).

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
### LL-3012 — Tests d’intégration
**Statut :** À faire
**Dépendances :** LL-3007, LL-3008

- Tester :
  - Inscription → connexion → accès à un endpoint protégé.
  - JWT expiré → accès refusé.
- **Critères** :
  - Couverture des cas principaux (succès + échecs).

---
### LL-3013 — Mise à jour de la documentation
**Statut :** À faire
**Dépendances :** Tous les tickets ci-dessus

- Mettre à jour :
  - `README.md` (ajouter section "Authentification").
  - `CHANGELOG.md` (nouveautés du Sprint 3).
  - `PROJECT_STATUS.md` (statut des sprints).

---
---
## 🔗 **Dépendances**



LL-3001 → LL-3002 → LL-3003 → LL-3004 → LL-3005 → LL-3006 → LL-3007 LL-3008 (dépend de LL-3006) LL-3009 → LL-3010 → LL-3011 LL-3012 (dépend de LL-3007, LL-3008) LL-3013 (dépend de tous les billets)
texte
Photocopieuse

---
## ✅ **Definition of Done**
Le sprint est terminé lorsque :
- Un utilisateur peut **s’inscrire** et **se connecter**.
- Les endpoints protégés sont **accessibles uniquement avec un JWT valide**.
- Le frontend **affiche l’utilisateur connecté** et permet de se déconnecter.
- La documentation est à jour.

---
## 🚫 **Hors périmètre**
- Récupération de mot de passe.
- OAuth (Google, Facebook, etc.).
- 2FA (double authentification).
- Gestion fine des rôles (ex : ACL).
- Audit des logs de sécurité.

---
## 🔐 **Recommandations de sécurité**
- Utiliser **HTTPS** en production.
- Ne jamais stocker le JWT dans les cookies non sécurisés.
- Limiter la durée de validité du JWT (ex : 24h).
- Utiliser des **clés secrètes fortes** pour signer le JWT.



