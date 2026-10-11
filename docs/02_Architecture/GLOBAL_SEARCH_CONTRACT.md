# Contrat de la recherche textuelle globale (#91)

Endpoint public de recherche **globale** dans les activités : pas de rayon, pas de zone visible. La
pertinence textuelle classe d'abord ; la proximité ne départage qu'à pertinence égale et n'écarte jamais
un résultat. Parent : #89 (option C). Les endpoints existants (`GET /api/v1/activities`, `/nearby`,
`/within-bounds`) ne changent pas.

Gratuit : PostgreSQL/PostGIS existant uniquement, aucun moteur de recherche ni service externe.

## Requête

`GET /api/v1/activities/search`

| Paramètre | Obligatoire | Règle |
|---|---|---|
| `q` | oui | Texte cherché, 2 à 120 caractères après suppression des espaces autour, au moins une lettre ou un chiffre. |
| `latitude`, `longitude` | non, mais ensemble | Position de référence pour le tri par proximité. Latitude −90 à 90, longitude −180 à 180 (mêmes bornes que `/nearby`). Jamais enregistrée. |
| `category` | non | Valeurs séparées par des virgules, même sémantique que `/nearby` : chaque valeur est comparée à la catégorie libre `activity.category` **et** aux codes du référentiel (`CULTURE`, `SPORT`…). `OU` entre les valeurs. Valeur inconnue : aucun résultat, pas d'erreur. |
| `date` | non | `yyyy-MM-dd`. Seule : la date doit tomber dans la période de l'occurrence (ou de l'activité). Début de période si `dateTo` est fourni. |
| `dateTo` | non | `yyyy-MM-dd`, exige `date`, `dateTo ≥ date`. Une occurrence est retenue si sa période chevauche `[date, dateTo]`. |
| `limit` | non | 1 à 100, 20 par défaut. |
| `offset` | non | 0 ou plus, 0 par défaut. |

