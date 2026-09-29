# CATEGORIES_AUDIT — Audit du système actuel de catégories (CAT-000)

> Sprint CATEG (réécrit), ticket CAT-000. Document d'analyse uniquement : **aucun code métier n'est
> modifié**. Relu et restructuré suite à la réécriture complète de `SPRINT_CATEG.md` et à l'ajout de
> `docs/CATEGORIES_FUNCTIONAL_MODEL.md` (référence fonctionnelle principale du sprint).
>
> Base analysée : `main` @ `3190a4b`.
>
> **Mise à jour 29/09/2026 :** extraction réelle `activity.category` fournie par Alex (1075 lignes) — analysée en §6.2/§6.2 bis. Voir aussi `docs/CATEGORIES_TOP200.md`.

## 0. Lecture à la lumière du nouveau modèle fonctionnel

Le sprint réécrit introduit une distinction que l'audit initial n'avait pas à faire : **catégorie
principale ≠ type/sous-type ≠ politique de visibilité** (`CATEGORIES_FUNCTIONAL_MODEL.md` §2). Cet
audit reprend donc l'inventaire des valeurs de `activity.category` (et `food_truck.category`) en les
qualifiant, quand c'est possible, de :

* **candidate catégorie principale** : famille large, compréhensible par le grand public (ex. musique,
  gastronomie) ;
* **candidate type/sous-type** : plus précis, se rattache à une catégorie (ex. foodtruck, brocante,
  dégustation) ;
* **candidate à `PENDING`** : classification insuffisante (`null`, chaînes non descriptives) ;
* **hors périmètre catégorie d'activité** : décrit autre chose qu'une classe d'événement (ex. type de
  cuisine d'un commerce).

Cette qualification prépare CAT-001 (référentiel) sans le préempter : **aucune décision de
classement définitif n'est prise ici**, conformément au périmètre de CAT-000 (« aucune suppression
arbitraire »). La politique `DEFAULT` / `OPT_IN` / `PENDING` elle-même n'existe dans aucune donnée
actuelle : elle est absente du code (`Activity.status` ne porte que `PENDING`/`PUBLISHED`/`REJECTED`,
qui est un statut de **modération**, distinct de la visibilité définie par le nouveau modèle — voir
§7).

## 0.1 Limites de cet audit (mise à jour)

* **Inventaire des valeurs en base réelle : partiellement réalisé.** Alex a fourni l'extraction
  `activity.category × source_type` (§6.2, §6.2 bis) — analysée ci-dessous. Les extractions
  complémentaires (par `source_name`, tags `activity_tag`, `food_truck.category`) restent à
  fournir (§6.2 ter) avant de figer CAT-001.
* Les tests n'ont pas pu être exécutés (pas de Maven Central en sandbox). Aucun fichier Java, SQL ou
  TypeScript n'est modifié : risque de régression nul ; `mvn verify` reste à lancer en CI/local.

## 1. Architecture actuelle

Il existe **deux mécanismes totalement déconnectés** :

| Élément | Nature | Rôle réel |
|---|---|---|
| `activity.category` | `VARCHAR(255)` nullable, chaîne libre (`V2__create_activity_table.sql`) | Seule catégorie réellement utilisée (filtre, affichage, import) |
| table `category` | `id`, `name`, `description` (`V5__create_category_table.sql`) | Référentiel **vide et orphelin** : aucune FK, aucune donnée insérée, aucun code applicatif ne l'utilise hors sa propre consultation |
| `activity.status` | `VARCHAR` avec `CHECK` (`PENDING`/`PUBLISHED`/`REJECTED`) | Statut de **modération/publication**, indépendant de toute notion de visibilité par défaut |

### 1.1 Modèle `Category` (module `category`)

* `Category` = record `(Long id, String name, String description)` — pas de `code`, `active`,
  `display_order`, pas de champ `type`/`parent`, pas de politique de visibilité, pas de contrainte
  d'unicité sur `name`, `name` nullable.
* `CategoryRepository` : lecture seule (`findAll`, `findById`), étend `Repository`.
* `CategoryService` : simple délégation. `CategoryController` : `GET /api/v1/categories` (public),
  renvoie l'entité brute. Pas d'endpoint d'écriture. Aucune migration n'insère de catégories.
  L'endpoint renvoie donc `[]`.
* **Constat pour CAT-001/CAT-002** : ce modèle ne porte aujourd'hui ni la notion de type/sous-type ni
  de politique `DEFAULT`/`OPT_IN`. Il devra être étendu ou remplacé, pas simplement peuplé.

### 1.2 Modèle `Activity`

* `Activity.category` : `String`, colonne `VARCHAR(255)` **nullable**. Aucun lien avec `category`
  (décision LL-4004, documentée dans `ActivityService`, `PROJECT_STATUS.md`,
  `DATA_QUALITY_AUDIT.md` problème n°7).
* Recopiée à l'identique dans `ActivityResponse`, `ActivitySummaryResponse`, `ActivityDetailResponse`.
* Les mots-clés OpenAgenda complets sont conservés à part dans `activity_tag` (LL-11011) — c'est le
  seul mécanisme actuel qui se rapproche d'une notion de « type/tag » multiple, mais il n'est pas
  structuré (pas de distinction type vs tag libre) ni relié à une politique de visibilité.
