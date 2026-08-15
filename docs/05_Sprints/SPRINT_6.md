# SPRINT_6.md

# Sprint 6 — Qualité des données et administration minimale

**Statut :** À faire

---

# Objectif

Fiabiliser les données réellement présentes dans LocalLife après l'intégration de la première source externe.

À la fin du sprint, un administrateur doit pouvoir contrôler les activités importées ou publiées, corriger les données problématiques et éviter que des données manifestement invalides apparaissent sur la carte.

Le sprint reste volontairement minimal : **pas de back-office complet**.

---

# Périmètre

## Inclus

- validation renforcée des activités ;
- statut de modération ;
- contrôle administratif minimal ;
- gestion des activités importées ;
- possibilité de masquer une activité invalide ;
- amélioration de la qualité des données ;
- premiers éléments nécessaires aux food trucks.

## Exclus

- dashboard complet ;
- statistiques ;
- système de rôles complexe ;
- workflow de modération avancé ;
- notifications ;
- recommandations ;
- marketplace ;
- paiement ;
- application mobile ;
- deuxième collecteur ;
- moteur de recherche avancé.

---

# Tickets

## LL-6001 — Auditer la qualité des données

**Priorité : Haute**

### Objectif

Analyser les données produites par le Sprint 5 avant d'ajouter de nouvelles fonctionnalités.

### À vérifier

- titres ;
- descriptions ;
- dates ;
- coordonnées ;
- catégories ;
- URLs ;
- doublons ;
- activités sans localisation exploitable.

### Critères d'acceptation

- problèmes identifiés ;
- règles de validation documentées ;
- aucune modification arbitraire du modèle métier.

---

## LL-6002 — Renforcer la validation Activity

**Priorité : Haute**

**Dépendance :** LL-6001

### Objectif

Empêcher la création ou l'import de données manifestement invalides.

### À valider au minimum

- titre obligatoire ;
- coordonnées valides ;
- date cohérente ;
- catégorie valide si renseignée ;
- URL valide lorsqu'elle est fournie.

### Critères d'acceptation

- validation côté backend ;
- messages d'erreur cohérents ;
- tests unitaires.

---

## LL-6003 — Ajouter le statut de modération

**Priorité : Haute**

**Dépendance :** LL-6002

### Objectif

Permettre de distinguer une activité visible d'une activité en attente ou masquée.

### Statuts MVP

- `PENDING`
- `PUBLISHED`
- `REJECTED`

### Critères d'acceptation

- statut persisté ;
- valeur par défaut définie ;
- transitions minimales documentées ;
- aucune machine à états complexe.

---

## LL-6004 — Exclure les activités non publiées de la carte publique

**Priorité : Haute**

**Dépendance :** LL-6003

### Objectif

Empêcher qu'une activité `PENDING` ou `REJECTED` apparaisse dans les recherches publiques.

### Critères d'acceptation

Les endpoints publics de recherche ne retournent que les activités `PUBLISHED`.

---

## LL-6005 — Contrôle administratif minimal

**Priorité : Haute**

**Dépendance :** LL-6003

### Objectif

Permettre à un administrateur de consulter les activités nécessitant une intervention.

### À réaliser

Endpoint permettant de lister les activités par statut.

Exemple :

```text
GET /api/v1/admin/activities?status=PENDING
```

L'accès doit être réservé au rôle administrateur déjà présent dans le projet.

### Critères d'acceptation

- endpoint protégé ;
- accès refusé aux utilisateurs non administrateurs ;
- filtrage par statut ;
- tests de sécurité.

---

## LL-6006 — Publier ou rejeter une activité

**Priorité : Haute**

**Dépendance :** LL-6005

### Objectif

Permettre à un administrateur de modifier le statut d'une activité.

### Endpoints

```text
PATCH /api/v1/admin/activities/{id}/publish
PATCH /api/v1/admin/activities/{id}/reject
```

### Critères d'acceptation

