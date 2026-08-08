# Sprint 2 — Gestion des utilisateurs et des catégories

**Statut :** À faire

---
## 🎯 Objectif
Permettre aux utilisateurs de :
- **Créer un compte** (sans authentification avancée).
- **Consulter les catégories d'activités**.
- **Contribuer** en signalant une nouvelle activité (sans modération automatique).

**Exclusions :**
- Pas de système de rôles.
- Pas de modération automatique.
- Pas de notifications.

---
## 📦 Périmètre

### Inclus :
- Module **User** (entité + repository + service).
- Module **Category** (entité + repository + service).
- API REST pour :
  - Créer un utilisateur.
  - Lister les catégories.
  - Ajouter une activité (contribution basique).
- Mise à jour du frontend pour :
  - Afficher les catégories.
  - Formulaire de contribution.

### Exclus :
- Authentification (JWT, OAuth).
- Rôles (admin, utilisateur).
- Modération des contributions.
- Notifications.
- Recherche avancée.

---
## 📋 Tickets

---
### LL-2001 — Créer le module User
**Priorité : Haute**
**Statut :** À faire

- Structure du module (domain, application, infrastructure).
- **Critères d'acceptation** :
  - Compilation OK.
  - Aucune logique métier complexe.

---
### LL-2002 — Créer l'entité User
**Statut :** À faire

- Champs : `id`, `username`, `email`, `createdAt`.
- **Critères** :
  - Entité validée par le lead dev.

---
### LL-2003 — Migration Flyway pour User
**Statut :** À faire

- Créer la table **User**.
- **Critères** :
  - Migration automatique.
  - Base démarrable.

---
### LL-2004 — Repository User
**Statut :** À faire

- Opérations : `save()`, `findById()`, `findByEmail()`.
- **Critères** :
  - Tests unitaires passés.

---
### LL-2005 — Service User
**Statut :** À faire

- Fonctions : `createUser()`, `getUserById()`.
- **Critères** :
  - Service testé.

---
### LL-2006 — Créer le module Category
**Statut :** À faire

- Structure du module.
- Entité **Category** avec champs :
  - `id`
  - `name`
  - `description`

---
### LL-2007 — Migration Flyway pour Category
**Statut :** À faire

- Créer la table **Category**.
- **Critères** :
  - Migration automatique.

---
### LL-2008 — Repository Category
**Statut :** À faire

- Opérations : `findAll()`, `findById()`.

---
### LL-2009 — Service Category
**Statut :** À faire

- Fonctions : `getAllCategories()`, `getCategoryById()`.

---
### LL-2010 — API REST pour User
**Statut :** À faire

- Endpoints :
  - `POST /api/v1/users` (créer un utilisateur).
  - `GET /api/v1/users/{id}` (récupérer un utilisateur).

---
### LL-2011 — API REST pour Category
**Statut :** À faire

- Endpoints :
  - `GET /api/v1/categories` (lister les catégories).

---
### LL-2012 — Formulaire de contribution
**Statut :** À faire

- Frontend :
  - Formulaire pour soumettre une activité (titre, description, catégorie, localisation).
  - **Critères** :
    - Formulaire fonctionnel.
    - Données envoyées au backend.

---
### LL-2013 — Mise à jour de la documentation
**Statut :** À faire

- Mettre à jour :
  - `README.md`
  - `CHANGELOG.md`
  - `PROJECT_STATUS.md`

---
## 🔗 Dépendances
LL-2001 → LL-2002 → LL-2003 → LL-2004 → LL-2005 → LL-2010
LL-2006 → LL-2007 → LL-2008 → LL-2009 → LL-2011
LL-2012 (dépend de LL-2010 et LL-2011)
LL-2013 (dépend de tous les tickets)

---
## ✅ Definition of Done
Le sprint est terminé lorsque :
- Le backend démarre sans erreur.
- Les endpoints `User` et `Category` répondent.
- Le formulaire de contribution est fonctionnel.
- La documentation est à jour.

---
## 🚫 Hors périmètre
- Authentification.
- Rôles.
- Modération des contributions.
- Notifications.
- Recherche avancée.
