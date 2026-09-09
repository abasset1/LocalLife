# Sprint 11 — Modèle événementiel riche et programmation récurrente

**Statut :** ⏳ À faire

---

# 1. Objectif

Faire évoluer le modèle métier de LocalLife afin qu'il puisse représenter
durablement :

* les événements ponctuels ;
* les événements possédant plusieurs horaires ;
* les événements récurrents ;
* les activités présentes régulièrement à un endroit ;
* les food trucks présents à différents endroits selon les jours ;
* les changements exceptionnels de lieu ou d'horaire ;
* les lieux réutilisés par plusieurs activités ;
* les fiches d'activités riches ;
* plusieurs photos par activité ;
* les descriptions longues ;
* les liens et contacts ;
* les informations d'accessibilité ;
* les tags et métadonnées provenant des sources ;
* les données riches provenant d'OpenAgenda ;
* les recherches par ville et par date ;
* le futur affichage détaillé Premium.

Le sprint doit construire **le socle métier** nécessaire à ces fonctionnalités.

Le paiement et l'abonnement Premium ne font pas partie du sprint.

---

# 2. Principe architectural

Le modèle doit distinguer quatre concepts :

```text
Activity
    = ce qu'est l'activité

Schedule
    = comment l'activité est programmée

Occurrence
    = une réalisation concrète de l'activité

Location
    = où elle se déroule
```

Relation principale :

```text
Activity
    │
    └── Schedule
          │
          ├── règle de récurrence
          ├── horaires
          └── Location
                │
                └── Occurrences
```

Une activité peut donc posséder plusieurs programmations.

Exemple :

```text
Food Truck X
│
├── Schedule
│   ├── Mardi
│   ├── 11:30 → 14:00
│   └── Avignon
│
├── Schedule
│   ├── Jeudi
│   ├── 18:00 → 22:00
│   └── Marseille
│
└── Schedule
    ├── Samedi
    ├── 12:00 → 15:00
    └── Orange
```

---

# 3. Décision importante : `city` n'appartient pas à `Activity`

La ville ne doit pas être considérée comme une propriété intrinsèque de
l'activité.

Une même activité peut changer de lieu.

La localisation appartient donc au niveau de la programmation et/ou de
l'occurrence.

```text
Activity
   │
   ├── Schedule → Avignon
   │
   └── Schedule → Marseille
```

Cela permet également de répondre correctement à :

> Quels événements ont lieu à Avignon demain ?

La recherche doit porter sur les programmations/occurrences pertinentes,
et non simplement sur `Activity.city`.

---

# 4. `Location`

## LL-11001 — Créer le modèle Location

**Priorité : Haute**

### Objectif

Créer une entité métier indépendante représentant un lieu.

### Données minimales

```text
id
name
address
postalCode
city
department
region
countryCode
insee
latitude
longitude
timezone
website
email
phone
```

Les champs effectivement persistés doivent rester cohérents avec les
données réellement fournies par les sources.

### Règles

* un lieu peut être utilisé par plusieurs activités ;
* les coordonnées restent compatibles avec PostGIS ;
* la ville est stockée séparément ;
* le code postal est stocké séparément ;
* l'adresse complète reste disponible pour affichage ;
* aucun lieu ne doit être créé artificiellement à partir de données
  insuffisantes.

### Critères d'acceptation

* entité métier créée ;
* migration Flyway ;
* repository ;
* service ;
* tests ;
* compatibilité avec les données existantes ;
* documentation.

---

# 5. `Schedule`

## LL-11002 — Créer le modèle Schedule

**Priorité : Haute**

**Dépendance :** LL-11001

### Objectif

Représenter la programmation d'une activité.

Un Schedule doit pouvoir représenter :

* un événement ponctuel ;
* une programmation répétée ;
* un horaire de début ;
* un horaire de fin ;
* une période de validité ;
* un fuseau horaire ;
* un lieu.

### Exemple

```text
Food Truck X

Schedule
------------------------
Jour : mardi
Début : 11:30
Fin : 14:00
Lieu : Avignon
```

### Critères d'acceptation

* une activité peut avoir plusieurs schedules ;
* chaque schedule peut avoir son propre lieu ;
* les horaires ne sont pas perdus ;
* le modèle est indépendant d'OpenAgenda ;
* les données sont persistées ;
* tests complets.

