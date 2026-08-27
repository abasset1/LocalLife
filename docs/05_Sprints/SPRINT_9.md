# Sprint 9 — Bêta et corrections post-bêta

**Statut :** ⏳ À faire

---

# Objectif

Sprint 8 clôturé avec une décision **GO bêta conditionnel** (`LL-8009`,
voir `docs/PROJECT_STATUS.md`).

Ce sprint a deux objectifs successifs :

1. rendre LocalLife accessible dans un environnement bêta réel ;
2. permettre à Alex d'effectuer les premiers tests bêta et de traiter les
   problèmes réellement identifiés.

Le sprint ne doit pas introduire de nouvelles fonctionnalités métier.

Les corrections sont ajoutées au sprint au fur et à mesure qu'elles sont
identifiées et doivent rester autonomes et testables indépendamment.

---

# Périmètre

## Inclus

### Mise à disposition de la bêta

* déploiement du frontend ;
* déploiement du backend ;
* base de données dédiée à la bêta ;
* configuration des variables d'environnement et secrets ;
* configuration CORS ;
* HTTPS ;
* configuration des collecteurs Avignon ;
* exécution automatique des collectes ;
* vérification de l'alimentation automatique ;
* validation du parcours complet depuis Internet.

### Bêta

* tests du MVP par Alex ;
* tests par un panel d'utilisateurs ;
* identification des erreurs et comportements inattendus ;
* collecte des retours utilisateurs ;
* corrections nécessaires identifiées pendant la bêta.

## Exclus

* nouveau domaine métier ;
* nouvelle fonctionnalité majeure ;
* refonte graphique importante ;
* optimisation d'architecture non justifiée par un problème réel ;
* fonctionnalités ajoutées uniquement sur hypothèse ;
* préparation de la V1 avant analyse des retours bêta.

---

# Phase 0 — Préparation de la bêta

Cette phase est un **prérequis au test utilisateur** et ne constitue pas une
nouvelle fonctionnalité du produit.

## Déploiement

L'application doit être accessible depuis Internet dans un environnement
distinct du développement local.

La cible est :

```text
Utilisateur
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

## Vérifications minimales

* frontend accessible depuis Internet ;
* backend accessible depuis Internet ;
* HTTPS actif ;
* base de données bêta opérationnelle ;
* secrets absents du dépôt ;
* CORS correctement configuré ;
* authentification fonctionnelle ;
* rôles correctement protégés ;
* health check fonctionnel ;
* collecteurs Avignon configurés ;
* exécution automatique des collectes active ;
* activités collectées visibles via l'API ;
* activités collectées visibles sur la carte.

### Critère de passage

La bêta ne peut commencer que si une activité provenant d'une collecte réelle
peut être suivie de bout en bout :

```text
Agenda Avignon
      ↓
Collector
      ↓
Activity en base
      ↓
API
      ↓
Carte LocalLife
```

---

# Phase 1 — Bêta utilisateur

## Objectif

Permettre à un petit panel d'utilisateurs réels d'utiliser LocalLife dans des
conditions normales.

Les utilisateurs ne doivent pas être guidés pas à pas dans l'application.

Ils doivent recevoir des objectifs généraux afin de permettre l'observation
des problèmes réels d'utilisation.

### Exemples de missions

* trouver une activité intéressante à Avignon ;
* explorer les activités disponibles sur la carte ;
* rechercher une activité ;
* consulter le détail d'une activité ;
* créer une activité ;
* revenir sur l'application et consulter de nouvelles activités.

Les missions exactes peuvent être adaptées au profil des testeurs.

---

# Phase 2 — Corrections post-bêta

Les problèmes sont ajoutés sous forme de tickets `LL-90xx`.

Chaque ticket doit contenir :

* constat ;
* objectif ;
* périmètre ;
* dépendances ;
* critères d'acceptation ;
* tests nécessaires ;
* statut.

Aucune correction ne doit être ajoutée uniquement sur la base d'une
hypothèse.

---

# Tickets

## LL-9001 — Ne plus afficher les activités hors période sur les recherches publiques

**Priorité : Haute**

**Statut : ✅ traité — en attente de confirmation `mvn verify` par Alex**

**Dépendance :** aucune

### Constat

Les recherches publiques ne filtrent actuellement que sur le statut
`PUBLISHED`.

Une activité terminée ou prévue dans le futur peut donc être retournée par
les recherches publiques.

### Objectif

Ne plus retourner par défaut une activité dont la période ne couvre pas la
date du jour.

Le paramètre `date` existant (`LL-4005`) doit continuer à fonctionner
lorsqu'il est explicitement fourni.

### Critères d'acceptation

* une activité terminée n'apparaît plus dans les recherches publiques ;
* une activité future n'apparaît plus sans `date` explicite ;
* une activité en cours continue d'apparaître ;
* une activité sans `end_date` continue d'être traitée correctement ;
* `date` explicite conserve son comportement ;
* la consultation administrative n'est pas affectée ;
* les tests correspondants sont présents.

---

# Ajout de tickets pendant la bêta

Les tickets `LL-9002` et suivants seront créés uniquement lorsqu'un problème
réel aura été identifié.

Exemples de catégories possibles :

* bug backend ;
* bug frontend ;
* problème d'affichage ;
* problème de données collectées ;
* problème de recherche ;
* problème de carte ;
* problème d'authentification ;
* problème de contribution ;
* problème UX bloquant ou critique.

Le numéro du ticket doit être attribué au moment où le problème est
formalisé.

---

# Priorisation des corrections

Les problèmes identifiés pendant la bêta sont classés :

### Bloquant

Empêche l'utilisation d'une fonction essentielle ou rend l'application
inutilisable.

→ Correction prioritaire avant poursuite de la bêta.

### Critique

Dégrade fortement un parcours essentiel mais ne bloque pas complètement
l'application.

→ Correction prioritaire.

### Important

Problème réel ayant un impact significatif mais permettant de poursuivre
l'utilisation.

→ À traiter selon la capacité du sprint.

### Mineur

Problème cosmétique ou faible impact.

→ Peut être reporté.

### Amélioration

Demande ou idée d'évolution qui n'est pas un bug.

→ Backlog, pas de développement automatique pendant ce sprint.

---

# Definition of Done

Le Sprint 9 est terminé lorsque :

* LocalLife est accessible sur Internet ;
* l'environnement bêta est distinct du développement ;
* frontend et backend fonctionnent depuis Internet ;
* PostgreSQL/PostGIS est opérationnel ;
* HTTPS et les secrets sont correctement configurés ;
* les collecteurs Avignon alimentent automatiquement la bêta ;
* le parcours `collector → base → API → carte` est validé ;
* le panel bêta a pu utiliser l'application ;
* les problèmes identifiés ont été formalisés ;
* les problèmes bloquants et critiques retenus ont été corrigés ;
* les corrections ont été retestées ;
* aucun nouveau problème bloquant connu ne subsiste.

---

# Fin du Sprint

Le Sprint 9 ne déclenche **pas automatiquement** une nouvelle phase
fonctionnelle.

À sa clôture, les retours bêta sont analysés.

Deux possibilités :

### Cas A — Corrections suffisantes

La bêta est stable et les retours ne justifient pas d'évolution majeure.

→ Décision de poursuivre vers une V1.

### Cas B — Problèmes ou besoins importants

Les problèmes ou besoins identifiés sont priorisés.

→ Ils alimentent le backlog et servent de base au prochain sprint.

**Le Sprint suivant ne doit donc être défini qu'après analyse des résultats
réels de la bêta.**