* Il n'existe **aucun champ de visibilité** distinct du statut de modération.

### 1.3 `FoodTruck` (cas de référence du nouveau modèle, §7)

* `food_truck.category` `VARCHAR(255) NOT NULL` (V12), chaîne libre (« type de cuisine »),
  **obligatoire**, validée uniquement non vide (`FoodTruckService`).
* Créé via `POST /api/v1/foodtrucks` (authentifié). Aucune relation avec `activity` ni avec la table
  `category`. Module séparé (décision `FOOD_TRUCK_CONTRACT.md`, LL-6008) : pas de dates, pas de
  source, pas d'endpoint de modération.

## 2. Flux de création d'une « catégorie »

Il n'existe pas de création de catégorie au sens référentiel : une catégorie est **une chaîne
copiée dans `activity.category`** au moment de la création/import de l'activité. Toute valeur non
vide est acceptée, sans étape de classification distincte.

```
Contribution manuelle : ContributionModal (input texte, required)
   → POST /api/v1/activities {category}  → ActivityService.createActivity
   → validateCategory (non blank si fournie) → INSERT activity.category (statut PENDING)

OpenAgenda : keywords.fr[0]  → OpenAgendaCollector.firstKeyword
   → CollectedActivity.category → NormalizationService.normalize (isValidCategory : non blank)
   → ImportService.withSourceAndKey → save (statut PUBLISHED)
   (keywords.fr complets → CollectedActivity.tags → activity_tag)

Seed : V3__insert_demo_activities.sql (5 valeurs minuscules)

FoodTruck : POST /api/v1/foodtrucks {category obligatoire} → statut PUBLISHED direct
   (aucune classification, aucun lien avec activity.category ni avec la table category)
```

Aucun de ces flux ne comporte d'étape de **classification LocalLife** au sens du nouveau modèle
(catégorie + type déterminés puis politique de visibilité appliquée) : la valeur saisie ou importée
devient directement la catégorie affichée, et le statut `PENDING` existant est un statut de
modération (« en attente de publication »), pas un état « classification insuffisante ».

