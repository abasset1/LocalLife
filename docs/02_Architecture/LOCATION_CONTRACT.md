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
- Paramètres de filtre (`city`) et de tri (`sort`) : LL-10006.
