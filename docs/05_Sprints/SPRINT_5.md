# SPRINT_5.md

# Sprint 5 — Alimentation réelle de la carte

**Statut :** À faire

---

# Objectif

Faire évoluer LocalLife d'une carte alimentée uniquement par des données internes/de démonstration vers une carte alimentée par des **données réelles**.

À la fin du sprint, LocalLife doit pouvoir :

- recevoir des événements depuis une source externe ;
- normaliser ces données vers le modèle `Activity` ;
- éviter les doublons évidents ;
- identifier la source d'une activité ;
- importer les données de manière reproductible ;
- afficher les données importées sur la carte existante.

Le sprint ne cherche pas à multiplier les sources. **Une seule source réelle suffit pour valider l'architecture.**

---

# Périmètre

## Inclus

- modèle `Source` minimal ;
- architecture des collecteurs ;
- pipeline collecte → normalisation → validation → persistance ;
- premier collecteur réel ;
- identification de la source ;
- détection simple des doublons ;
- logs d'import ;
- tests du pipeline ;
- documentation.

## Exclus

- plusieurs collecteurs ;
- scraping complexe ;
- navigateur headless ;
- IA pour normaliser les données ;
- système de recommandation ;
- recherche plein texte ;
- Elasticsearch/OpenSearch ;
- synchronisation temps réel ;
- planificateur complexe ;
- application mobile ;
- marketplace ;
- monétisation.

---

# Principe d'architecture

Un collecteur **ne doit jamais écrire directement dans les tables métier**.

Flux imposé :

```text
Source externe
      ↓
Collecteur
      ↓
Données brutes
      ↓
Normalisation
      ↓
Validation
      ↓
Détection de doublon
      ↓
Service métier
      ↓
Activity
```

Le collecteur reste responsable de l'acquisition.

Le domaine métier reste responsable de la persistance et des règles métier.

---

# Tickets

## LL-5001 — Définir le contrat Source

**Priorité : Haute**

### Objectif

Définir le modèle minimal permettant d'identifier l'origine d'une activité importée.

### À définir

- identifiant ;
- nom ;
- type ;
- URL ;
- statut ;
- date de dernière synchronisation.

### Critères d'acceptation

- modèle documenté ;
- aucun mécanisme de collecte implémenté ;
- compatible avec les activités créées manuellement.

---

## LL-5002 — Créer le module Source

**Priorité : Haute**

**Dépendance :** LL-5001

### À réaliser

- domaine ;
- repository ;
- migration Flyway ;
- service minimal.

### Critères d'acceptation

- source persistée en base ;
- tests unitaires ;
- migration fonctionnelle.

---

## LL-5003 — Définir le contrat Collector

**Priorité : Haute**

### Objectif

Définir une interface commune aux futurs collecteurs.

Le contrat doit permettre :

- de récupérer des données ;
- d'identifier la source ;
- de retourner des données brutes normalisables.

### Important

Ne pas créer de framework générique de collecte.

Le contrat doit rester minimal.

---

## LL-5004 — Définir le modèle de données importées

**Priorité : Haute**

**Dépendance :** LL-5003

Créer un modèle interne représentant une donnée collectée avant conversion vers `Activity`.

### Critères

Le modèle doit pouvoir contenir au minimum :

- titre ;
- description ;
- date début ;
- date fin ;
- catégorie ;
- latitude ;
- longitude ;
- URL source ;
- identifiant externe ;
- source.

---

## LL-5005 — Pipeline de normalisation

**Priorité : Haute**

**Dépendance :** LL-5004

Créer le pipeline :

```text
CollectedActivity
        ↓
Validation
        ↓
Activity
```

### Critères d'acceptation

- données invalides rejetées ;
- données valides converties ;
- aucune logique spécifique à un collecteur dans `ActivityService`.

---

## LL-5006 — Premier collecteur réel

**Priorité : Haute**

**Dépendance :** LL-5003, LL-5004, LL-5005

### Objectif

Implémenter **une seule source réelle**.

La source doit être choisie avant l'implémentation selon :

- disponibilité d'une API ou d'un flux public ;
- conditions d'utilisation ;
- stabilité ;
- qualité des données ;
- couverture géographique utile au MVP.

