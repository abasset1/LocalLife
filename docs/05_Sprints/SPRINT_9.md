# Sprint 9 — Bêta et corrections post-bêta

**Statut :** ⏳ À faire

---

# Objectif

Sprint 8 clôturé avec une décision **GO bêta conditionnel** (`LL-8009`,
voir `docs/PROJECT_STATUS.md`).

Le Sprint 9 a pour objectif de rendre LocalLife accessible dans un
environnement bêta réel, de permettre les premiers tests par un panel
d'utilisateurs et de corriger les problèmes réellement identifiés pendant
ces tests.

Le sprint poursuit la méthode adoptée précédemment :

* chaque problème ou évolution technique est traité comme un ticket ;
* chaque ticket possède ses propres critères d'acceptation ;
* aucune nouvelle fonctionnalité métier n'est ajoutée sur simple hypothèse ;
* les corrections supplémentaires sont ajoutées au sprint lorsqu'elles sont
  réellement identifiées.

La chaîne cible du Sprint 9 est :

```text
Utilisateur bêta
      ↓
Frontend LocalLife
      ↓
API Backend
      ↓
PostgreSQL / PostGIS
      ↑
Collecteurs Avignon
      ↑
Agendas configurés au Sprint 8
```

---

# Périmètre

## Inclus

### Mise à disposition de la bêta

* préparation de l'environnement bêta ;
* déploiement du backend ;
* déploiement du frontend ;
* configuration de la base de données bêta ;
* configuration des secrets et variables d'environnement ;
* configuration HTTPS et CORS ;
* configuration des collecteurs Avignon ;
* vérification de l'alimentation automatique ;
* validation du fonctionnement depuis Internet.

### Tests bêta

* tests du MVP depuis l'environnement en ligne ;
* utilisation par un panel d'utilisateurs ;
* identification des erreurs ;
* identification des problèmes de données ;
* identification des problèmes UX ;
* collecte et qualification des retours.

### Corrections

* correction des problèmes réellement identifiés ;
* tests de non-régression ;
* stabilisation de la version bêta.

## Exclus

* nouveau domaine métier ;
* nouvelle fonctionnalité majeure ;
* refonte graphique importante ;
* optimisation d'architecture non justifiée par un problème réel ;
* fonctionnalité développée uniquement sur hypothèse ;
* préparation de la V1 avant analyse des retours bêta.

---

# Tickets

## LL-9001 — Ne plus afficher les activités hors période sur les recherches publiques

**Priorité : Haute**

**Statut : ✅ traité — en attente de confirmation `mvn verify` par Alex**

**Dépendance :** aucune

### Constat

Les recherches publiques
(`GET /api/v1/activities/nearby`,
`GET /api/v1/activities/within-bounds`)
ne filtrent actuellement que sur le statut (`PUBLISHED` uniquement,
LL-6004).

Une activité terminée ou prévue dans le futur peut donc être retournée
par les recherches publiques.

### Objectif

Ne plus retourner par défaut une activité dont la période
`[start_date, end_date]` ne couvre pas la date du jour.

Le paramètre `date` existant (`LL-4005`) doit continuer à fonctionner
lorsqu'il est explicitement fourni.

### Points de décision

* portée : uniquement les recherches publiques
  `findNearby` / `findWithinBounds` ;
* les consultations administratives restent consultables sans restriction
  de date ;
* une activité est considérée comme en cours lorsque
  `start_date <= aujourd'hui <= end_date` ;
* lorsque `end_date` est absente, `start_date` est utilisé ;
* lorsqu'un paramètre `date` explicite est fourni, il conserve son
  comportement actuel.

### Critères d'acceptation

* une activité dont `end_date < aujourd'hui` n'apparaît plus dans
  `/nearby` / `/within-bounds` sans paramètre `date` explicite ;
* une activité dont `start_date > aujourd'hui` n'apparaît plus dans les
  mêmes conditions ;
* une activité en cours continue de s'afficher ;
* une activité sans `end_date` est correctement traitée ;
* le paramètre `date` existant continue de fonctionner ;
* la consultation administrative n'est pas affectée ;
* les tests couvrent les cas terminée, future, en cours et sans
  `end_date`.

---

## LL-9002 — Préparer l'environnement de déploiement bêta

**Priorité : Haute**

**Statut : ✅ traité** — voir `docs/02_Architecture/BETA_DEPLOYMENT.md`.

**Dépendance :** aucune

### Objectif

Définir et préparer un environnement dédié à la bêta, distinct de
l'environnement de développement local.

### À traiter

