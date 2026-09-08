# Contrat de localisation d'une activité (LL-10001)

Définit précisément les données de localisation exposées par une
activité, conformément au modèle cible du Sprint 10
(`docs/05_Sprints/SPRINT_10.md`). Sert de base à l'évolution de la
persistance (LL-10002), à la normalisation à l'écriture (LL-10003,
LL-10004) et à l'exposition API (LL-10005, LL-10006) — pas de code à
ce stade, uniquement le contrat.

---

## Contexte

Le ticket LL-EF-008 (Sprint Évol-Fix, voir
`ADR-0001-adresse-structuree-activites.md`) a introduit `address`,
`city` et `postalCode` sur `Activity` pour permettre l'affichage d'un
lieu lisible et un premier regroupement par ville. Le Sprint 10
généralise et formalise cette base : ce document en fixe le contrat
définitif, réutilisable par le web comme par le futur client mobile.

## Modèle cible

Une activité expose les champs de localisation suivants, en plus de
ses champs existants (`id`, `title`, `description`, `category`,
`startDate`, `endDate`, `status`) :

| Champ         | Type    | Obligatoire | Nullable | Description                                                        |
| ------------- | ------- | ------------ | -------- | -------------------------------------------------------------------- |
| `address`     | string  | non          | oui      | Adresse lisible telle que saisie ou fournie par la source (ex. `"10 rue de la République"`). Texte libre, non structuré au-delà de la valeur elle-même. |
| `postalCode`  | string  | non          | oui      | Code postal (ex. `"84000"`). Type `string` (et non numérique) : préserve les codes avec zéro non significatif et reste compatible avec des formats non français si une source étrangère est ajoutée un jour. |
| `city`        | string  | non          | oui      | Ville (ex. `"Avignon"`). Donnée structurée indépendante de `address` — voir « Comportement des champs absents » ci-dessous. |
| `latitude`    | double  | oui          | non      | Coordonnée GPS. Référence pour la carte et la recherche géographique (`nearby`, `within-bounds`), inchangée par ce contrat. |
| `longitude`   | double  | oui          | non      | Coordonnée GPS. Idem `latitude`. |

Exemple de représentation JSON :

```json
{
  "id": 42,
  "title": "Marché de Noël",
  "description": "Marché artisanal en centre-ville",
  "category": "marché",
  "address": "10 rue de la République",
  "postalCode": "84000",
  "city": "Avignon",
  "latitude": 43.9493,
  "longitude": 4.8055,
  "startDate": "2026-12-01T09:00:00",
  "endDate": "2026-12-24T19:00:00",
  "status": "PUBLISHED"
}
```

## Comportement des champs absents

- `latitude`/`longitude` restent **obligatoires** : aucune activité
  n'existe sans coordonnées, inchangé par ce contrat.
- `address`, `postalCode` et `city` sont **individuellement
  nullables**. Une activité peut avoir une `city` connue sans
  `postalCode` (ou l'inverse) selon ce que la source ou le géocodage
  ont pu résoudre — aucun des trois champs ne dépend des deux autres
  pour être présent.
- Un champ absent est représenté par `null` en JSON, jamais par une
  chaîne vide ni par une valeur déduite ou approximative. En
  particulier :
  - `city` n'est **jamais** déduite de `address` (ni côté backend, ni
    côté frontend — règle explicite du Sprint 10) ;
  - aucune ville n'est inventée à partir d'une adresse non fiable ou
    partiellement résolue.
- Un client (web ou mobile) doit prévoir un repli d'affichage pour
  chacun de ces trois champs pris individuellement (ex. regroupement
  sous « Ville non renseignée » déjà en place côté frontend depuis
  LL-EF-008). Ce contrat ne définit que la donnée ; le comportement
  d'affichage du repli reste du ressort de chaque client.
- L'absence de `address`/`postalCode`/`city` ne bloque et n'invalide
  jamais la création ou l'import d'une activité (cohérent avec
  `NormalizationService#isValid`, qui ne les valide pas).