---

# 6. Récurrence

## LL-11003 — Implémenter les règles de récurrence

**Priorité : Haute**

**Dépendance :** LL-11002

### Objectif

Permettre de déclarer une programmation récurrente sans créer un moteur
propriétaire.

La représentation doit être basée sur un standard de calendrier de type
iCalendar/RRULE.

Exemple :

```text
FREQ=WEEKLY;BYDAY=TU
```

### Cas minimum

* tous les jours ;
* toutes les semaines ;
* jours déterminés de la semaine ;
* date de début ;
* date de fin ;
* absence de date de fin lorsque la source le permet.

### Critères d'acceptation

* règle persistée ;
* règle reconstruisible ;
* calcul des occurrences possible ;
* fuseau horaire pris en compte ;
* tests de récurrence ;
* aucun format propriétaire inventé.

---

# 7. Occurrence

## LL-11004 — Créer le modèle Occurrence

**Priorité : Haute**

**Dépendance :** LL-11003

### Objectif

Représenter une occurrence concrète.

### Modèle minimal

```text
id
scheduleId
startAt
endAt
locationId
status
```

### Exemple

```text
Schedule
    ↓
Tous les mardis 11:30 → 14:00
    ↓
Occurrence
    08/09/2026 11:30 → 14:00
Occurrence
    15/09/2026 11:30 → 14:00
Occurrence
    22/09/2026 11:30 → 14:00
```

### Critères d'acceptation

* occurrence datée ;
* heure de début ;
* heure de fin ;
* lieu effectif ;
* statut ;
* relation avec Schedule ;
* tests.

---

# 8. Exceptions de récurrence

## LL-11005 — Gérer les exceptions et modifications d'occurrence

**Priorité : Haute**

**Dépendance :** LL-11004

### Objectif

Permettre qu'une programmation récurrente soit modifiée pour une date
particulière.

### Cas obligatoires

```text
Tous les mardis
11:30 → 14:00
Avignon
```

Mais :

```text
15 septembre → annulé
22 septembre → Marseille
29 septembre → normal
```

### Le modèle doit permettre

* annulation ;
* modification d'horaire ;
* déplacement ;
* changement de lieu.

### Critères d'acceptation

* une exception est identifiable ;
* elle ne détruit pas la règle générale ;
* l'occurrence exceptionnelle est représentée correctement ;
* les recherches utilisent l'état effectif de l'occurrence ;
* tests.

---

# 9. Fiche événementielle riche

## LL-11006 — Enrichir Activity

**Priorité : Haute**

**Dépendance :** LL-11001

### Objectif

Permettre de conserver suffisamment d'informations pour construire une
fiche complète.

### Données à prévoir

```text
description
longDescription
conditions
ageMin
ageMax
```

La conception doit rester indépendante de la source.

### Critères d'acceptation

* description courte conservée ;
* description longue conservée ;
* conditions conservées ;
* informations d'âge conservées lorsqu'elles existent ;
* aucune dépendance au modèle OpenAgenda dans le domaine ;
* migration et tests.

---

# 10. Médias

## LL-11007 — Créer le modèle Media

**Priorité : Haute**

**Dépendance :** LL-11006

### Objectif

Permettre plusieurs images par activité et, à terme, par lieu.

### Modèle

```text
Media
----------------
id
url
type
width
height
credit
altText
```

Relation :

```text
Activity
   │
   └── ActivityMedia
          ├── mediaId
          └── position
```

### Critères d'acceptation

* plusieurs images possibles ;
* ordre conservé ;
* crédit conservé ;
* URL source conservée ;
* aucune dépendance à un stockage particulier ;
* tests.

---

# 11. Liens et contacts

## LL-11008 — Créer les liens et contacts

**Priorité : Moyenne**

**Dépendance :** LL-11006

### Objectif

Conserver les moyens permettant à l'utilisateur d'obtenir davantage
d'informations.

### Exemples

```text
Site officiel
Billetterie
Réservation
Programme
Réseau social
Téléphone
Email
```

### Critères d'acceptation

* plusieurs liens possibles ;
* type ;
* libellé ;
* URL ;
* contacts lorsque disponibles ;
* validation adaptée au type ;
* tests.

---

