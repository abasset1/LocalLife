# Sprint 4 — Recherche et découverte géographique

**Statut :** 🟡 En cours

---
# 🎯 Objectif

Transformer la carte actuelle en véritable outil de découverte locale.

À la fin du sprint, l'utilisateur doit pouvoir :

---
# **Tickets en cours / terminés**
Aucun ticket terminé pour l'instant. Les tickets **LL-4001 à LL-4015** sont à faire ou en cours.

---
# **Dépendances avec les Sprints précédents**

* rechercher les activités autour d'une position ;
* voir uniquement les activités correspondant à la zone affichée ;
* filtrer les activités par catégorie ;
* filtrer les activités par date ;
* utiliser sa position actuelle pour découvrir les activités proches.

---
# 📦 Périmètre

## Inclus

* recherche géographique PostGIS ;
* recherche par rayon ;
* recherche par zone cartographique ;
* filtres par catégorie ;
* filtre par date ;
* géolocalisation utilisateur ;
* mise à jour dynamique des marqueurs ;
* gestion des états de chargement et d'erreur.

## Exclus

* moteur de recherche textuel avancé ;
* Elasticsearch/OpenSearch ;
* recommandations ;
* favoris ;
* notifications ;
* clustering avancé ;
* collecteurs externes ;
* application mobile native ;
* statistiques ;
* IA.

---
# 📋 Tickets

---

## LL-4001 — Définir la recherche géographique

**Priorité : Haute**

### Objectif
Définir le contrat de recherche géographique utilisé par le backend.

### À réaliser
Définir les paramètres permettant de rechercher des activités :
* latitude ;
* longitude ;
* rayon.

Définir également le format de réponse.

### Critères d'acceptation
* contrat documenté ;
* paramètres clairement définis ;
* aucun nouveau moteur de recherche ajouté ;
* compatible avec PostgreSQL/PostGIS.

---

## LL-4002 — Ajouter la recherche géographique PostGIS

**Priorité : Haute**
**Dépendance :** LL-4001

### Objectif
Permettre au backend de rechercher les activités situées dans un rayon donné.

### À réaliser
Utiliser PostGIS pour :
* calculer la distance ;
* filtrer les activités ;
* retourner uniquement les activités situées dans le rayon demandé.

### Critères d'acceptation
* recherche basée sur PostGIS ;
* distance calculée côté base ;
* résultats corrects ;
* tests couvrant au minimum une activité proche et une activité hors rayon.

---

## LL-4003 — Endpoint des activités proches

**Priorité : Haute**
**Dépendance :** LL-4002

### Endpoint



GET /api/v1/activities/nearby
text
Copier

### Paramètres



latitude
longitude
radius
text
Copier

### Exemple



GET /api/v1/activities/nearby?latitude=43.2965&longitude=5.3698&radius=5000
text
Copier

### Critères d'acceptation
* endpoint fonctionnel ;
* paramètres validés ;
* réponse JSON cohérente avec l'API existante ;
* gestion des paramètres invalides ;
* documentation OpenAPI mise à jour.

---
## LL-4004 — Filtre par catégorie

**Priorité : Haute**
**Dépendance :** LL-4003

### Objectif
Permettre de limiter les résultats à une ou plusieurs catégories.

### Exemple



GET /api/v1/activities/nearby
?latitude=43.2965
&longitude=5.3698
&radius=5000
&categoryId=1
text
Copier

### Critères d'acceptation
* filtre fonctionnel ;
* catégorie inexistante gérée proprement ;
* combinaison avec la recherche géographique fonctionnelle.

---
## LL-4005 — Filtre par date

**Priorité : Haute**
**Dépendance :** LL-4003

### Objectif
Permettre de rechercher les activités correspondant à une date.

### Critères
Une activité est considérée comme active pour une date donnée lorsque sa période couvre cette date.

### Critères d'acceptation
* recherche par date fonctionnelle ;
* gestion correcte des activités d'une journée ;
* gestion correcte des activités sur plusieurs jours ;
* tests automatisés.

---
## LL-4006 — Recherche par zone cartographique

**Priorité : Haute**
**Dépendance :** LL-4003

---
## LL-4007 — Bounding Box

**Priorité : Haute**
**Dépendance :** LL-4006

---
## LL-4008 — Filtre par catégorie (frontend)