**Sans `date`** (décision à valider) : seules les activités **non terminées** sont retenues, c'est-à-dire une
occurrence (ou la période de l'activité) qui se termine aujourd'hui ou plus tard, **sans borne
supérieure**. La fenêtre par défaut de 7 jours de `/nearby` n'est pas reprise : elle masquerait les
événements lointains d'une recherche globale. Les dates sont comparées au jour, dans le fuseau du lieu
(défaut `Europe/Paris`), comme `/nearby`.

## Réponse `200`

```json
{
  "items": [
    {
      "id": 42,
      "title": "Fête de la musique",
      "description": "…",
      "category": "concert",
      "categoryCodes": ["CULTURE"],
      "city": "Lyon",
      "address": "Place Bellecour",
      "latitude": 45.7578,
      "longitude": 4.8320,
      "startDate": "2026-06-21T18:00:00",
      "endDate": "2026-06-21T23:00:00",
      "relevance": "TITLE_EXACT",
      "distanceMeters": 1200
    }
  ],
  "limit": 20,
  "offset": 0,
  "total": 1
}
```

- `id` : `Long` ; dates : `LocalDateTime` sans fuseau, heure locale du lieu, comme les DTO existants.
- **Lieu effectif retenu** : pour une activité avec horaires, `city`, `address`, `latitude`, `longitude`,
  `startDate`, `endDate` et `distanceMeters` viennent tous de **la même occurrence** :
  - la plus proche de la position de référence parmi celles qui respectent les filtres ;
  - à défaut de position de référence, la plus tôt.
  Les colonnes historiques de l'activité ne sont jamais mélangées avec un lieu d'occurrence. Sans horaire,
  ce sont les colonnes de l'activité.
- Champs de lieu `null` si inconnus. `distanceMeters` (mètres, arrondi) est `null` sans position de
  référence ou sans position effective.
- `total` : nombre de résultats tous filtres appliqués, avant pagination ; il reste exact quand `offset`
  dépasse la fin (`items` vide).

## Correspondance

- Champs cherchés :
  - titre ;
  - description ;
  - catégories : catégorie libre, codes et libellés du référentiel ;
  - ville et adresse : celles de l'activité **et** des lieux effectifs de ses occurrences retenues.
- Normalisation identique pour la requête et les champs : `ll_search_normalize` (V58, SQL pur).
  Elle met en minuscules, retire les accents latins (é → e) et transforme les ligatures (œ → oe, æ → ae,
  ß → ss). La ponctuation et les espaces superflus deviennent un seul espace.
- Un champ correspond s'il contient **tous les termes significatifs** de la requête, dans n'importe quel
  ordre. Contenir la requête entière l'implique.
- Termes significatifs : les mots de la requête normalisée, moins les mots vides (`a, au, aux, d, de, des,
  du, en, et, l, la, le, les, ou, un, une, the, of, and`). Une requête faite uniquement de mots vides
  utilise tous ses mots.
- Correspondance par sous-chaîne : `art` trouve `marteau`.
- **Hors V1** : fautes de frappe, synonymes, correspondance répartie sur plusieurs champs
  (« concert lyon » avec « concert » dans le titre et « Lyon » en ville ne correspond pas). À proposer
  séparément après mesure.

## Classement

1. **Niveau de pertinence**, du plus fort au plus faible :

   | Niveau | Condition |
   |---|---|
   | `TITLE_EXACT` | titre normalisé égal à la requête normalisée |
   | `TITLE_PREFIX` | titre normalisé commençant par la requête |
   | `TITLE_CONTAINS` | titre contenant tous les termes significatifs |
   | `CATEGORY_MATCH` | correspondance dans une catégorie |
   | `LOCATION_MATCH` | correspondance dans la ville ou l'adresse |
   | `DESCRIPTION_MATCH` | correspondance uniquement dans la description |

2. Nombre de champs distincts qui correspondent (titre, catégorie, lieu, description), décroissant.
3. Distance au lieu effectif retenu, croissante, si une position de référence est fournie ; les distances
   inconnues viennent en dernier.
4. `startDate` croissante, inconnues en dernier.
5. `id` croissant.

La distance ne passe **jamais** avant le niveau, et **aucun rayon** ne s'applique : les résultats
lointains restent accessibles par pagination.

## Visibilité et modèle riche (Sprint 11, ADR-0006)

- Activités `PUBLISHED` uniquement.
- Lieu effectif d'une occurrence : `COALESCE(occurrence.location_id, schedule.location_id)`, qui couvre
  les changements exceptionnels de lieu.
- Occurrences `CANCELLED` ignorées : elles ne rendent l'activité ni éligible, ni trouvable par leur lieu,
  ni proche.
- Activité avec horaires : retenue si au moins une occurrence non annulée respecte le filtre temporel.
  Sans horaire, ses dates et son lieu historiques s'appliquent.
- **Une seule ligne par activité**, quel que soit le nombre d'occurrences.
- Activité sans aucune position effective : conservée si le texte correspond, avec `distanceMeters` à
  `null`.

## Erreurs `400`

Format `ErrorResponse` habituel (`timestamp`, `status`, `error`, `message`, `path`). Cas couverts :
- `q` absent, vide, de 1 caractère ou de plus de 120, ou sans lettre ni chiffre ;
- `latitude` sans `longitude`, ou l'inverse ;
- latitude ou longitude non numérique ou hors bornes ;
- `date` ou `dateTo` mal formée ; `dateTo` sans `date` ; `dateTo < date` ;
- `limit` hors de 1 à 100 ou non entier ; `offset` négatif.

## Performance

Mesures `EXPLAIN ANALYZE` sur PostgreSQL 16 / PostGIS 3.4 en local. Jeu de données généré dans une
transaction annulée : 20 000 activités (18 000 publiées), la moitié avec horaires, 30 000 occurrences
réparties sur 3 000 lieux. Trois exécutions par requête.

| Version | Temps par requête |
|---|---|
| Première écriture : sous-requêtes corrélées sur les occurrences | ~50 s |
| Agrégations par activité (`GROUP BY`) | ~18 s |
| + textes normalisés calculés à l'écriture (V59) | ~2,3 s |
| + termes calculés une fois, `LIKE ALL` sans sous-requête par ligne | **~0,15 à 0,35 s** |

Décisions qui en découlent :
- **V59, colonnes générées** `search_title`, `search_description`, `search_category`, `search_place`
  sur `activity`, et `search_place` sur `location`. Normaliser les descriptions à chaque recherche
  coûtait à lui seul ~6 s.
- **Pas d'index texte** à ce volume : le reste du coût est le parcours des activités publiées et de
  leurs occurrences (~0,2 s). Si le volume grandit nettement, la piste mesurable est un index trigramme
  (GIN `pg_trgm`) sur les colonnes `search_*`. Cela demande d'installer l'extension, donc une migration
  dédiée et une décision.
- Pas d'extension `unaccent` : disponible mais non installée, et son installation dépend des droits du
  rôle de migration. `ll_search_normalize` (SQL pur, `IMMUTABLE`) la remplace pour l'alphabet latin.

## Limites connues

- Accents : alphabet latin seulement ; les autres écritures sont comparées telles quelles, en minuscules.
- Correspondance par sous-chaîne, sans notion de mot ; pas de fautes de frappe ni de synonymes.
- Pas de correspondance répartie sur plusieurs champs (voir Correspondance).
- Activité sans horaire, et sans date de début ni de fin : jamais retenue, comme dans `/nearby`.