Il n'existe **aucun endpoint de modification** d'activité hors `PATCH /{id}/cancel` et
`publish`/`reject` (admin) : `category` n'est écrite qu'à la création/import (et ré-écrite à chaque
ré-import d'une activité existante via le même `importKey`).

## 3. Producteurs de catégories (écritures)

| # | Producteur | Fichier | Valeur écrite | Contrôle | Source unique ? |
|---|---|---|---|---|---|
| P1 | Import OpenAgenda | `OpenAgendaCollector#firstKeyword` (l. ~287, 392) | 1er mot-clé français brut, ou `null` si aucun | aucune normalisation (casse, accents, pluriel) | Oui — spécifique OpenAgenda |
| P2 | Normalisation import | `NormalizationService#normalize` (l. 125), `isValidCategory` (l. 383/408) | recopie | rejette seulement la chaîne blanche (`null` accepté) | — |
| P3 | Persistance import | `ImportService#withSourceAndKey` / `withStatus` (l. 424/434) | recopie | — | — |
| P4 | Contribution manuelle API | `ActivityController#createActivity` → `ActivityService#createActivity` / `validateCategory` (l. 733, 786) | chaîne libre de l'utilisateur | non blank si fournie ; `null` accepté côté API | Oui — spécifique à la saisie manuelle |
| P5 | Rattachement source supprimée | `SourceService#withSourceId` (l. 171) | recopie (ne change pas la valeur) | — | — |
| P6 | Seed démo | `V3__insert_demo_activities.sql` | `concert`, `marché`, `food truck`, `exposition`, `cinéma` | — | — |
| P7 | Food trucks | `FoodTruckController` / `FoodTruckService#validateCategory` | chaîne libre (« type de cuisine », obligatoire) | non blank | Oui — spécifique au module `foodtruck`, sémantique différente (§7) |
| P8 | Frontend (formulaire) | `ContributionModal.tsx` (`<input type="text" required>`), état `category` de `App.tsx` | saisie libre | `required` HTML | — |

**Catégories spécifiques à une source** : P1 (OpenAgenda, vocabulaire de l'éditeur, hors contrôle
LocalLife) et P4/P8 (saisie manuelle, vocabulaire de l'utilisateur) sont les deux seules sources
réelles de valeurs ; elles ne partagent aujourd'hui **aucun vocabulaire imposé** — un mapping
« catégorie source → catégorie LocalLife » (exigé par le nouveau modèle, §10) n'existe donc pas
pour P1, et P4/P8 n'ont aucune liste fermée à respecter. Autres sources : aucune (pas d'autre
collecteur : `collectors/` ne contient qu'un README ; pas d'import CSV, pas de script de seed hors
V3, pas de fixtures JSON/SQL de catégories).

## 4. Consommateurs de catégories (lectures)

### Backend
* `ActivityRepository` (2 requêtes : `findWithinRadius`, `findWithinBounds`) :
  `a.category = ANY(string_to_array(:categoriesCsv, ','))` — **égalité exacte, sensible à la casse
  et aux accents**.
* `ActivityService#findNearby` / `#findWithinBounds` / `normalizeCategories` : trim + retrait des
  valeurs vides ; une catégorie inconnue renvoie une liste vide (décision LL-4004 — **à confronter
  au nouveau modèle** : le sprint réécrit ne tranche pas explicitement ce point comme le faisait la
  version précédente ; §7 revient sur `PENDING` vs catégorie de filtre inconnue, à traiter en CAT-005).
* `ActivityController` (`GET /nearby`, `/within-bounds`, param `category` CSV) ; DTO de réponse
  `ActivityResponse`, `ActivitySummaryResponse`, `ActivityDetailResponse` (champ `category`).
* `CategoryController` (`GET /api/v1/categories`) : servi mais retourne une liste vide.

### Frontend web
* `activityUtils.ts#buildCategoryOptions` : **les filtres sont calculés à partir des activités
  affichées** (`new Set` des `item.category` + tri `localeCompare`), uniquement quand aucun filtre
  catégorie/date n'est actif (`App.tsx` l. ~144). Le frontend n'appelle pas `/api/v1/categories`.
* `ExplorerFilters.tsx` (`<select>` alimenté par ces valeurs brutes), `App.tsx` (envoi de
  `category` en query string), `activityUtils.ts#matchesSearchQuery` (la catégorie fait partie du
  texte recherché).
* Affichage brut : `ActivityPreview.tsx`, `ActivityList.tsx`, `ActivityDetailModal.tsx`,
  `FavoritesPage.tsx`, `AdminPage.tsx`, `ExplorerMap.tsx` (food trucks, popup avec `category`).
* Types : `explorer/types.ts` (`category: string`, non nullable alors que la donnée peut être `null`).

### Mobile (`mobile/`)
* `api/categories.ts#listCategories` (`GET /categories`) exporté mais **aucun écran ne l'utilise**.
* `api/types.ts` (`Category {id,name,description}`, `category: string` sur activités/food trucks) ;
  `api/activities.ts` (transmission du paramètre `category`) ; `features/map/PreviewCard.tsx`
  (affichage brut, préfixe « Food truck · » pour distinguer visuellement les deux types de marqueurs
  — seule trace actuelle d'une différenciation de type dans l'UI).

### Documentation / maquette
* `LocalLife_home_mockup.html` : catégories fictives (Culture, Gastronomie, Musique, Sport, Sorties,
  Food trucks) — maquette statique.
* `docs/02_Architecture/GEO_SEARCH_CONTRACT.md`, `BOUNDING_BOX_SEARCH_CONTRACT.md`,
  `FOOD_TRUCK_CONTRACT.md`, `DATA_QUALITY_AUDIT.md` (§ problème n°7), `PROJECT_STATUS.md`
  (LL-4004, LL-5005, LL-7007), `ARCHITECTURE.md`, `SPRINT_1/2/4/10/11.md`.

## 5. Validateurs

| Validateur | Règle | Limite |
|---|---|---|
| `ActivityService#validateCategory` | chaîne fournie ⇒ non blanche | aucune liste fermée, aucune longueur max (colonne 255 → erreur SQL possible), `null` accepté |
| `NormalizationService#isValidCategory` | `null` ou non blanche | idem ; pas de catégorie ⇒ import accepté (aucun équivalent `PENDING`) |
| `FoodTruckService#validateCategory` | obligatoire, non blanche | idem |
| Frontend | `required` sur l'input | contournable (API directe) |

Aucun validateur n'implémente ni ne peut implémenter aujourd'hui le triptyque
catégorie/type/visibilité : ils ne connaissent que la chaîne `category`.

## 6. Inventaire des valeurs de catégorie

### 6.1 Valeurs identifiables dans le dépôt, qualifiées par le nouveau modèle

| Valeur | Occurrences | Source | Variantes / remarques | Qualification (§0) | Catégorie/type proposé (CAT-001) |
|---|---|---|---|---|---|
| `concert` | seed V3, tests (nombreux) | Seed, tests, OpenAgenda | variante `Concert` | candidate catégorie ou type | Catégorie MUSIQUE, ou type CONCERT sous MUSIQUE |
| `marché` | seed V3, tests (nombreux) | Seed, tests, OpenAgenda | `marche`, `Marché`, `marchés` ; recouvre marché de Noël, brocante | candidate catégorie ou type | Catégorie MARCHÉS, ou type sous une catégorie plus large |
| `food truck` | seed V3 (événement, ex. « Food Truck Festival ») | Seed | **à distinguer de `food_truck.category`** : ici c'est la catégorie d'une *activité* (rassemblement), pas le type de cuisine d'un commerce | candidate type | Type FOODTRUCK_EVENT sous GASTRONOMIE (OPT_IN à évaluer) |
| `exposition` | seed V3 | Seed | | candidate type | Type sous CULTURE |
| `cinéma` | seed V3 | Seed | `cinema` | candidate catégorie ou type | Catégorie ou type CINÉMA |
| `sport` | tests repository/intégration | Tests | | candidate catégorie | Catégorie SPORT |
| `loisir` | tests (`NonRegressionIntegrationTest`, `ActivityServiceTest`) | Tests | terme très générique | candidate à `PENDING` ou `OTHER` | Trop large pour être une catégorie utile telle quelle |
| `brocante`, `atelier` | `OpenAgendaCollectorTest` (mots-clés) | Tests OpenAgenda | | candidate type | Types sous MARCHÉS / ATELIERS |
| `noël` | 2ᵉ mot-clé de test (conservé en tag, pas en catégorie) | Tests OpenAgenda | | tag, pas une catégorie/type | — |
| `Category`, `Another Category` | `ActivityControllerTest` | Tests (placeholders) | valeurs non métier | hors périmètre | à remplacer en CAT-009 |
| `categorie-inexistante-xyz` | `ActivityControllerIntegrationTest` | Test filtre inconnu | comportement à revoir (§4, §7) | — | — |
| `null` | activités OpenAgenda sans mot-clé (**confirmé en réel**, LL-7003/LL-7007, ex. ids 114, 128, 133) ; contributions API sans catégorie | OpenAgenda / API | a déjà causé un bug frontend (corrigé) | candidate `PENDING` | classification par titre/description, sinon `PENDING` (nouveau modèle) plutôt que `OTHER` par défaut |

**Catégories trop fines / trop larges / inutilisables** (constat qualitatif, à confirmer par les
volumes réels §6.2) :

* **Trop larges / peu discriminantes** : `loisir` (ne dit rien de l'activité), un éventuel `divers`
  s'il apparaît en base — candidates `PENDING`/`OTHER`, pas des catégories principales.
* **Trop fines pour être une catégorie principale** : `brocante`, `atelier`, `dégustation` — ce sont
  des types plausibles sous une catégorie plus large (MARCHÉS, ATELIERS, GASTRONOMIE), pas des
  catégories principales au sens du nouveau modèle (cible ~8–12).
* **Sans correspondance claire avec un usage LocalLife** : aucune identifiée dans le dépôt à ce
  stade (aucune valeur « bizarre » trouvée hors tests/placeholders) ; ce point ne pourra être
  confirmé qu'avec les valeurs réelles (§6.2).
* **`food truck` (activité) vs `food_truck.category` (type de cuisine)** : collision de vocabulaire
  entre deux concepts différents — voir §7, point d'attention explicite pour CAT-001.

### 6.2 Valeurs réelles en base

**Extraction fournie par Alex le 29/09/2026** (requête « comptes par valeur brute et par source »,
1075 lignes, colonnes `source_type, category, nb`). Les autres requêtes (variantes, tags
`activity_tag`, `food_truck.category`, répartition par source détaillée) restent à exécuter — voir
§6.2 bis pour l'analyse et §6.2 ter pour ce qu'il reste à extraire.

Requêtes originales (pour mémoire / ré-exécution) :

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
-- (nécessite l'extension unaccent : CREATE EXTENSION IF NOT EXISTS unaccent;
--  à défaut, remplacer unaccent(...) par la colonne seule)
SELECT lower(unaccent(btrim(category))) AS normalise,
       array_agg(DISTINCT category)      AS variantes,
       COUNT(*)                          AS nb
FROM activity
WHERE category IS NOT NULL
GROUP BY 1
HAVING COUNT(DISTINCT category) > 1
ORDER BY nb DESC;

-- Activités sans catégorie
SELECT COUNT(*) FILTER (WHERE category IS NULL OR btrim(category) = '') AS sans_categorie,
       COUNT(*) AS total
FROM activity;

-- Mots-clés d'origine (candidats types/tags pour CAT-001/CAT-004) : top des tags OpenAgenda
SELECT lower(btrim(t.tag)) AS tag, COUNT(*) AS nb
FROM activity_tag t
GROUP BY 1 ORDER BY nb DESC LIMIT 200;

-- Food trucks : valeurs de "type de cuisine" (à ne pas confondre avec une catégorie d'activité)
SELECT category, COUNT(*) FROM food_truck GROUP BY 1 ORDER BY 2 DESC;

-- Répartition par source (volume par catégorie et par origine, pour juger DEFAULT vs OPT_IN)
SELECT s.type AS source_type, a.category, COUNT(*) AS nb
FROM activity a LEFT JOIN source s ON s.id = a.source_id
GROUP BY 1, 2 ORDER BY 1, nb DESC;
```

### 6.2 bis — Analyse de l'extraction réelle (résultat, 29/09/2026)

**Chiffres clés** (1075 lignes reçues, colonnes `source_type, category, nb`) :

| Indicateur | Valeur |
|---|---|
| Total d'activités couvertes par l'extraction | **47 338** |
| Activités **sans catégorie** (`category` NULL) | **41 696 — soit 88,1 %** |
| Activités avec une catégorie renseignée | 5 642 (11,9 %) |
| Valeurs distinctes après normalisation (casse/accents) | **959** |
| `source_type` distinct dans l'extraction | **`API` uniquement** — la requête groupe par `source_type` (`API`/`RSS`/`MANUAL`), pas par `source_name` : elle ne permet donc **pas** de distinguer plusieurs agendas de type `API` entre eux (voir limite ci-dessous) |

**Verdict principal — l'extraction invalide l'hypothèse de départ** : `activity.category` (premier
mot-clé OpenAgenda) n'est **pas** une donnée de catégorie exploitable telle quelle.

1. **88,1 % des activités n'ont aucune catégorie.** Toute classification basée uniquement sur ce
   champ ignorerait la quasi-totalité des données. C'est, à lui seul, l'argument principal pour que
   `PENDING` (nouveau modèle) soit le statut par défaut d'une grande partie du flux entrant, plutôt
   qu'un cas marginal.
2. **Fragmentation extrême sur les 11,9 % restants** : 959 valeurs distinctes pour 5 642 activités
   (soit 5,9 activités par valeur en moyenne). **61 % des valeurs (582/959) n'apparaissent qu'une
   seule fois.** Le top 10 des valeurs ne couvre que 34 % des activités catégorisées (4 % du total),
   et il faut 200 valeurs pour couvrir 83 % des activités catégorisées (10 % du total). Une
   normalisation casse/accents ne suffit donc pas à obtenir un référentiel exploitable : il faut un
   véritable mapping sémantique (CAT-001/CAT-004), pas une simple déduplication.
3. **Le contenu du champ n'est pas homogène avec une notion de « catégorie ».** Le premier mot-clé
   OpenAgenda capture indifféremment (classification heuristique par mots-clés, indicative,
   voir script d'extraction) :
   * du **vocabulaire emploi/formation** (`Recrutement`, `Tour de l emploi`, `1 jeune  1 solution`,
     `Aides à l'emploi`, `Découverte secteur / métier`, `#TousMobilisés`, `#TousEnImmersion`,
     `QPV Ruralité...`) : ~1 240 activités sur des valeurs de ce type — signe qu'au moins une des
     sources importées est un **agenda d'événements emploi/insertion** (type France Travail /
     maison de l'emploi), un domaine que ni l'ancien référentiel ni le nouveau modèle fonctionnel
     n'anticipaient explicitement ;
   * du **vocabulaire culture/spectacle** exploitable (`théâtre`, `concert`, `danse`, `exposition`,
     `cirque`, `musique`, `opéra`, `conte`, `marionnettes`, `cinéma`) : ~960 activités — c'est le
     socle le plus proche de ce qu'attendait le référentiel initial ;
   * du **vocabulaire patrimoine** (`patrimoine`, `visite (guidée/commentée)`, `archéologie`,
     `architecture`, `Journées (Européennes) du Patrimoine`) : ~320 activités ;
   * des **noms de lieux/salles** (`Cameo Commanderie`, `Cameo Saint Sébastien`, `Le Star`,
     `Discobole`, `Le New Vox`, `Cines Palace`, `Tinqueux`, `La Comète`), des **acronymes de clubs
     sportifs** (`MSB`, `MHB`), des **noms de personnes/auteurs/œuvres** (`Isabelle Lafon`,
     `Shakespeare`, `Macbett`, `Ernest Pignon-Ernest`, `naïssamjalal`) et des **codes internes**
     (`NDMBX26`) — regroupés en « autre/non classable » (~2 890 activités, 717 valeurs distinctes,
     la majorité de la longue traîne). Ces valeurs ne sont **pas des catégories au sens du nouveau
     modèle**, même en tant que type : ce sont des mots-clés d'identification (lieu, programmation,
     artiste), à conserver en tags, jamais à mapper vers une catégorie/type.
4. **Conséquence directe pour CAT-001/CAT-004** : le mapping « catégorie source → catégorie
   LocalLife » ne peut pas se limiter à `activity.category` (premier mot-clé). Il devra s'appuyer
   sur l'ensemble des mots-clés conservés dans `activity_tag` (tous les `keywords.fr`, pas
   seulement le premier), éventuellement combinés au titre/à la description, pour espérer classifier
   une part significative des 88 % d'activités actuellement sans catégorie. `NormalizationService`
   ne récupère aujourd'hui que `keywords.fr[0]` comme `category`

 — c'est cette troncature à un seul
   mot-clé, plus que l'absence de mot-clé, qui explique une partie du volume de `null`.