* choix de l'hébergement ;
* définition des composants nécessaires ;
* configuration de l'environnement ;
* définition des variables d'environnement ;
* définition de la stratégie de gestion des secrets ;
* définition des URL frontend/backend.

### Critères d'acceptation

* l'architecture de l'environnement bêta est documentée ;
* frontend, backend et base de données sont identifiés ;
* l'environnement bêta est distinct du développement local ;
* les secrets nécessaires sont identifiés ;
* aucun secret n'est ajouté au dépôt Git ;
* la procédure de déploiement est documentée.

---

## LL-9003 — Déployer le backend et la base de données bêta

**Priorité : Haute**

**Dépendance :** LL-9002

### Objectif

Rendre l'API LocalLife opérationnelle dans l'environnement bêta avec sa
propre base de données.

### À traiter

* déploiement du backend ;
* déploiement/configuration de PostgreSQL/PostGIS ;
* configuration des migrations ;
* configuration des variables d'environnement ;
* configuration des secrets ;
* configuration du profil d'exécution bêta.

### Critères d'acceptation

* PostgreSQL/PostGIS est opérationnel ;
* les migrations du projet sont appliquées ;
* le backend démarre sans dépendance à l'environnement local ;
* l'API est accessible depuis Internet ;
* le health check fonctionne ;
* l'authentification fonctionne ;
* aucun secret n'est exposé dans le dépôt ;
* aucun secret ou mot de passe n'apparaît dans les logs.

---

## LL-9004 — Déployer le frontend et rendre LocalLife accessible en ligne

**Priorité : Haute**

**Dépendance :** LL-9003

### Objectif

Permettre à un utilisateur extérieur à l'environnement de développement
d'accéder à LocalLife et d'utiliser le MVP.

### Critères d'acceptation

* le frontend est accessible depuis Internet ;
* l'accès se fait en HTTPS ;
* le frontend utilise l'API bêta et non une URL localhost ;
* l'inscription fonctionne ;
* la connexion fonctionne ;
* la carte fonctionne ;
* les recherches fonctionnent ;
* le détail d'une activité fonctionne ;
* la contribution fonctionne ;
* aucun élément de configuration de développement ne subsiste dans le
  build bêta.

---

## LL-9005 — Sécuriser la configuration de la bêta

**Priorité : Haute**

**Dépendance :** LL-9003, LL-9004

### Objectif

Garantir que l'environnement bêta est suffisamment sécurisé pour être
accessible à des utilisateurs externes.

### À traiter

* HTTPS ;
* CORS ;
* secrets ;
* JWT ;
* protection des endpoints administratifs ;
* configuration des comptes ;
* exposition des informations techniques.

### Critères d'acceptation

* les communications frontend/backend utilisent HTTPS ;
* aucun secret n'est présent côté frontend ;
* les secrets backend proviennent de la configuration sécurisée de
  l'environnement ;
* un utilisateur `USER` ne peut pas accéder aux opérations `ADMIN` ;
* les endpoints administratifs restent protégés ;
* aucune information sensible n'est exposée par l'API ;
* aucune information sensible n'est exposée dans les logs.

---

## LL-9006 — Activer et vérifier l'alimentation automatique Avignon en bêta

**Priorité : Haute**

**Dépendance :** LL-9003

### Objectif

Vérifier que les agendas Avignon configurés pendant le Sprint 8
continuent à alimenter automatiquement l'environnement bêta.

### À traiter

* configuration des agendas validés au Sprint 8 ;
* configuration de l'exécution automatique ;
* vérification de la collecte ;
* vérification de la persistance ;
* vérification de la gestion des doublons ;
* vérification des erreurs de collecte.

### Critères d'acceptation

* les agendas Avignon validés au Sprint 8 sont configurés ;
* le mécanisme d'exécution automatique est actif ;
* plusieurs agendas sont effectivement collectés ;
* les activités collectées sont persistées en base ;
* les doublons sont gérés conformément au comportement validé au
  Sprint 8 ;
* une erreur d'une source n'empêche pas les autres sources d'être
  collectées ;
* les résultats des collectes sont observables ;
* une activité collectée peut être retrouvée via l'API.

---

## LL-9007 — Valider le parcours complet depuis Internet

**Priorité : Haute**

**Dépendance :** LL-9004, LL-9005, LL-9006

### Objectif

Vérifier que LocalLife fonctionne réellement de bout en bout dans
l'environnement bêta.

### Parcours de validation

```text
Agenda Avignon
      ↓
Collector
      ↓
Base de données
      ↓
API
      ↓
Frontend
      ↓
Carte
```

### Critères d'acceptation