# 12. Accessibilité, tags et métadonnées

## LL-11009 — Conserver les informations complémentaires

**Priorité : Moyenne**

**Dépendance :** LL-11006

### Objectif

Éviter de perdre les informations disponibles dans les sources externes
mais qui ne justifient pas toutes une nouvelle colonne métier.

### Accessibilité

Créer un modèle structuré pour les principales informations
d'accessibilité.

### Tags

Permettre plusieurs tags par activité.

### Metadata

Utiliser JSONB pour les données spécifiques ou évolutives ne constituant
pas encore des données métier de premier niveau.

### Critères d'acceptation

* informations importantes structurées ;
* tags conservés ;
* données spécifiques conservables ;
* aucune table créée inutilement pour chaque champ d'une source ;
* tests.

---

# 13. Provenance et données externes

## LL-11010 — Faire évoluer ExternalActivity

**Priorité : Haute**

### Objectif

Permettre de conserver la richesse des données provenant des collectors
sans la perdre lors de la normalisation.

### Modèle cible

```text
ExternalActivity
----------------------------
id
sourceId
externalId
externalUrl
sourceUpdatedAt
lastSeenAt
payloadHash
rawPayload
```

`rawPayload` est stocké en JSONB.

### Règle

Le JSON source sert de **donnée de provenance et de récupération**, pas de
modèle métier.

Le domaine LocalLife doit rester indépendant d'OpenAgenda.

### Critères d'acceptation

* identifiant externe conservé ;
* URL source conservée ;
* date de modification conservée lorsqu'elle existe ;
* payload brut conservable ;
* détection de modification possible ;
* aucun accès direct au JSON brut depuis le frontend.

---

# 14. Évolution du pipeline Collector

## LL-11011 — Adapter le pipeline d'import

**Priorité : Haute**

**Dépendance :** LL-11006 à LL-11010

### Flux cible

```text
Source externe
      ↓
Collector
      ↓
ExternalActivity
      ↓
Normalisation
      ↓
Validation
      ↓
Persistence
      ↓
Activity
 ├── Location
 ├── Schedule
 ├── Occurrence
 ├── Media
 ├── Links
 ├── Accessibility
 ├── Tags
 └── Metadata
```

### OpenAgenda

Le collector OpenAgenda doit pouvoir exploiter les données riches
disponibles dans la source.

Au minimum :

* titre ;
* descriptions ;
* description longue ;
* image(s) ;
* horaires ;
* lieu ;
* adresse ;
* ville ;
* code postal ;
* coordonnées ;
* liens ;
* conditions ;
* accessibilité ;
* tags ;
* informations disponibles relatives à l'inscription.

### Critères d'acceptation

* aucune perte volontaire des données déjà disponibles ;
* plusieurs horaires supportés ;
* récurrence supportée lorsqu'elle est fournie ;
* localisation structurée ;
* médias conservés ;
* liens conservés ;
* données complémentaires conservées ;
* pipeline conforme au contrat Collector existant.

---

# 15. Recherche par ville et par date

## LL-11012 — Recherche sur les occurrences

**Priorité : Haute**

**Dépendance :** LL-11004, LL-11005

### Objectif

Faire évoluer la recherche pour prendre en compte le nouveau modèle.

Exemple :

```text
GET /api/v1/activities?city=Avignon&date=2026-09-15
```

doit retourner les activités effectivement présentes à Avignon à cette
date.

### Le système doit gérer

* activité ponctuelle ;
* activité récurrente ;
* plusieurs schedules ;
* plusieurs villes ;
* exceptions ;
* changement exceptionnel de lieu ;
* horaires.

### Critères d'acceptation

* filtre par ville ;
* filtre par date ;
* ville + date ;
* recherche géographique PostGIS conservée ;
* récurrences prises en compte ;
* exceptions prises en compte ;
* tests d'intégration.

---

# 16. API résumé / détail

## LL-11013 — Faire évoluer les DTOs et l'API

**Priorité : Haute**

**Dépendance :** LL-11006 à LL-11012

### Objectif

Séparer les données nécessaires à la carte/liste de celles nécessaires à
la fiche détaillée.

### Résumé

```text
GET /api/v1/activities
```

doit rester léger.

Il doit notamment pouvoir fournir :

