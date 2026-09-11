# Contrat de recherche géographique (LL-4001)

Définit le contrat utilisé par le backend pour la recherche d'activités
autour d'une position. Sert de base à l'implémentation PostGIS (LL-4002)
et à l'endpoint (LL-4003) — pas de code à ce stade, uniquement le contrat.

---

## Endpoint

```text
GET /api/v1/activities/nearby
```

Cohérent avec le style déjà en place (`GET /api/v1/activities`) : mêmes
conventions de nommage, même format de réponse, mêmes codes d'erreur.

## Paramètres (query string)

| Paramètre   | Type   | Obligatoire | Contraintes                                    | Description                              |
| ----------- | ------ | ------------ | ------------------------------------------------ | ----------------------------------------- |
| `latitude`  | double | oui          | entre -90 et 90                                  | Latitude du point de recherche.           |
| `longitude` | double | oui          | entre -180 et 180                                | Longitude du point de recherche.          |
| `radius`    | double | oui          | strictement positif, **en kilomètres**, max `50` | Rayon de recherche autour du point.       |
| `category`  | string | non          | une ou plusieurs valeurs séparées par des virgules (ex. `concert,marché`) | Filtre les résultats sur la/les catégorie(s) données (correspondance exacte, `OU` entre les valeurs). Absent → aucun filtrage. Catégorie ne correspondant à aucune activité → liste vide, **pas** une erreur 400 (voir décision ci-dessous). |
| `date`      | string | non          | format ISO-8601 `yyyy-MM-dd` | Filtre les résultats sur une date donnée : une activité est retenue quand cette date tombe dans sa période `[startDate, endDate]` (bornes incluses, comparaison au jour près). Absent → aucun filtrage. Voir décision LL-4005 ci-dessous. Borne de début de la période quand `dateTo` est également fourni — voir décision LL-11003 ci-dessous. |
| `dateTo`    | string | non          | format ISO-8601 `yyyy-MM-dd`, doit être ≥ `date` | Borne de fin optionnelle de la période, n'a de sens qu'accompagnée de `date`. Voir décision LL-11003 ci-dessous. |

⚠️ **Mise à jour LL-6004 (Sprint 6)** : le paramètre `status`, documenté
ci-dessous jusqu'à LL-4003/LL-4004 (« filtre les résultats sur ce statut,
absent → aucun filtrage »), **a été retiré** de cet endpoint.
Avec l'introduction de la modération (LL-6003 : statuts
`PENDING`/`PUBLISHED`/`REJECTED`), un endpoint public sans filtrage par
statut par défaut exposait les activités en attente ou rejetées à
n'importe quel visiteur — dette technique signalée dès LL-5012/LL-5008
(`DETTE_TECHNIQUE.md`, « activités ARCHIVED visibles par défaut »).
Cet endpoint étant public (aucune authentification), il ne retourne
désormais **que** les activités `PUBLISHED`, sans exception : le
paramètre `status` n'a donc plus de raison d'exister ici plutôt que
d'être conservé comme filtre vestigial ne pouvant jamais rien renvoyer
d'autre. Une future consultation par statut (ex. file de modération)
passera par un endpoint dédié, réservé aux administrateurs (LL-6005),
pas par celui-ci.

Exemple :

```text
GET /api/v1/activities/nearby?latitude=43.2965&longitude=5.3698&radius=5&category=concert,marché&date=2026-09-05
```

Exemple avec période (LL-11003) :

```text
GET /api/v1/activities/nearby?latitude=43.2965&longitude=5.3698&radius=5&date=2026-09-05&dateTo=2026-09-10
```

## Réponse

`200 OK` — tableau JSON d'objets `Activity`, **même format que
`GET /api/v1/activities`** (id, title, description, category, latitude,
longitude, startDate, endDate, status), trié par distance croissante au
point de recherche. Pas de champ distance ajouté à la réponse à ce stade
(hors périmètre — pourra être ajouté plus tard si un besoin apparaît).

## Erreurs

Même format standardisé que le reste de l'API (`ErrorResponse` — voir
`common.ErrorResponse` / `GlobalExceptionHandler`) :

| Cas                                              | Code | 
| ------------------------------------------------- | ---- |
| Paramètre manquant (`latitude`, `longitude` ou `radius`) | `400 Bad Request` |
| Paramètre hors contraintes (latitude/longitude hors plage, rayon ≤ 0 ou > 50 km) | `400 Bad Request` |
| Paramètre non numérique                            | `400 Bad Request` |
| `date` fournie mais pas au format ISO-8601 (`yyyy-MM-dd`)     | `400 Bad Request` |
| `dateTo` fournie mais pas au format ISO-8601 (`yyyy-MM-dd`)   | `400 Bad Request` |
| `dateTo` fournie sans `date`                                  | `400 Bad Request` |
| `dateTo` antérieure à `date`                                  | `400 Bad Request` |

## Implémentation attendue

* Calcul de la distance et filtrage **côté base de données**, via PostGIS
  (`ST_DWithin` sur une colonne géographique), pas en mémoire côté
  application — cohérent avec la stack déjà choisie
  (`docs/04_Project/... décisions validées : PostgreSQL + PostGIS`) et
  avec le critère d'acceptation de LL-4001 ("compatible avec
  PostgreSQL/PostGIS"). Note d'implémentation pour LL-4002 : `ST_DWithin`
  sur le type `geography` attend une distance en **mètres** — convertir
  `radius` (km, tel que reçu du client) en mètres avant l'appel SQL.
