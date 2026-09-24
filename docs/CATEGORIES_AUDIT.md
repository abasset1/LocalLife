# CATEGORIES_AUDIT — Audit du système actuel de catégories (CAT-000)

> Sprint CATEG, ticket CAT-000. Document d'analyse uniquement : **aucun code métier n'est modifié**.
> Base analysée : `main` @ `d7520f1`.

## 0. Limites de cet audit

* **Inventaire des valeurs en base réelle : non réalisé ici.** L'audit a été mené sur le dépôt
  (code, migrations, tests, fixtures, docs). Les données des bases dev/beta/prod ne sont pas
  accessibles depuis l'environnement d'analyse. La section 6 liste donc les valeurs **attendues
  d'après le code** et fournit les requêtes SQL à exécuter pour obtenir les comptes réels
  (à joindre à ce document avant CAT-001).
* Les tests n'ont pas pu être exécutés (pas de Maven / Maven Central en sandbox). Aucun fichier
  Java, SQL ou TypeScript n'étant modifié, le risque de régression est nul ; `mvn verify` reste à
  lancer en CI/local par précaution.

## 1. Architecture actuelle

Il existe **deux mécanismes totalement déconnectés** :

| Élément | Nature | Rôle réel |
|---|---|---|
| `activity.category` | `VARCHAR(255)` nullable, chaîne libre (`V2__create_activity_table.sql`) | Seule catégorie réellement utilisée (filtre, affichage, import) |
| table `category` | `id`, `name`, `description` (`V5__create_category_table.sql`) | Référentiel **vide et orphelin** : aucune FK, aucune donnée insérée, aucun code applicatif ne l'utilise hors sa propre consultation |

### 1.1 Modèle `Category` (module `category`)

* `Category` = record `(Long id, String name, String description)` — pas de `code`, `active`,
  `display_order`, pas de contrainte d'unicité sur `name`, `name` nullable.
* `CategoryRepository` : lecture seule (`findAll`, `findById`), étend `Repository`.
* `CategoryService` : simple délégation.
* `CategoryController` : `GET /api/v1/categories` (public), renvoie l'entité brute (pas de DTO).
  Pas d'endpoint d'écriture.
* Aucune migration n'insère de catégories (V5 crée seulement la table). L'endpoint renvoie donc `[]`.
* Tests : `CategoryControllerTest` (mock, deux catégories fictives `Concert`, `Marché`).

### 1.2 Modèle `Activity`

* `Activity.category` : `String` (record), colonne `activity.category` `VARCHAR(255)`, **nullable**.
* Aucun lien avec `category` (décision documentée LL-4004 dans `ActivityService`,
  `PROJECT_STATUS.md`, `DATA_QUALITY_AUDIT.md` problème n°7).
* Recopiée à l'identique dans `ActivityResponse`, `ActivitySummaryResponse`, `ActivityDetailResponse`.
* Les mots-clés OpenAgenda complets sont stockés à part dans `activity_tag` (LL-11011).

### 1.3 `FoodTruck` (cas séparé)

* `food_truck.category` `VARCHAR(255) NOT NULL` (V12), chaîne libre (« type de cuisine »),
  **obligatoire** contrairement à `Activity`, validée uniquement non vide (`FoodTruckService`).
* Créé via `POST /api/v1/foodtrucks` (authentifié). Aucune relation avec la table `category`.
* Non couvert par le périmètre du sprint tel que rédigé (activités) : **décision à prendre** (§8).

## 2. Flux de création d'une « catégorie »

Il n'existe pas de création de catégorie au sens référentiel : une catégorie est **une chaîne
copiée dans `activity.category`** au moment de la création/import de l'activité. Toute valeur non
vide est acceptée. Une nouvelle valeur « existe » dès qu'une activité la porte.

```
Contribution manuelle : ContributionModal (input texte, required)
   → POST /api/v1/activities {category}  → ActivityService.createActivity
   → validateCategory (non blank si fournie) → INSERT activity.category (statut PENDING)

OpenAgenda : keywords.fr[0]  → OpenAgendaCollector.firstKeyword
   → CollectedActivity.category → NormalizationService.normalize (isValidCategory : non blank)
   → ImportService.withSourceAndKey → save (statut PUBLISHED)
   (keywords.fr complets → CollectedActivity.tags → activity_tag)

Seed : V3__insert_demo_activities.sql (5 valeurs minuscules)
```