```text
id
title
category
date pertinente
ville
adresse
latitude
longitude
image principale
```

### Détail

```text
GET /api/v1/activities/{id}
```

doit pouvoir fournir :

```text
Activity
 ├── description
 ├── longDescription
 ├── schedules
 ├── occurrences pertinentes
 ├── locations
 ├── media
 ├── links
 ├── accessibility
 ├── tags
 └── informations complémentaires
```

### Critères d'acceptation

* aucun endpoint existant cassé ;
* DTO résumé ;
* DTO détail ;
* OpenAPI à jour ;
* tests API ;
* aucune donnée sensible exposée.

---

# 17. Préparation Premium

## LL-11014 — Préparer la fiche Premium

**Priorité : Moyenne**

**Dépendance :** LL-11013

### Objectif

Préparer l'architecture pour qu'une fiche complète puisse être réservée
aux utilisateurs Premium.

### Règle fondamentale

Le modèle de données ne doit pas connaître le forfait.

Ne jamais créer :

```text
premiumDescription
premiumImage
PremiumActivity
```

Le modèle conserve les données disponibles.

La couche API pourra ensuite décider :

```text
Utilisateur gratuit
        ↓
Résumé

Utilisateur Premium
        ↓
Fiche détaillée
```

### Hors périmètre

* paiement ;
* abonnement ;
* facturation ;
* gestion commerciale Premium.

### Critères d'acceptation

* aucune donnée Premium spécifique dans le domaine ;
* fiche détaillée disponible techniquement ;
* séparation résumé/détail documentée ;
* architecture prête pour une future autorisation Premium.

---

# 18. Frontend

## LL-11015 — Exploiter le nouveau modèle dans le frontend

**Priorité : Haute**

**Dépendance :** LL-11013

### Objectif

Adapter l'interface aux nouvelles données.

### Affichage minimal

Une activité peut afficher :

```text
Titre
Date
Ville
Adresse
Horaires
Image
```

Le détail doit pouvoir afficher :

```text
Galerie
Description longue
Horaires
Lieu
Liens
Contacts
Accessibilité
Informations complémentaires
```

### Récurrence

Afficher une récurrence de manière compréhensible :

```text
Tous les mardis
11h30 → 14h00
Avignon
```

et non afficher directement :

```text
FREQ=WEEKLY;BYDAY=TU
```

### Critères d'acceptation

* activité ponctuelle correctement affichée ;
* activité récurrente correctement affichée ;
* food truck multi-lieux correctement affiché ;
* adresse visible ;
* ville visible ;
* images visibles ;
* description longue visible ;
* liens utilisables ;
* absence de données correctement gérée.

---

# 19. Tests métier de référence

## LL-11016 — Tester les cas réels de programmation

**Priorité : Haute**

**Dépendance :** LL-11015

### Cas 1 — Événement ponctuel

```text
Concert
12 septembre
20h00 → 23h00
Avignon
```

### Cas 2 — Événement récurrent

```text
Marché
Tous les mardis
08h00 → 13h00
Avignon
```

### Cas 3 — Food truck multi-lieux

```text
Lundi    → Marseille
Mardi    → Avignon
Mercredi → Orange
```

### Cas 4 — Exception

```text
Tous les mardis → Avignon

15/09 → annulé
22/09 → Marseille
29/09 → normal
```

### Cas 5 — Fiche riche

```text
Titre
Description
Description longue
Photos
Lieu
Horaires
Liens
Accessibilité
Tags
```

### Critères d'acceptation

Tous ces scénarios doivent être représentables et testés.

---

# 20. Documentation

## LL-11017 — Documenter le nouveau modèle

**Priorité : Haute**

**Dépendance :** tous les tickets

### Documentation à mettre à jour

* `ARCHITECTURE.md`
* `COLLECTOR_CONTRACT.md`
* `FOOD_TRUCK_CONTRACT.md`
* contrat API ;
* modèle de données ;
* recherche géographique ;
* documentation OpenAPI ;
* `PROJECT_STATUS.md` ;
* backlog ;
* roadmap ;
* `CHANGELOG.md`.

### La documentation doit notamment expliquer

```text
Activity
    ↓
Schedule
    ↓
Recurrence
    ↓
Occurrence
    ↓
Location
```

ainsi que :