- endpoints protégés ;
- activité existante uniquement ;
- statut correctement modifié ;
- tests backend.

---

## LL-6007 — Gestion minimale des activités importées

**Priorité : Moyenne**

**Dépendance :** LL-6006

### Objectif

Permettre de distinguer les activités issues d'une source externe des activités créées directement dans LocalLife.

### Critères

- source identifiable ;
- activité manuelle conservée ;
- activité importée conservée avec sa source ;
- aucune duplication de données.

---

## LL-6008 — Définir le modèle Food Truck

**Priorité : Moyenne**

### Objectif

Préparer l'intégration des food trucks sans créer un second système cartographique.

### Décision MVP

Un food truck doit pouvoir être représenté sur la même carte que les activités.

Ne pas créer un module complexe de gestion de tournée ou de planning.

### À définir

- nom ;
- description ;
- position ;
- catégorie/type ;
- URL ou contact ;
- statut de publication.

### Critères d'acceptation

Le modèle est documenté et compatible avec la carte existante.

---

## LL-6009 — Première intégration Food Truck

**Priorité : Moyenne**

**Dépendance :** LL-6008

### Objectif

Permettre de référencer manuellement un premier food truck.

### Critères d'acceptation

- création possible ;
- position affichable ;
- visibilité sur la carte ;
- distinction visuelle ou fonctionnelle suffisante avec une activité ;
- aucun système de commande ou paiement.

---

## LL-6010 — Tests de non-régression

**Priorité : Haute**

**Dépendance :** LL-6004, LL-6006, LL-6009

### Tester

- activité publiée visible ;
- activité en attente invisible publiquement ;
- activité rejetée invisible publiquement ;
- accès administrateur ;
- accès utilisateur standard ;
- publication ;
- rejet ;
- food truck visible sur la carte ;
- recherche géographique inchangée.

---

## LL-6011 — Documentation

**Priorité : Haute**

**Dépendance :** tous les tickets précédents

Mettre à jour :

- `PROJECT_STATUS.md`
- `ROADMAP.md`
- `CHANGELOG.md`
- documentation API ;
- documentation d'architecture si nécessaire.

---

# Dépendances

```text
LL-6001
   ↓
LL-6002
   ↓
LL-6003
   ├── LL-6004
   └── LL-6005
          ↓
       LL-6006
          ↓
       LL-6007

LL-6008
   ↓
LL-6009

LL-6004 + LL-6006 + LL-6009
   ↓
LL-6010
   ↓
LL-6011
```

---

# Règles du sprint

L'IA qui réalise un ticket ne doit pas :

- créer un back-office complet ;
- créer une nouvelle architecture d'authentification ;
- créer un nouveau système de rôles ;
- créer un workflow de modération complexe ;
- créer un système de paiement pour les food trucks ;
- créer un système de réservation ;
- créer un système de tournée automatique ;
- ajouter un deuxième collecteur ;
- modifier la carte en profondeur ;
- introduire une nouvelle infrastructure ;
- anticiper les fonctionnalités communautaires avancées.

---

# Definition of Done

Le Sprint 6 est terminé lorsque :

- les données invalides sont mieux contrôlées ;
- les activités disposent d'un statut de publication ;
- seules les activités publiées sont visibles publiquement ;
- un administrateur peut consulter les activités en attente ;
- un administrateur peut publier ou rejeter une activité ;
- les activités importées restent identifiables par leur source ;
- un premier food truck peut être référencé ;
- le food truck apparaît sur la carte ;
- les tests de non-régression passent ;
- la documentation est à jour.

---

# Livrable du Sprint

> Une carte alimentée par des données réelles, mais désormais suffisamment contrôlées pour éviter que des données invalides ou non validées soient exposées publiquement.

Le sprint suivant pourra être consacré à l'amélioration de l'expérience utilisateur et à l'élargissement maîtrisé des données, selon les résultats du MVP.
