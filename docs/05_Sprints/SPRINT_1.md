# SPRINT_1.md

## Sprint 1 — Première fonctionnalité visible

**Statut :** ✅ Terminé

---

# Objectif

Livrer la première version visible de LocalLife.

À la fin du sprint, un utilisateur doit pouvoir :

* ouvrir l'application ;
* voir une carte ;
* voir plusieurs activités de démonstration ;
* consulter les informations d'une activité.

Aucune authentification.

Aucune contribution.

Aucun collecteur.

Aucune administration.

---

# Périmètre

Inclus :

* premier domaine métier Activity ;
* première migration de base de données ;
* API REST de consultation ;
* données de démonstration ;
* première intégration frontend ;
* affichage sur la carte.

Exclus :

* Place
* User
* Source
* Contribution
* JWT
* rôles
* administration
* import automatique
* recherche avancée

---

# Tickets

---

## LL-1001 — Créer le module Activity

**Priorité : Haute**

### Objectif

Créer le premier module métier.

### À réaliser

* structure du module
* packages domain
* application
* infrastructure

### Critères d'acceptation

* compilation OK
* aucune logique métier

---

## LL-1002 — Créer l'entité Activity

Créer l'entité avec les champs :

* id
* title
* description
* category
* latitude
* longitude
* startDate
* endDate
* status

Aucune relation avec d'autres entités.

---

## LL-1003 — Migration Flyway

Créer la table Activity.

Critères :

* migration automatique
* base démarrable

---

## LL-1004 — Repository Activity

Créer le repository Spring Data.

Uniquement les opérations de lecture.

---

## LL-1005 — Service Activity

Créer un service minimal.

Fonctions :

* findAll()
* findById()

---

## LL-1006 — Données de démonstration

Créer au moins cinq activités.

Exemples :

* concert
* marché
* food truck
* exposition
* cinéma

---

## LL-1007 — API REST

Créer :

GET /api/v1/activities

GET /api/v1/activities/{id}

Uniquement des endpoints de consultation.

---

## LL-1008 — Carte

Créer la première carte.

Fonctions :

* affichage
* zoom
* déplacement

Pas de géolocalisation.

---

## LL-1009 — Affichage des activités

Afficher les activités retournées par l'API.

Chaque activité apparaît sous forme de marqueur.

---

## LL-1010 — Popup activité

Au clic sur un marqueur :

Afficher :

* titre
* catégorie
* date

---

## LL-1011 — Documentation

Mettre à jour :

* README
* CHANGELOG
* PROJECT_STATUS

---

# Dépendances

LL-1001

↓

LL-1002

↓

LL-1003

↓

LL-1004

↓

LL-1005

↓

LL-1006

↓

LL-1007

↓

LL-1008

↓

LL-1009

↓

LL-1010

↓

LL-1011

---

# Definition of Done

Le sprint est terminé lorsque :

* le backend démarre ;
* le frontend démarre ;
* la migration est exécutée automatiquement ;
* l'API répond ;
* cinq activités sont disponibles ;
* la carte affiche les cinq activités ;
* un clic sur un marqueur affiche une popup ;
* la documentation est à jour.

---

# Hors périmètre

Ne pas développer :

* authentification ;
* comptes utilisateurs ;
* rôles ;
* collecteurs ;
* administration ;
* contributions ;
* recherche avancée ;
* filtres ;
* géolocalisation ;
* notifications ;
* application mobile ;
* optimisation des performances.

Toute fonctionnalité hors de cette liste devra être planifiée dans un sprint ultérieur.