* les exceptions ;
* les sources externes ;
* la conservation du payload brut ;
* la distinction résumé/détail ;
* la future distinction Basic/Premium.

---

# 21. Dépendances

```text
LL-11001 Location
       │
       ▼
LL-11002 Schedule
       │
       ▼
LL-11003 Recurrence
       │
       ▼
LL-11004 Occurrence
       │
       ▼
LL-11005 Exceptions
       │
       ├────────────────────────┐
       ▼                        ▼
LL-11006 Activity riche     LL-11012 Recherche
       │                        │
 ┌─────┼──────┐                 │
 ▼     ▼      ▼                 │
Media Links Metadata            │
 │      │      │                │
 └──────┴──────┴───────┐        │
                       ▼        │
                  LL-11010     │
                  External     │
                       │       │
                       ▼       │
                  LL-11011     │
                       │       │
                       └──┬────┘
                          ▼
                     LL-11013 API
                          │
                    ┌─────┴─────┐
                    ▼           ▼
                LL-11014    LL-11015
                Premium     Frontend
                    │           │
                    └─────┬─────┘
                          ▼
                     LL-11016
                          │
                          ▼
                     LL-11017
```

---

# 22. Hors périmètre

Ce sprint ne doit pas introduire :

* paiement ;
* abonnement ;
* facturation ;
* marketplace ;
* moteur de recommandation ;
* Elasticsearch ;
* microservices ;
* Kafka/RabbitMQ ;
* nouvelle infrastructure ;
* application mobile ;
* stockage CDN obligatoire ;
* moteur propriétaire de calendrier ;
* système complexe de tournée Food Truck ;
* gestion de commande Food Truck.

Le projet reste un **monolithe modulaire Spring Boot + PostgreSQL/PostGIS**.

---

# 23. Definition of Done

Le Sprint 11 est terminé lorsque :

* `Location` est une entité indépendante ;
* une activité peut avoir plusieurs `Schedule` ;
* un `Schedule` possède une programmation ;
* une programmation peut être récurrente ;
* la récurrence repose sur un format standard ;
* les occurrences peuvent être calculées ;
* les exceptions sont représentables ;
* une occurrence peut avoir un lieu différent ;
* un food truck peut avoir plusieurs lieux selon les jours ;
* une activité possède une fiche riche ;
* plusieurs médias peuvent être associés ;
* les descriptions longues sont conservées ;
* les liens et contacts sont conservés ;
* l'accessibilité est conservée ;
* les tags sont conservés ;
* les métadonnées spécifiques sont conservables ;
* la provenance des données est conservée ;
* le payload externe peut être retrouvé ;
* OpenAgenda peut être exploité sans perte des informations importantes ;
* la recherche par ville et date fonctionne ;
* la recherche PostGIS continue de fonctionner ;
* l'API distingue résumé et détail ;
* le frontend exploite le nouveau modèle ;
* le modèle est prêt à supporter une fiche Premium ;
* aucune logique de paiement n'est introduite ;
* les tests passent ;
* la documentation est à jour.

---

# 24. Résultat attendu

À la fin du Sprint 11, LocalLife doit être capable de représenter ce type de
donnée :

```text
                    ACTIVITY
                        │
              "Food Truck X"
                        │
             ┌──────────┴──────────┐
             │                     │
          SCHEDULE              SCHEDULE
             │                     │
          MARDI                  JEUDI
             │                     │
        11:30-14:00           18:00-22:00
             │                     │
          AVIGNON               MARSEILLE
             │                     │
             ▼                     ▼
        OCCURRENCES            OCCURRENCES
             │                     │
        ┌────┼────┐           ┌────┼────┐
        ▼    ▼    ▼           ▼    ▼    ▼
       08   15   22           10   17   24
       sep  sep  sep          sep  sep  sep
                  │
                  └── exception
                       ↓
                    MARSEILLE
```

tout en permettant :

```text
Activity
 ├── contenu riche
 ├── description longue
 ├── galerie photos
 ├── horaires
 ├── récurrence
 ├── lieux
 ├── liens
 ├── contacts
 ├── accessibilité
 ├── tags
 └── provenance
```

Ce modèle devient ensuite le **socle commun du web, de la future application
mobile, de la carte, de la recherche par ville, des collectors et de la
future fiche Premium**.