* Aucun nouveau moteur de recherche (Elasticsearch, etc.) — explicitement
  exclu du périmètre du Sprint 4 (voir `SPRINT_4.md`).
* La table `activity` n'a aujourd'hui que des colonnes `latitude`/
  `longitude` (`DOUBLE PRECISION`), pas de colonne géographique PostGIS :
  l'ajout de cette colonne (et de sa migration Flyway) fait partie du
  périmètre de LL-4002, pas de LL-4001.

## Décisions validées (par toi)

* `radius` exprimé en **kilomètres** (et non mètres) et plafonné à **50 km**
  — au-delà, `400 Bad Request`.
* Filtrage par statut (paramètre `status`) proposé initialement ici,
  **retiré en LL-6004** — voir la mise à jour en tête de ce document.
  Cet endpoint ne retourne plus que les activités `PUBLISHED`.

## Décision LL-4004 à valider : `category` (chaîne) et non `categoryId`

Le ticket LL-4004 (`SPRINT_4.md`) donne l'exemple `&categoryId=1`. Ce
paramètre n'a pas été implémenté tel quel, pour une raison de modèle de
données : la colonne `activity.category` est une **chaîne libre** saisie
par le contributeur (voir `ActivityService.createActivity`, LL-2012), sans
aucune relation avec la table `category` (qui n'a ni clé étrangère depuis
`activity`, ni données). Utiliser `categoryId` aurait nécessité soit
d'ajouter une vraie relation `Activity` → `Category` (modification du
modèle métier explicitement interdite par les règles du Sprint 4 sans
ticket dédié), soit d'accepter un paramètre qui ne correspondrait à aucune
donnée réelle.

Le paramètre s'appelle donc `category` (chaîne, voir tableau ci-dessus),
comparé directement à la colonne `activity.category`. Une catégorie qui ne
correspond à aucune activité renvoie une liste vide (`200 OK`), pas une
erreur — contrairement à `status`, il n'existe pas de liste fermée de
catégories valides (champ libre à la création), donc rien à valider
au sens strict.

## Décision LL-4005 : gestion de `end_date` absente

Une activité est considérée comme active pour une date donnée lorsque sa
période `[startDate, endDate]` couvre cette date (critère d'acceptation du
ticket). Cependant, `endDate` peut être absente en base : les activités
créées via le formulaire de contribution (voir `ActivityService.createActivity`,
LL-2012/LL-3012) n'ont pas de date de fin renseignée par le formulaire.
Décision : dans ce cas, l'activité est traitée comme ne durant qu'une seule
journée, celle de `startDate` (équivalent à `COALESCE(endDate, startDate)`
côté SQL). Alternative écartée : exclure ces activités de tout filtre par
date, ce qui les rendrait invisibles dès qu'un utilisateur filtre par date —
contraire à l'objectif du Sprint 4 (carte réellement exploitable).

## Décision LL-11003 : filtre par période via `dateTo`

Signalé par toi (retour utilisateur) : le filtre « Période » du frontend
(ce weekend/cette semaine/ce mois-ci/prochain mois, LL-11002) n'envoyait
que la date de **début** de la période. Avec la sémantique LL-4005 (une
activité est retenue quand `date` tombe dans `[startDate, endDate]`), une
activité qui commence un autre jour de la période mais reste visible
pendant celle-ci — ou qui a commencé avant la période mais se termine
dedans — n'était pas retournée : seule la correspondance exacte avec le
premier jour de la période comptait.

Décision : ajout du paramètre optionnel `dateTo`, qui n'a de sens
qu'accompagné de `date` (borne de début). Quand les deux sont fournis, une
activité est retenue dès que sa période `[startDate, endDate]` **chevauche**
`[date, dateTo]` (bornes incluses des deux côtés, comparaison au jour près,
même règle `COALESCE(endDate, startDate)` que LL-4005 pour les activités
sans date de fin) — et non plus seulement lorsque sa période couvre
exactement `date`.

`dateTo` fourni sans `date` est rejeté (`400 Bad Request`) plutôt
qu'ignoré silencieusement : une période « allant de quand à `dateTo` ? »
serait ambiguë, et un rejet explicite évite qu'un appelant ne se rende pas
compte que son paramètre est sans effet. De même, `dateTo` antérieure à
`date` (période inversée) ne correspond à aucun cas d'usage identifié et
signale plus probablement une erreur côté appelant qu'une intention
réelle — rejeté plutôt qu'interprété silencieusement (ex. en inversant les
bornes).

Absence de `dateTo` → comportement strictement inchangé (correspondance
sur la seule date `date`, LL-4005), pour rester rétrocompatible avec les
appelants existants qui ne fourniraient que `date`.

## Points laissés ouverts pour LL-4002/LL-4003 (à valider à ce moment-là)

* Nom exact de la colonne géographique ajoutée à `activity` et stratégie
  de migration (colonne dérivée de `latitude`/`longitude` existants vs.
  double stockage).