### Critères d'acceptation

- collecte fonctionnelle ;
- source identifiable ;
- données normalisées ;
- erreurs gérées ;
- aucun accès direct aux repositories `Activity` depuis le collecteur.

---

## LL-5007 — Détection simple des doublons

**Priorité : Haute**

**Dépendance :** LL-5005

### Objectif

Éviter de créer plusieurs fois la même activité lors d'importations successives.

### Stratégie MVP

Priorité à l'identifiant externe fourni par la source.

À défaut, utiliser une combinaison déterministe de champs :

- source ;
- titre ;
- date ;
- localisation.

### Important

Ne pas développer de système de déduplication "intelligent".

---

## LL-5008 — Persistance des imports

**Priorité : Haute**

**Dépendance :** LL-5007

### Objectif

Importer les données via les services métier existants.

### Critères

- aucune duplication ;
- activité existante mise à jour si elle appartient à la même source ;
- activité supprimée de la source gérée selon une stratégie documentée ;
- création manuelle d'une activité non affectée.

---

## LL-5009 — Journalisation des imports

**Priorité : Moyenne**

**Dépendance :** LL-5008

### Objectif

Permettre de comprendre le résultat d'un import.

Journaliser au minimum :

- source ;
- début ;
- fin ;
- nombre récupéré ;
- nombre créé ;
- nombre mis à jour ;
- nombre ignoré ;
- nombre en erreur.

Pas de tableau de bord d'administration dans ce sprint.

---

## LL-5010 — Tests du pipeline

**Priorité : Haute**

**Dépendance :** LL-5008

### Tester

- donnée valide ;
- donnée invalide ;
- doublon ;
- nouvelle activité ;
- mise à jour ;
- erreur du collecteur ;
- import vide.

### Critères

Les principaux cas du pipeline sont automatisés.

---

## LL-5011 — Vérifier l'affichage sur la carte

**Priorité : Haute**

**Dépendance :** LL-5006, LL-5008

### Objectif

Vérifier que les activités importées utilisent correctement les fonctionnalités du Sprint 4.

### Critères

- activité importée visible sur la carte ;
- recherche géographique fonctionnelle ;
- filtres fonctionnels ;
- activité importée consultable comme une activité normale.

Aucune fonctionnalité frontend spécifique aux collecteurs n'est requise.

---

## LL-5012 — Documentation

**Priorité : Haute**

**Dépendance :** tous les tickets précédents

Mettre à jour :

- `PROJECT_STATUS.md` ;
- `ROADMAP.md` ;
- documentation d'architecture ;
- documentation du collecteur ;
- README si nécessaire.

---

# Dépendances

```text
LL-5001
   ↓
LL-5002

LL-5003
   ↓
LL-5004
   ↓
LL-5005
   ↓
LL-5006
   ↓
LL-5007
   ↓
LL-5008
   ├── LL-5009
   └── LL-5010

LL-5006 + LL-5008
   ↓
LL-5011
   ↓
LL-5012
```

---

# Règles du sprint

L'IA qui réalise un ticket ne doit pas :

- créer plusieurs collecteurs ;
- choisir une architecture microservices ;
- créer une file Kafka/RabbitMQ ;
- ajouter Elasticsearch ;
- créer un système de scraping générique ;
- développer un scheduler complexe ;
- modifier le modèle `Activity` sans ticket ;
- contourner les services métier pour écrire en base ;
- implémenter de déduplication basée sur l'IA ;
- anticiper le système d'administration des sources.

---

# Definition of Done

Le Sprint 5 est terminé lorsque :

- une source réelle est intégrée ;
- ses données peuvent être collectées ;
- les données sont normalisées ;
- les données invalides sont rejetées ;
- les doublons évidents sont évités ;
- les activités importées sont persistées ;
- les imports sont journalisés ;
- les tests principaux passent ;
- les activités importées apparaissent sur la carte ;
- la documentation est à jour.

---

# Livrable du Sprint

> Une première alimentation réelle de LocalLife permettant de valider que l'architecture peut passer de données internes à des données externes sans complexifier prématurément le projet.

Le sprint suivant pourra ensuite se concentrer sur **la qualité des données, les food trucks et la validation/administration**, selon les résultats de cette première intégration.