* une collecte réelle est exécutée ;
* les activités collectées sont présentes en base ;
* les activités sont retournées par l'API ;
* les activités sont visibles sur la carte ;
* la recherche permet de retrouver les activités ;
* le détail d'une activité fonctionne ;
* l'inscription fonctionne ;
* la connexion fonctionne ;
* la contribution fonctionne ;
* les opérations administratives fonctionnent avec un compte autorisé ;
* aucun problème bloquant n'est identifié.

---

## LL-9008 — Préparer et lancer le panel bêta

**Priorité : Haute**

**Dépendance :** LL-9007

### Objectif

Permettre à un premier panel d'utilisateurs réels de tester LocalLife dans
des conditions normales.

### Préparation

* sélectionner un petit panel de testeurs ;
* transmettre l'URL bêta ;
* définir les consignes générales ;
* préparer les missions de test ;
* préparer le moyen de remontée des problèmes ;
* définir les informations à recueillir.

### Principe

Les utilisateurs ne doivent pas être guidés bouton par bouton.

Les missions doivent permettre d'observer leur comportement réel.

### Exemples de missions

* trouver une activité intéressante à Avignon ;
* explorer la carte ;
* rechercher une activité ;
* consulter le détail d'une activité ;
* créer une activité ;
* revenir ultérieurement vérifier les nouvelles activités.

### Critères d'acceptation

* le panel est constitué ;
* chaque testeur dispose d'un accès à la bêta ;
* les consignes sont disponibles ;
* les missions sont définies ;
* le mécanisme de remontée des problèmes fonctionne ;
* les premiers tests utilisateurs ont été réalisés.

---

## LL-9009 — Suivre et qualifier les problèmes bêta

**Priorité : Haute**

**Dépendance :** LL-9008

### Objectif

Centraliser les problèmes et retours identifiés pendant la bêta afin de
permettre leur traitement méthodique.

### Chaque problème doit être qualifié

* description du constat ;
* contexte ;
* étapes de reproduction lorsque pertinentes ;
* comportement attendu ;
* comportement observé ;
* impact ;
* priorité ;
* ticket associé lorsqu'une correction est nécessaire.

### Classification

**Bloquant**

Empêche l'utilisation d'une fonctionnalité essentielle ou de l'application.

**Critique**

Dégrade fortement un parcours essentiel.

**Important**

Problème réel avec un impact significatif mais non bloquant.

**Mineur**

Problème de faible impact.

**Amélioration**

Suggestion ou besoin fonctionnel ne constituant pas un bug.

### Critères d'acceptation

* tous les problèmes identifiés sont enregistrés ;
* chaque problème possède une priorité ;
* les doublons sont regroupés ;
* les problèmes nécessitant une correction possèdent un ticket ;
* les simples suggestions d'évolution sont placées dans le backlog ;
* aucune amélioration fonctionnelle n'est développée sans décision
  explicite.

---

# Tickets correctifs supplémentaires

Les tickets `LL-9010` et suivants sont créés uniquement lorsqu'un problème
réel est identifié pendant la bêta.

Ils suivent la structure :

```text
LL-90XX — <problème>

Priorité :
Statut :
Dépendance :

Constat

Objectif

Critères d'acceptation

Tests nécessaires
```

Ils ne doivent pas être pré-remplis avec des problèmes hypothétiques.

---

# Definition of Done

Le Sprint 9 est terminé lorsque :

* LocalLife est accessible sur Internet ;
* l'environnement bêta est distinct du développement ;
* frontend et backend sont opérationnels ;
* PostgreSQL/PostGIS est opérationnel ;
* HTTPS est actif ;
* les secrets sont correctement externalisés ;
* l'authentification fonctionne ;
* les autorisations fonctionnent ;
* les collecteurs Avignon fonctionnent automatiquement ;
* plusieurs agendas Avignon alimentent la base ;
* le parcours `collector → base → API → carte` est validé ;
* le panel bêta a pu utiliser l'application ;
* les problèmes rencontrés ont été qualifiés ;
* les problèmes bloquants et critiques retenus ont été corrigés ;
* les corrections ont été testées ;
* aucun problème bloquant connu ne subsiste.

---

# Fin du Sprint

La clôture du Sprint 9 doit donner lieu à un bilan de la bêta.

Deux situations sont possibles.

## Bêta satisfaisante

Les problèmes restants sont mineurs et aucune évolution majeure n'est
nécessaire.

→ Préparation de la suite vers une V1.

## Bêta nécessitant des évolutions

Des problèmes importants ou des besoins fonctionnels significatifs sont
identifiés.

→ Ils sont priorisés dans le backlog et servent de base au sprint suivant.

Le sprint suivant ne doit pas être défini avant l'analyse des résultats
réels de la bêta.