**Priorité : Haute**
**Dépendance :** LL-4004

---
## LL-4009 — Filtre par date (frontend)

**Priorité : Haute**
**Dépendance :** LL-4005

---
## LL-4010 — Géolocalisation utilisateur

**Priorité : Haute**
**Dépendance :** LL-4003

### Objectif
Permettre à l'utilisateur d'utiliser sa position actuelle.

### À réaliser
Utiliser l'API de géolocalisation du navigateur.

### Critères d'acceptation
* demande explicite de permission ;
* position récupérée si autorisée ;
* gestion du refus ;
* gestion d'une erreur de géolocalisation ;
* aucune position utilisateur persistée en base.

---
## LL-4011 — Recherche autour de l'utilisateur

**Priorité : Haute**
**Dépendance :** LL-4010, LL-4003

### Objectif
Afficher les activités proches de la position utilisateur.

### Critères d'acceptation
* utilisation des coordonnées obtenues par le navigateur ;
* appel de l'API `/nearby` ;
* affichage des résultats ;
* gestion du chargement ;
* gestion de l'absence de résultats.

---
## LL-4012 — Chargement dynamique de la carte

**Priorité : Haute**
**Dépendance :** LL-4006, LL-4007

### Objectif
Actualiser les activités lorsque l'utilisateur déplace ou zoome la carte.

### Critères d'acceptation
* nouvelle recherche après déplacement significatif ;
* nouvelle recherche après changement de zoom ;
* pas de requête à chaque événement de mouvement ;
* gestion du chargement ;
* suppression des anciens marqueurs avant affichage des nouveaux résultats.

---
## LL-4013 — États frontend

**Priorité : Moyenne**
**Dépendance :** LL-4012

### Objectif
Gérer proprement les différents états de recherche.

### États
* chargement ;
* résultats ;
* aucun résultat ;
* erreur.

### Critères d'acceptation
Chaque état est visible et compréhensible par l'utilisateur.

---
## LL-4014 — Tests d'intégration

**Priorité : Haute**
**Dépendances :** LL-4003, LL-4004, LL-4005, LL-4006

### Tester
* recherche par rayon ;
* activité hors rayon ;
* filtre catégorie ;
* filtre date ;
* bounding box ;
* paramètres invalides ;
* combinaison de filtres.

### Critères d'acceptation
Les scénarios principaux sont automatisés.

---
## LL-4015 — Mise à jour de la documentation

**Priorité : Haute**
**Dépendance :** tous les tickets précédents

### Mettre à jour
* README.md
* CHANGELOG.md
* PROJECT_STATUS.md
* documentation OpenAPI
* documentation API si nécessaire.

---
# 🔗 Dépendances



LL-4001
↓
LL-4002
↓
LL-4003
├── LL-4004
│     ↓
│   LL-4008
│
├── LL-4005
│     ↓
│   LL-4009
│
└── LL-4006
↓
LL-4012
LL-4003
↓
LL-4007
↓
LL-4008 / LL-4009 / LL-4012
LL-4010
↓
LL-4011
LL-4012
↓
LL-4013
LL-4003 / LL-4004 / LL-4005 / LL-4006
↓
LL-4014
↓
LL-4015
text
Copier

---
# 🚫 Règles du sprint

L'IA qui réalise un ticket ne doit pas :

* introduire Elasticsearch/OpenSearch ;
* modifier l'architecture ;
* créer un moteur de recommandation ;
* ajouter des favoris ;
* créer un système de cache ;
* implémenter du clustering ;
* ajouter un collecteur ;
* modifier le modèle métier sans ticket dédié ;
* anticiper le Sprint 5.

---
# Definition of Done

Le Sprint 4 est terminé lorsque :

* l'utilisateur peut rechercher les activités autour d'une position ;
* la carte peut charger les activités de sa zone visible ;
* les catégories peuvent être filtrées ;
* une date peut être filtrée ;
* la géolocalisation navigateur fonctionne ;
* les erreurs et absences de résultats sont gérées ;
* les tests backend principaux sont présents ;
* la documentation est à jour.

---
# Livrable du Sprint

> Une carte réellement exploitable pour découvrir la vie locale autour de soi.

Le sprint ne cherche pas encore à augmenter fortement le volume de données. Le prochain enjeu sera **l'alimentation de la carte avec des données réelles**, notamment via les collecteurs.