Il n'existe **aucun endpoint de modification** d'activité (seulement `PATCH /{id}/cancel` et
`publish`/`reject` admin) : `category` n'est écrite qu'à la création/import (et ré-écrite à chaque
ré-import d'une activité existante, `existing.map(Activity::id)` → même `importKey`).

## 3. Producteurs de catégories (écritures)

| # | Producteur | Fichier | Valeur écrite | Contrôle |
|---|---|---|---|---|
| P1 | Import OpenAgenda | `OpenAgendaCollector#firstKeyword` (l. ~287, 392) | 1er mot-clé français brut, ou `null` si aucun | aucune normalisation (casse, accents, pluriel) |
| P2 | Normalisation import | `NormalizationService#normalize` (l. 125), `isValidCategory` (l. 383/408) | recopie | rejette seulement la chaîne blanche (`null` accepté) |
| P3 | Persistance import | `ImportService#withSourceAndKey` / `withStatus` (l. 424/434) | recopie | — |
| P4 | Contribution manuelle API | `ActivityController#createActivity` → `ActivityService#createActivity` / `validateCategory` (l. 733, 786) | chaîne libre de l'utilisateur | non blank si fournie ; `null` accepté côté API |
| P5 | Rattachement source supprimée | `SourceService#withSourceId` (l. 171) | recopie (ne change pas la valeur) | — |
| P6 | Seed démo | `V3__insert_demo_activities.sql` | `concert`, `marché`, `food truck`, `exposition`, `cinéma` | — |
| P7 | Food trucks | `FoodTruckController` / `FoodTruckService#validateCategory` | chaîne libre (obligatoire) | non blank |
| P8 | Frontend (formulaire) | `ContributionModal.tsx` (`<input type="text" required>`), état `category` de `App.tsx` | saisie libre | `required` HTML |

Autres sources : aucune (pas d'autre collecteur : `collectors/` ne contient qu'un README ; pas
d'import CSV, pas de script de seed hors V3, pas de fixtures JSON/SQL de catégories).

## 4. Consommateurs de catégories (lectures)

### Backend
* `ActivityRepository` (2 requêtes : `findWithinRadius`, `findWithinBounds`) :
  `a.category = ANY(string_to_array(:categoriesCsv, ','))` — **égalité exacte, sensible à la casse
  et aux accents**.
* `ActivityService#findNearby` / `#findWithinBounds` / `normalizeCategories` : trim + retrait des
  valeurs vides ; une catégorie inconnue renvoie une liste vide (décision LL-4004, pas de 400).
* `ActivityController` (`GET /nearby`, `/within-bounds`, param `category` CSV) ; DTO de réponse
  `ActivityResponse`, `ActivitySummaryResponse`, `ActivityDetailResponse` (champ `category`).
* `CategoryController` (`GET /api/v1/categories`) : servi mais consommé seulement par le mobile
  (voir ci-dessous) et retourne une liste vide.

### Frontend web
* `activityUtils.ts#buildCategoryOptions` : **les filtres sont calculés à partir des activités
  affichées** (`new Set` des `item.category` + tri `localeCompare`), uniquement quand aucun filtre
  catégorie/date n'est actif (`App.tsx` l. ~144). Le frontend n'appelle pas `/api/v1/categories`.
* `ExplorerFilters.tsx` (`<select>` alimenté par ces valeurs brutes), `App.tsx` (envoi de
  `category` en query string), `activityUtils.ts#matchesSearchQuery` (la catégorie fait partie du
  texte recherché).
* Affichage brut : `ActivityPreview.tsx`, `ActivityList.tsx`, `ActivityDetailModal.tsx`,
  `FavoritesPage.tsx`, `AdminPage.tsx`, `ExplorerMap.tsx` (food trucks).
* Types : `explorer/types.ts` (`category: string`, non nullable alors que la donnée peut être `null`).

### Mobile (`mobile/`)
* `api/categories.ts#listCategories` (`GET /categories`) exporté mais **aucun écran ne l'utilise**
  (aucun usage hors `index.ts`) ; `api/types.ts` (`Category {id,name,description}`, `category: string`
  sur activités/food trucks) ; `api/activities.ts` (transmission du paramètre `category`) ;
  `features/map/PreviewCard.tsx` (affichage brut).

### Documentation / maquette
* `LocalLife_home_mockup.html` : catégories fictives (Culture, Gastronomie, Musique, Sport, Sorties,
  Food trucks) — maquette statique, aucun lien avec le code.
* `docs/02_Architecture/GEO_SEARCH_CONTRACT.md`, `BOUNDING_BOX_SEARCH_CONTRACT.md`,
  `FOOD_TRUCK_CONTRACT.md`, `DATA_QUALITY_AUDIT.md` (§ problème n°7), `PROJECT_STATUS.md`
  (LL-4004, LL-5005, LL-7007), `ARCHITECTURE.md`, `SPRINT_1/2/4/10/11.md` : décrivent la catégorie
  comme chaîne libre.

## 5. Validateurs

| Validateur | Règle | Limite |
|---|---|---|
| `ActivityService#validateCategory` | chaîne fournie ⇒ non blanche | aucune liste fermée, aucune longueur max (colonne 255 → erreur SQL possible), `null` accepté |
| `NormalizationService#isValidCategory` | `null` ou non blanche | idem ; pas de catégorie ⇒ import accepté |
| `FoodTruckService#validateCategory` | obligatoire, non blanche | idem |
| Frontend | `required` sur l'input | contournable (API directe) |

## 6. Inventaire des valeurs de catégorie

### 6.1 Valeurs identifiables dans le dépôt

| Valeur | Occurrences dans le dépôt | Source | Variantes / remarques | Catégorie métier potentielle (proposition CAT-001) |
|---|---|---|---|---|
| `concert` | seed V3 (1), tests repo/service/controller/OpenAgenda (nombreux) | Seed, tests, OpenAgenda (mot-clé) | variante `Concert` (test `CategoryControllerTest`, doc audit) ; « concert(s) », « musique » | MUSIC |
| `marché` | seed V3 (1), tests import/normalisation/collector (nombreux) | Seed, tests, OpenAgenda | `marche`, `Marché`, `marchés` ; recouvre marché de Noël, brocante | MARKET |
| `food truck` | seed V3 (1) | Seed | doublon conceptuel avec la table `food_truck` | FOOD ou hors périmètre (food trucks) |
| `exposition` | seed V3 (1) | Seed | | CULTURE |
| `cinéma` | seed V3 (1) | Seed | `cinema` | CINEMA |
| `sport` | tests `ActivityRepositoryIntegrationTest`, `ImportedActivityVisibilityIntegrationTest` | Tests | | SPORT |
| `loisir` | `NonRegressionIntegrationTest` (création manuelle + filtre) ; assertion `ActivityServiceTest` | Tests | terme très générique | OTHER (ambigu) |
| `brocante`, `atelier` | `OpenAgendaCollectorTest` (mots-clés) | Tests OpenAgenda | | MARKET, WORKSHOP |
| `noël` | 2ᵉ mot-clé des tests OpenAgenda (conservé en tag, pas en catégorie) | Tests OpenAgenda | | tag uniquement |
| `Category`, `Another Category` | `ActivityControllerTest` | Tests (placeholders) | valeurs non métier | à remplacer en CAT-009 |
| `categorie-inexistante-xyz` | `ActivityControllerIntegrationTest` | Test filtre inconnu | comportement à revoir en CAT-007 (400) | — |
| `null` | activités OpenAgenda sans mot-clé français (**confirmé en réel**, LL-7003/LL-7007 : ex. ids 114, 128, 133) ; contributions API sans catégorie | OpenAgenda / API | a déjà provoqué un bug frontend (`buildCategoryOptions`, corrigé depuis) | classification par titre/description, sinon OTHER |

### 6.2 Valeurs réelles en base (à extraire)

À exécuter sur chaque environnement (dev, beta, prod) et à coller dans ce document :

```sql
-- Comptes par valeur brute et par source
SELECT COALESCE(a.category, '<NULL>')       AS category,
       s.type                                AS source_type,
       s.name                                AS source_name,
       COUNT(*)                              AS nb
FROM activity a
LEFT JOIN source s ON s.id = a.source_id
GROUP BY 1, 2, 3
ORDER BY nb DESC;

-- Variantes potentielles (casse / accents / espaces)
SELECT lower(unaccent(btrim(category))) AS normalise,
       array_agg(DISTINCT category)      AS variantes,
       COUNT(*)                          AS nb
FROM activity
WHERE category IS NOT NULL
GROUP BY 1
HAVING COUNT(DISTINCT category) > 1
ORDER BY nb DESC;   -- nécessite l'extension unaccent

-- Activités sans catégorie
SELECT COUNT(*) FILTER (WHERE category IS NULL OR btrim(category) = '') AS sans_categorie,
       COUNT(*) AS total
FROM activity;

-- Mots-clés d'origine (pour CAT-002/004) : top des tags OpenAgenda
SELECT lower(btrim(t.name)) AS tag, COUNT(*) AS nb   -- adapter le nom de colonne à activity_tag (V24)
FROM activity_tag t
GROUP BY 1 ORDER BY nb DESC LIMIT 200;

-- Food trucks
SELECT category, COUNT(*) FROM food_truck GROUP BY 1 ORDER BY 2 DESC;
```

### 6.3 Répartition par source (structure attendue)

| Source | Production de catégorie | Attendu en base |
|---|---|---|
| OpenAgenda (source `type` agenda, statut `PUBLISHED`) | 1er mot-clé `keywords.fr` brut, sinon `null` | Grande diversité de valeurs libres, nombreux `null`, variantes de casse/accents/pluriels |
| Manuelle (source réservée `MANUAL`) | Saisie libre du contributeur (statut `PENDING`) | Valeurs libres courtes (« loisir », « concert », ...) |
| Seed / tests | 5 valeurs V3 (uniquement si V3 appliquée en env.) | `concert`, `marché`, `food truck`, `exposition`, `cinéma` |
| Autres | aucune | — |

## 7. Problèmes constatés

1. **Deux systèmes de catégories** : table `category` vide et inutilisée, `activity.category` libre.
2. **Aucune normalisation** : `Concert`/`concert`/`concerts` = trois valeurs, filtre à égalité stricte.
3. **OpenAgenda impose sa taxonomie** : le 1er mot-clé (ordre arbitraire, éditeur-dépendant) devient la
   catégorie ; les mots-clés suivants sont perdus comme catégorie (conservés en tags seulement).
4. **`null` massif** sur les imports sans mot-clé ; `null` accepté par tous les validateurs.
5. **Saisie manuelle libre** (API et formulaire) ; aucune borne de longueur (colonne 255).
6. **Filtres frontend dérivés des données affichées** : ensemble variable selon la vue, valeurs brutes
   affichées, dépend des activités chargées (pas de `display_order`, pas de libellés officiels).
7. **Filtre backend fragile** : égalité exacte, catégorie inconnue → liste vide (aucune erreur).
8. **Ré-import écrasant** : `category` est réécrite à chaque import (aucune valeur manuelle
   correctrice possible tant qu'il n'y a pas de mécanisme dédié).
9. **`FoodTruck.category`** : 3ᵉ zone de texte libre, obligatoire, sans lien avec le référentiel.
10. **Types TypeScript incohérents** : `category: string` alors que `null` est possible.
11. **Tests et fixtures** : valeurs en dur (`concert`, `marché`, `sport`, `loisir`, `Category`) partout ;
    `CategoryControllerTest` ne teste que des mocks.
12. **Endpoint `/api/v1/categories`** : entité brute exposée, sans DTO, sans tri, sans `active`.

## 8. Risques de migration

* **Volume et diversité inconnus** tant que les requêtes §6.2 ne sont pas exécutées : le mapping
  ancienne valeur → catégorie officielle ne peut être validé qu'avec ces comptes.
* **Perte d'information** : `activity.category` (1er mot-clé) est la seule trace de la catégorie
  d'origine pour certaines activités ; à conserver (tags ou colonne d'archive) avant suppression.
* **Ré-import OpenAgenda** : tant que le collecteur écrit le 1er mot-clé, un ré-import annule tout
  reclassement (CAT-006 doit précéder toute exécution d'import après CAT-005, ou CAT-005 doit être
  rejoué).
* **Compatibilité API** : le champ `category` (chaîne) est exposé par 3 DTO, consommé par le web et le
  mobile, et le paramètre de filtre `category` (CSV de chaînes) est documenté dans les contrats
  `GEO_SEARCH`/`BOUNDING_BOX`. Passage à un code stable = évolution de contrat à documenter.
* **Contrat de filtre** : décision LL-4004 (« catégorie inconnue → liste vide ») contredit CAT-007
  (« 400 »). À trancher explicitement pour les endpoints de **lecture**.
* **Migrations Flyway** : `V31` est la dernière ; toute nouvelle migration doit suivre. Le seed V3
  existe déjà en base : il faudra reclasser ses 5 valeurs. Colonnes nullable existantes : la
  contrainte `NOT NULL` ne pourra être posée qu'après reclassement complet (CAT-005).
* **Requêtes SQL natives** : les deux requêtes `ActivityRepository` filtrent sur la chaîne ; elles
  devront être réécrites (jointure ou `category_id`) avec un plan d'index.
* **Tests** : ~15 fichiers de test contiennent des valeurs en dur ; la refonte touche
  `ActivityRepositoryIntegrationTest`, `ActivityControllerIntegrationTest`, `NonRegressionIntegrationTest`,
  `ImportServiceTest`, `NormalizationServiceTest`, `OpenAgendaCollectorTest` (assertions
  `assertEquals("marché", ...)`).
* **Food trucks** : hors périmètre explicite du sprint ; si non traités, ils resteront une source de
  catégories libres (contraire à l'objectif « aucune catégorie incohérente »).

## 9. Recommandations pour CAT-001

1. Réaliser l'extraction §6.2 **avant** de figer le référentiel et confronter la liste initiale du
   sprint (MUSIC, SHOW, CULTURE, …, OTHER) aux valeurs réelles (notamment top tags OpenAgenda).
2. Prévoir des **codes stables** indépendants des libellés (la table actuelle n'a que `name`) ;
   CAT-003 devra ajouter `code`, `active`, `display_order` (aucun de ces champs n'existe).
3. Traiter explicitement `loisir`, `food truck`, `exposition`, `festival`, `noël` (valeurs
   ambiguës ou sans équivalent direct) dans la section « cas ambigus ».
4. Décider du sort de **`FoodTruck.category`** (dans le sprint ou hors périmètre documenté).
5. Décider du comportement des filtres de lecture pour une catégorie inconnue (liste vide actuelle vs 400).
6. Décider de la conservation de la valeur d'origine (tags OpenAgenda déjà conservés ; prévoir une
   trace pour les contributions manuelles avant reclassement).
7. Ordonnancement conseillé pour les risques : CAT-006 (imports) ne doit pas être exécuté en prod
   entre CAT-005 et sa mise en service, sous peine de réintroduire des valeurs libres.

## 10. Critères d'acceptation CAT-000

* [x] Producteurs de catégories identifiés (§3)
* [x] Consommateurs identifiés (§4)
* [x] Fonctionnement OpenAgenda documenté (§2, §3 P1–P3)
* [x] Saisie manuelle documentée (§2, §3 P4/P8)
* [x] Modèle `Category` existant documenté (§1.1)
* [~] Valeurs existantes inventoriées : valeurs du dépôt inventoriées (§6.1) ; **comptes réels en base
  à extraire** avec les requêtes §6.2 (pas d'accès aux bases depuis l'audit)
* [x] Aucun code métier modifié
* [~] Tests existants : inchangés (aucun fichier de code touché) ; `mvn verify` non exécutable en
  sandbox, à confirmer en CI