5. **Domaine emploi/insertion à traiter explicitement en CAT-001** : ce n'est ni du bruit, ni un cas
   isolé (~1 240 activités, largement plus que `concert` ou `marché`) — il faut décider s'il s'agit
   d'une catégorie principale à part entière (ex. `EMPLOI_FORMATION`), d'un type sous une catégorie
   plus large (« Vie locale »), ou d'un contenu `PENDING`/`OPT_IN` par défaut le temps d'une
   décision produit.

**Limite de cette analyse** : la répartition « bucket » ci-dessus est une classification
**heuristique par mots-clés**, faite pour cet audit, pas un mapping validé. Elle sert à qualifier
l'ampleur des familles, pas à préjuger du référentiel final de CAT-001.

**Top 200 valeurs normalisées** (regroupement casse/accents, triées par volume) : voir
`docs/CATEGORIES_TOP200.md`, généré à partir de l'extraction fournie et joint à cette PR pour ne pas
alourdir ce document.

### 6.2 ter — Ce qu'il reste à extraire

| Extraction | Statut |
|---|---|
| `activity.category` par valeur et `source_type` | ✅ Fournie (§6.2 bis) |
| `activity.category` par valeur **et `source_name`** (pour distinguer les agendas `API` entre eux, notamment isoler l'agenda emploi des agendas culturels) | ❌ À fournir — la colonne `source_type` seule ne le permet pas |
| Top des tags `activity_tag` (tous les mots-clés, pas seulement le premier) | ❌ À fournir — nécessaire pour évaluer le potentiel de classification au-delà du premier mot-clé (§6.2 bis point 4) |
| `food_truck.category` | ❌ À fournir |
| Variantes casse/accents (`unaccent`) | Reconstituée par normalisation côté audit (§6.2 bis) à partir des 1075 lignes ; la requête SQL originale reste utile pour une ré-extraction future |

### 6.3 Répartition par source (constat réel, remplace l'« attendu » de la version précédente)

| Source | Production de catégorie | Constat réel (§6.2 bis) |
|---|---|---|
| Import (source(s) `type = API`) | 1er mot-clé `keywords.fr` brut, sinon `null` | 88,1 % `null` ; 959 valeurs distinctes pour les 11,9 % restants ; mélange hétérogène (emploi/formation, culture, patrimoine, noms de lieux/personnes) ; **au moins deux agendas de nature différente sont probablement mélangés** sous le même `source_type = API` (culturel généraliste + emploi/insertion), à confirmer par `source_name` (§6.2 ter) |
| Manuelle (source réservée `MANUAL`) | Saisie libre du contributeur (statut `PENDING` = modération, pas classification) | Aucune ligne `MANUAL` dans l'extraction reçue : soit aucune contribution manuelle n'existe encore en base, soit la requête n'en a pas retourné — à confirmer |
| Seed / tests | 5 valeurs V3 | `concert`, `marché`, `food truck`, `exposition`, `cinéma` — non distinguable dans cette extraction (pas de filtre par source précis) |
| Food trucks | Type de cuisine, hors `activity.category` | Non couvert par cette extraction (table séparée, §6.2 ter) |

## 7. Cas Foodtruck — analyse demandée par le nouveau modèle

Le sprint réécrit tranche déjà la question d'architecture (« aucun système spécifique Foodtruck ne
doit être créé », §9 du sprint) : `FOOD_TRUCK_CONTRACT.md` maintient `FoodTruck` comme module séparé
d'`Activity` (pas d'événement daté), et le modèle fonctionnel traite le foodtruck comme un **type**
sous une catégorie (« Gastronomie / Foodtruck / OPT_IN », §5 du modèle fonctionnel). Cet audit
documente les faits qui alimentent cette classification, sans la retrancher :

