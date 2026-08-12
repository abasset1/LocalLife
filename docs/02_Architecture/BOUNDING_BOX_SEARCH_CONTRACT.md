# Contrat de recherche par zone cartographique (LL-4006)

Définit le contrat utilisé par le backend pour rechercher des activités à
l'intérieur d'une zone rectangulaire (« bounding box »), typiquement la
zone actuellement visible sur la carte (`SPRINT_4.md` : « voir uniquement
les activités correspondant à la zone affichée »). Sert de base à
l'implémentation PostGIS et à l'endpoint (LL-4007 — « Bounding Box »),
comme LL-4001 l'a fait pour la recherche par rayon — pas de code à ce
stade, uniquement le contrat.

Ce contrat complète `GEO_SEARCH_CONTRACT.md` (recherche par rayon,
`GET /api/v1/activities/nearby`) : les deux coexistent comme deux modes de
recherche géographique distincts, chacun avec son propre endpoint. Aucun
des deux ne remplace l'autre — voir décision ci-dessous.

---

## Endpoint

```text
GET /api/v1/activities/within-bounds
```

⚠️ Décision à valider : nom retenu par analogie avec `/nearby`, mais reste
un choix ouvert. Alternatives envisagées et écartées : `/bounds` (moins
explicite), réutiliser `/nearby` avec des paramètres optionnels
alternatifs à `latitude`/`longitude`/`radius` (rejeté : mélangerait deux
contrats de validation différents sur un seul endpoint, rendant la
documentation OpenAPI et la validation plus confuses — un endpoint dédié
reste plus simple à documenter et à faire évoluer indépendamment,
notamment pour LL-4012 qui l'appellera à chaque déplacement de carte).

## Paramètres (query string)

Les quatre coins de la zone sont désignés par les coins sud-ouest (`sw`) et
nord-est (`ne`) du rectangle, convention standard des bibliothèques de
cartographie (dont Leaflet, déjà utilisé côté frontend depuis LL-1008 —
`map.getBounds().getSouthWest()`/`getNorthEast()`), pour limiter la
traduction nécessaire côté frontend en LL-4012.

| Paramètre     | Type   | Obligatoire | Contraintes                     | Description                                    |
| ------------- | ------ | ------------ | -------------------------------- | ------------------------------------------------ |
| `swLatitude`  | double | oui          | entre -90 et 90, `< neLatitude`  | Latitude du coin sud-ouest de la zone.            |
| `swLongitude` | double | oui          | entre -180 et 180                | Longitude du coin sud-ouest de la zone.           |
| `neLatitude`  | double | oui          | entre -90 et 90, `> swLatitude`  | Latitude du coin nord-est de la zone.             |
| `neLongitude` | double | oui          | entre -180 et 180, `> swLongitude` | Longitude du coin nord-est de la zone.          |
| `status`      | string | non          | doit correspondre à une valeur existante (ex. `PUBLISHED`, `PENDING`) | Filtre les résultats sur ce statut. Absent → aucun filtrage. Identique au contrat `/nearby`. |
| `category`    | string | non          | une ou plusieurs valeurs séparées par des virgules | Filtre sur la/les catégorie(s). Identique au contrat `/nearby` (LL-4004). |
| `date`        | string | non          | format ISO-8601 `yyyy-MM-dd`     | Filtre sur une date donnée. Identique au contrat `/nearby` (LL-4005). |

Exemple :

```text
GET /api/v1/activities/within-bounds?swLatitude=43.28&swLongitude=5.35&neLatitude=43.31&neLongitude=5.40&status=PUBLISHED
```

⚠️ Décision à valider : `swLongitude < neLongitude` est une contrainte
stricte du contrat — le passage de l'antiméridien (zone traversant
±180°) n'est **pas supporté** à ce stade (hors périmètre du MVP, non
mentionné dans les critères du Sprint 4, et sans cas d'usage réel pour une
application centrée sur Marseille). Une zone qui le traverserait renverrait
`400 Bad Request`.

## Réponse

`200 OK` — tableau JSON d'objets `Activity`, même format que
`GET /api/v1/activities` et `GET /api/v1/activities/nearby` (id, title,
description, category, latitude, longitude, startDate, endDate, status).

⚠️ Décision à valider : contrairement à `/nearby`, il n'y a pas de point
de référence unique pour calculer une distance, donc pas de tri par
distance possible. Tri retenu : par `id` croissant (ordre stable et simple,
suffisant pour l'affichage sur une carte où l'ordre des marqueurs n'a pas
d'importance fonctionnelle).

## Erreurs

Même format standardisé que le reste de l'API (`ErrorResponse`) :

| Cas                                                                | Code |
| -------------------------------------------------------------------| ---- |
| Paramètre manquant (`swLatitude`, `swLongitude`, `neLatitude`, `neLongitude`) | `400 Bad Request` |
| Paramètre non numérique                                             | `400 Bad Request` |
| Latitude/longitude hors plage (-90/90, -180/180)                    | `400 Bad Request` |
| `swLatitude >= neLatitude` ou `swLongitude >= neLongitude`          | `400 Bad Request` |
| `status` fourni mais ne correspondant à aucune valeur connue        | `400 Bad Request` |
| `date` fournie mais pas au format ISO-8601                          | `400 Bad Request` |

## Implémentation attendue (LL-4007)

* Filtrage **côté base de données**, via PostGIS (`ST_MakeEnvelope` +
  `ST_Within` ou l'opérateur `&&`), pas en mémoire côté application —
  même choix que LL-4002 pour la recherche par rayon, et compatible avec
  la stack déjà en place (colonne `location GEOGRAPHY`, migration V7).
* Réutilisation des mêmes filtres optionnels `status`/`category`/`date`
  que `findNearby` (même logique de validation et de normalisation),
  pour rester cohérent entre les deux modes de recherche et permettre la
  combinaison de filtres exigée par LL-4014 (tests d'intégration).
* Aucun nouveau moteur de recherche — explicitement exclu du périmètre du
  Sprint 4 (voir `SPRINT_4.md`).

## Relation avec LL-4012 (chargement dynamique de la carte)

Ce contrat est conçu pour être appelé à chaque déplacement/zoom
significatif de la carte (LL-4012), avec les coordonnées obtenues via
`map.getBounds()` (Leaflet). Cette utilisation prévue justifie le choix
d'un endpoint dédié plutôt qu'une extension de `/nearby` : les deux
endpoints ont des cas d'usage différents (recherche autour d'un point
précis vs. affichage de tout ce qui est visible à l'écran) et évolueront
probablement à des rythmes différents.