## Compatibilité avec les données existantes

- Les trois champs sont additifs : une activité créée avant leur
  introduction (ou dont la source ne les fournit pas) reste valide,
  avec `address`/`postalCode`/`city` à `null`.
- Aucun backfill rétroactif n'est requis par ce contrat : les
  activités existantes ne sont ni re-géocodées ni ré-importées
  automatiquement (cohérent avec la décision prise en LL-EF-008).
- `latitude`/`longitude` ne changent ni de nom, ni de type, ni de
  sémantique : aucune régression attendue sur la carte ni sur les
  recherches géographiques existantes (`nearby`, `within-bounds`).

## Consommation par le web et le futur mobile

- Les noms de champs (`address`, `postalCode`, `city`, `latitude`,
  `longitude`) et leur sémantique sont indépendants de tout client :
  aucune forme dérivée ou pré-formatée pour l'affichage (pas de champ
  du type `displayLocation` construit côté backend) n'est incluse
  dans ce contrat — la composition d'un texte d'affichage à partir de
  ces champs reste la responsabilité de chaque client.
- `postalCode` et `city` sont fournis comme données structurées
  distinctes précisément pour éviter qu'un client (mobile en
  particulier) n'ait à parser `address` pour en extraire une ville
  ou un code postal.
- Ce contrat ne définit pas encore *quels* endpoints exposent ces
  champs ni le format exact de pagination/tri — périmètre de
  LL-10005 et LL-10006. Il fixe uniquement la forme et la sémantique
  des champs eux-mêmes, pour que ces tickets n'aient plus à en
  décider.

## État actuel de l'implémentation

Depuis LL-10005, les quatre endpoints exposant une activité respectent
uniformément ce contrat :

| Endpoint                          | `address` | `postalCode` | `city` |
| ---------------------------------- | :-------: | :-----------: | :----: |
| `GET /api/v1/activities`           | ✅ | ✅ | ✅ |
| `GET /api/v1/activities/{id}`      | ✅ | ✅ | ✅ |
| `GET /api/v1/activities/nearby`    | ✅ | ✅ | ✅ |
| `GET /api/v1/activities/within-bounds` | ✅ | ✅ | ✅ |

(`postalCode` manquait initialement sur `ActivityResponse`, utilisée par
`nearby`/`within-bounds` — comblé par LL-10005.)

## Points laissés ouverts pour les tickets suivants

- Règles précises de normalisation à l'écriture (géocodage, source des
  trois champs pour une contribution manuelle vs. un import) : LL-10003
  et LL-10004 (déjà couverts, voir leurs livrables respectifs).
- Paramètres de filtre (`city`) et de tri (`sort`) : LL-10006 (voir
  section dédiée ci-dessous, désormais implémentée).

## Filtre `city` et tri `sort` (LL-10006)

Contrat exact demandé par `SPRINT_10.md` (« les valeurs exactes de
`sort` doivent être définies dans le contrat avant implémentation »),
défini et implémenté par ce même ticket — sur le modèle des sections
« Décision » déjà présentes dans `GEO_SEARCH_CONTRACT.md` pour
`category`/`date`.

### Endpoint concerné

```text
GET /api/v1/activities
```

Uniquement cet endpoint (listing simple). `nearby` et
`within-bounds` ont leurs propres contrats
(`GEO_SEARCH_CONTRACT.md`, `BOUNDING_BOX_SEARCH_CONTRACT.md`) et
restent inchangés — hors périmètre de LL-10006.

### Paramètres (query string)