| Axe | Constat (code de `main`) |
|---|---|
| Modèle | `FoodTruck(id, name, description, latitude, longitude, category, contact, status)`. `category` = **type de cuisine** (« burger », « tacos »), pas une catégorie d'événement. |
| Producteurs | Uniquement `POST /api/v1/foodtrucks` (utilisateur authentifié). Aucun import, aucun collecteur, aucun seed. |
| Consommateurs | Affichage seul (popup web, carte mobile avec préfixe « Food truck · »). **Aucun filtre, aucune recherche** ne s'appuie dessus ; le paramètre `category` de `/nearby`/`/within-bounds` ne porte que sur `activity`. |
| Valeurs | Exemples de contrat : `burger`, `tacos`, `street food asiatique`. Valeurs réelles à extraire (§6.2). |
| Usage fonctionnel | Décrire ce que vend le commerçant — répond à « que vend-il ? », pas à « qu'est-ce que je peux faire ? ». |

**Lecture à travers le nouveau modèle** :

* Le concept d'**acteur local mobile** (`CATEGORIES_FUNCTIONAL_MODEL.md` §10.1) correspond
  exactement au commerçant food truck actuel, mais **n'est pas implémenté** : la fiche `FoodTruck`
  actuelle est une fiche statique (pas d'occurrences, pas de calendrier de présence), alors que le
  modèle fonctionnel prévoit « où et quand suis-je présent ? » comme besoin principal de cette
  famille. Écart à noter pour un ticket futur (hors CAT-001, cf. §22 du périmètre).
* Un **événement** « rassemblement de food trucks » (ex. seed « Food Truck Festival ») est une
  *activité* au sens `Activity`, avec sa propre catégorie/type (candidat : type FOODTRUCK_EVENT sous
  GASTRONOMIE) — **à ne pas confondre** avec `FoodTruck.category` (type de cuisine du commerçant).
  Cette collision de vocabulaire (le mot « food truck » désigne deux choses différentes dans le
  dépôt) est un point d'attention explicite pour CAT-001.
* Le nouveau modèle recommande la politique `OPT_IN` pour foodtruck/restaurant/bar : cela n'est pas
  encore mesurable (aucune donnée de volume), mais c'est cohérent avec le constat CAT-000 selon
  lequel foodtruck n'a jamais été consommé comme critère de filtre — l'ajouter au flux général
  n'aurait modifié aucun comportement observable jusqu'ici.

**Conclusion de l'audit (factuelle, pas une décision CAT-001)** : `FoodTruck.category` reste, dans le
code actuel, une donnée de type de cuisine hors du système de catégories d'activités ; son
rattachement éventuel comme **type** sous une catégorie GASTRONOMIE, avec politique `OPT_IN`, est une
option cohérente avec le modèle fonctionnel et avec les faits ci-dessus, mais reste à formaliser en
CAT-001 (référentiel) et CAT-002 (modèle de données) — CAT-000 ne tranche pas.

## 8. Problèmes constatés

1. **Deux systèmes de catégories** : table `category` vide et inutilisée, `activity.category` libre.
2. **Aucune normalisation** : `Concert`/`concert`/`concerts` = trois valeurs, filtre à égalité stricte.
3. **OpenAgenda impose sa taxonomie** : le 1er mot-clé (ordre arbitraire, éditeur-dépendant) devient la
   catégorie ; les mots-clés suivants sont perdus comme catégorie (conservés en tags seulement).
4. **`null` massif** sur les imports sans mot-clé ; `null` accepté par tous les validateurs, sans
   équivalent d'un état « classification insuffisante » distinct du statut de modération.
5. **Saisie manuelle libre** (API et formulaire) ; aucune borne de longueur (colonne 255).
6. **Filtres frontend dérivés des données affichées** : ensemble variable selon la vue, valeurs brutes
   affichées, dépend des activités chargées.
7. **Filtre backend fragile** : égalité exacte, catégorie inconnue → liste vide (aucune erreur) — à
   confronter au nouveau modèle en CAT-005/CAT-006 (pas de notion de `PENDING` pour les résultats de
   filtre aujourd'hui).
8. **Ré-import écrasant** : `category` est réécrite à chaque import.
9. **Aucune notion de type/sous-type ni de politique de visibilité** dans le code actuel : tout ce que
   demande le nouveau modèle fonctionnel (catégorie + type + `DEFAULT`/`OPT_IN`/`PENDING`) est à
   construire, pas à adapter à partir d'un existant partiel.
10. **`activity.status` (PENDING/PUBLISHED/REJECTED)** risque d'être confondu avec le `PENDING` de
    visibilité du nouveau modèle : ce sont deux notions différentes (modération vs classification),
    à nommer distinctement dans CAT-001/CAT-002 pour éviter toute ambiguïté de vocabulaire.
11. **Collision de vocabulaire « food truck »** (§7) entre type d'événement et type de cuisine.
12. **Types TypeScript incohérents** : `category: string` alors que `null` est possible.
13. **Tests et fixtures** : valeurs en dur (`concert`, `marché`, `sport`, `loisir`, `Category`)
    partout ; `CategoryControllerTest` ne teste que des mocks.
14. **Endpoint `/api/v1/categories`** : entité brute exposée, sans DTO, sans tri, sans `active`, sans
    notion de type ni de visibilité.

## 9. Risques de migration (pour CAT-001 et suivants)

* **Volume et diversité inconnus** tant que les requêtes §6.2 ne sont pas exécutées : le mapping
  ancienne valeur → catégorie/type officiel ne peut être validé qu'avec ces comptes, notamment pour
  distinguer ce qui doit devenir catégorie principale de ce qui doit devenir type.
* **Décision `DEFAULT`/`OPT_IN` non mesurable sans volumes** : le modèle fonctionnel fixe déjà
  `OPT_IN` pour foodtruck/restaurant/bar (référence), mais les autres arbitrages dépendent de la
  fréquence réelle par catégorie.
* **Perte d'information** : `activity.category` (1er mot-clé) est la seule trace de la catégorie
  d'origine pour certaines activités ; à conserver (tags ou colonne d'archive) avant toute
  suppression, comme l'exige le nouveau modèle (§19 : ne pas détruire les données source).
* **Ré-import OpenAgenda** : tant que le collecteur écrit le 1er mot-clé, un ré-import annule tout
  reclassement.
* **Compatibilité API** : le champ `category` (chaîne) est exposé par 3 DTO, consommé par le web et le
  mobile ; passage à catégorie + type (+ visibilité) = évolution de contrat à documenter (CAT-002).
* **Confusion `PENDING` de modération / `PENDING` de classification** (§8, point 10) : à lever
  explicitement dans le vocabulaire de CAT-001/CAT-002, faute de quoi le pipeline d'import
  (CAT-006) risque de mélanger les deux statuts.
* **Migrations Flyway** : `V31` est la dernière ; toute nouvelle migration doit suivre. Le seed V3
  existe déjà en base : il faudra reclasser ses 5 valeurs.
* **Requêtes SQL natives** : les deux requêtes `ActivityRepository` filtrent sur la chaîne ; elles
  devront être réécrites (jointure catégorie/type) avec un plan d'index (CAT-002/CAT-003).
* **Tests** : ~15 fichiers de test contiennent des valeurs en dur ; la refonte touche
  `ActivityRepositoryIntegrationTest`, `ActivityControllerIntegrationTest`, `NonRegressionIntegrationTest`,
  `ImportServiceTest`, `NormalizationServiceTest`, `OpenAgendaCollectorTest`.
* **Food trucks** : le nouveau modèle situe le foodtruck comme un type d'acteur/activité, mais le
  module `FoodTruck` actuel n'est ni un acteur local ni une activité au sens du modèle — un ticket
  hors CAT-001 devra traiter ce rapprochement (§7).

## 10. Recommandations pour CAT-001

1. **Concevoir le référentiel pour un flux majoritairement `PENDING` au départ** : avec 88,1 % des
   activités sans catégorie source, `PENDING` ne peut pas être traité comme un cas résiduel — c'est
   le statut par défaut tant qu'une classification (par tags/titre/description) n'a pas été mise en
   œuvre (CAT-004/CAT-006). Fixer un objectif chiffré de réduction du volume `PENDING` serait
   prématuré sans avoir testé un premier mapping.
2. **Ne pas fonder le mapping source → LocalLife sur `activity.category` seul** : ce champ ne
   capture que le premier mot-clé OpenAgenda et mélange noms de lieux, d'artistes, d'œuvres et
   acronymes avec de vrais indices de catégorie (§6.2 bis point 3). CAT-004 devra s'appuyer sur
   l'ensemble des tags (`activity_tag`) — non encore extraits (§6.2 ter) — voire sur titre/
   description, plutôt que sur ce seul champ.
3. **Statuer explicitement sur le domaine emploi/formation** (~1 240 activités identifiées, plus
   volumineux que `concert` ou `marché` à eux seuls) : catégorie principale dédiée, type sous une
   catégorie « Vie locale/institutionnel », ou `OPT_IN` par défaut en attendant une décision produit.
   Ne pas le laisser retomber implicitement dans `OTHER`/`PENDING` sans décision explicite (§6.2 bis
   point 5).
4. Obtenir les extractions manquantes du §6.2 ter (`source_name`, tags, `food_truck.category`)
   **avant** de figer la liste des catégories principales — en particulier `source_name` pour
   vérifier si le domaine emploi/formation provient d'un agenda distinct (pouvant justifier une
   politique de visibilité différenciée par source) plutôt que d'un mélange dans un agenda généraliste.
