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

| Paramètre   | Type   | Obligatoire | Contraintes                          | Description                              |
| ----------- | ------ | ------------ | ------------------------------------- | ----------------------------------------- |
| `latitude`  | double | oui          | entre -90 et 90                       | Latitude du point de recherche.           |
| `longitude` | double | oui          | entre -180 et 180                     | Longitude du point de recherche.          |
| `radius`    | double | oui          | strictement positif, en **mètres**    | Rayon de recherche autour du point.       |

Aucun paramètre optionnel à ce stade (le filtre par catégorie et le filtre
par date sont hors périmètre de LL-4001, traités séparément en LL-4004 et
LL-4005 : ils s'ajouteront en query params optionnels sans modifier ce
contrat).

Exemple :

```text
GET /api/v1/activities/nearby?latitude=43.2965&longitude=5.3698&radius=5000
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
| Paramètre hors contraintes (latitude/longitude hors plage, rayon ≤ 0) | `400 Bad Request` |
| Paramètre non numérique                            | `400 Bad Request` |

## Implémentation attendue

* Calcul de la distance et filtrage **côté base de données**, via PostGIS
  (`ST_DWithin` sur une colonne géographique), pas en mémoire côté
  application — cohérent avec la stack déjà choisie
  (`docs/04_Project/... décisions validées : PostgreSQL + PostGIS`) et
  avec le critère d'acceptation de LL-4001 ("compatible avec
  PostgreSQL/PostGIS").
* Aucun nouveau moteur de recherche (Elasticsearch, etc.) — explicitement
  exclu du périmètre du Sprint 4 (voir `SPRINT_4.md`).
* La table `activity` n'a aujourd'hui que des colonnes `latitude`/
  `longitude` (`DOUBLE PRECISION`), pas de colonne géographique PostGIS :
  l'ajout de cette colonne (et de sa migration Flyway) fait partie du
  périmètre de LL-4002, pas de LL-4001.

## Points laissés ouverts pour LL-4002/LL-4003 (à valider à ce moment-là)

* Nom exact de la colonne géographique ajoutée à `activity` et stratégie
  de migration (colonne dérivée de `latitude`/`longitude` existants vs.
  double stockage).
* Faut-il restreindre `radius` à une valeur maximale (éviter une requête
  couvrant toute la planète) ? Pas de valeur imposée par LL-4001.
* Faut-il exclure les activités dont le statut n'est pas `PUBLISHED` du
  résultat (comme c'est déjà implicitement le cas pour la consultation
  publique) ? Le contrat ci-dessus ne le précise pas explicitement — à
  trancher en LL-4003, par cohérence avec `GET /api/v1/activities`
  (qui, lui, ne filtre actuellement pas par statut).