| Paramètre | Type   | Obligatoire | Description |
| --------- | ------ | ------------ | ------------ |
| `city`    | string | non | Filtre les résultats sur cette ville. Comparaison **exacte, insensible à la casse** (`LOWER(city) = LOWER(:city)`) — pas de recherche partielle, pas de normalisation des accents. Absent → aucun filtrage. Ville ne correspondant à aucune activité → liste vide (`200 OK`), pas d'erreur (même décision que `category` sur `/nearby`, voir `GEO_SEARCH_CONTRACT.md`). Une activité dont `city` est `null` en base ne correspond jamais à un `city` donné explicitement (rien à comparer). |
| `sort`    | string | non | Une ou plusieurs clés de tri séparées par une virgule, parmi **exactement** `city` et `date` (ex. `city`, `date`, `city,date`, `date,city`) — l'ordre des clés fixe la priorité du tri (`sort=city,date` trie d'abord par ville, puis par date à ville égale). `date` trie sur `startDate`. Tri **croissant uniquement** (pas de suffixe `-`/`desc` : non demandé par le ticket, non implémenté). Absent → comportement historique inchangé (aucun `ORDER BY` explicite ajouté, ordre non garanti). Valeur inconnue (ni `city` ni `date`) ou clé dupliquée → `400 Bad Request`. |

Exemples :

```text
GET /api/v1/activities?city=Avignon
GET /api/v1/activities?city=avignon        (identique au précédent, comparaison insensible à la casse)
GET /api/v1/activities?city=Avignon&sort=date
GET /api/v1/activities?sort=city,date
```

### Décision : déterminisme de la comparaison/du tri

Critère d'acceptation « la comparaison de ville est déterministe » :
compris ici comme « la règle de comparaison est fixe et prévisible »,
pas comme une exigence de recherche floue. Deux garanties concrètes :

- la comparaison de `city` utilise systématiquement `LOWER()` des deux
  côtés (paramètre et colonne) — un même couple de chaînes donne
  toujours le même résultat, quelle que soit la casse saisie par le
  client ;
- lorsque `sort` est fourni, l'`id` croissant est ajouté comme dernier
  critère de tri (après les clés demandées) — deux activités à égalité
  sur `city`/`date` (ou une valeur `city`/`date` absente pour les
  deux) obtiennent malgré tout un ordre stable et reproductible d'un
  appel à l'autre, plutôt qu'un ordre dépendant du plan d'exécution
  PostgreSQL.

Une activité sans `city` (`null`) triée par `city` est placée en fin
de liste (`NULLS LAST`, comportement par défaut de PostgreSQL pour un
tri ascendant) — cohérent avec le regroupement « Ville non renseignée »
déjà utilisé côté frontend (LL-EF-008).

### Décision : pas de paramètre `city`/`sort` sur `nearby`/`within-bounds`

Le ticket LL-10006 (`SPRINT_10.md`) ne donne d'exemples que sur
`GET /api/v1/activities`. Ajouter ces mêmes paramètres aux deux
endpoints de recherche géographique n'est pas demandé par ce ticket
(règle « un ticket = une seule responsabilité », `AI_RULES.md`) —
pourra faire l'objet d'un ticket dédié si un besoin apparaît (ex.
combiner recherche géographique et filtre ville).

### Erreurs

Même format standardisé que le reste de l'API (`ErrorResponse`) :

| Cas | Code |
| --- | ---- |
| `sort` contient une valeur autre que `city`/`date` | `400 Bad Request` |
| `sort` contient une clé en double (ex. `sort=city,city`) | `400 Bad Request` |

### Implémentation

- Filtrage et tri réalisés **côté base de données** (requête SQL avec
  `WHERE`/`ORDER BY` conditionnels), pas en mémoire côté application —
  cohérent avec le critère d'acceptation explicite du ticket et avec
  l'approche déjà retenue pour `findWithinRadius`/`findWithinBounds`.
- `sort` est validé (liste fermée `city`/`date`, pas de doublon) avant
  d'atteindre la requête SQL : les deux seules valeurs possibles sont
  ensuite injectées comme critères de `CASE WHEN` dans une unique
  requête `@Query`, jamais concaténées directement dans le SQL à
  partir de l'entrée brute du client.