5. Construire le référentiel en respectant explicitement la distinction catégorie/type/visibilité —
   ne pas reproduire une liste plate `code/name/description`.
6. Traiter nommément les valeurs ambiguës identifiées en §6.1/§6.2 bis (`loisir`, `food truck` en
   tant qu'activité vs type de cuisine, domaine emploi/formation, noms de lieux/personnes conservés
   en tags uniquement) dans la section « cas ambigus » du référentiel.
7. Statuer explicitement sur le rattachement de `FoodTruck.category` (type GASTRONOMIE/OPT_IN
   proposé en §7) sans créer de référentiel séparé, sauf justification nouvelle.
8. Nommer distinctement le `PENDING` de classification (nouveau) et le `PENDING` de modération
   (existant sur `Activity.status`) pour éviter toute confusion dans CAT-002 et CAT-006.
9. Définir la place de `PENDING`/`OTHER` pour les filtres de lecture avec une catégorie/type
   inconnu (§4, §8 point 7) — actuellement liste vide (LL-4004), à confronter au nouveau modèle en
   CAT-005/CAT-006.
10. Décider de la conservation de la valeur d'origine (tags OpenAgenda déjà conservés ; prévoir une
    trace équivalente pour les contributions manuelles avant reclassement).

## 11. Critères d'acceptation CAT-000 (repris du sprint réécrit)

* [x] Catégories réelles inventoriées — dépôt (§6.1) **et base réelle** (§6.2 bis, 959 valeurs
  distinctes sur 47 338 activités)
* [x] Types identifiés (candidats) — §6.1 et confirmés/enrichis par §6.2 bis (ex. domaine
  emploi/formation, absent de l'hypothèse initiale)
* [x] Fréquence : mesurée (§6.2 bis — distribution complète, top 200 dans `CATEGORIES_TOP200.md`)
* [x] Variantes orthographiques et synonymes documentés (§6.1 et §6.2 bis, normalisation
  casse/accents appliquée sur les 1075 lignes reçues)
* [x] Catégories trop fines / trop larges / inutilisables identifiées, **confirmées par les
  volumes** : 61 % des valeurs n'apparaissent qu'une fois (§6.2 bis point 2) ; noms de lieux,
  d'artistes, acronymes largement majoritaires dans la longue traîne
* [x] Catégories sans correspondance claire avec une catégorie/type LocalLife identifiées : ~2 890
  activités sur des valeurs « autre/non classable » (lieux, personnes, codes internes — §6.2 bis
  point 3)
* [x] Catégories spécifiques à une source identifiées : domaine emploi/formation (~1 240 activités,
  §6.2 bis point 5), vraisemblablement un agenda dédié, à confirmer par `source_name` (§6.2 ter)
* [x] Volume d'événements par catégorie : mesuré (§6.2 bis, `CATEGORIES_TOP200.md`)
* [x] Exemples représentatifs fournis (dépôt : seed/tests ; base réelle : top 200)
* [x] Aucune suppression arbitraire effectuée
* [x] Les données/le document servent de base à CAT-001

**Restant avant de considérer CAT-000 totalement clos** : extractions complémentaires du §6.2 ter
(`source_name`, tags `activity_tag`, `food_truck.category`).
