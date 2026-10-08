# CATEGORY_MIGRATION_MAP — Audit de l'existant (CAT-000)

> Sprint CATEG, ticket CAT-000 (« Référentiel canonique des activités V1 », sprint réécrit le
> 2026-09-30). Livrable demandé par le sprint : le présent document. **Aucun code, migration,
> modèle, Seeder, frontend ou donnée n'est modifié par ce document** — audit uniquement, conformément
> à la règle du ticket (« ne pas modifier massivement le projet avant d'avoir compris l'existant »).
>
> Base analysée : `main` @ `18cc61c`.

## 0. Cadrage par rapport au modèle du sprint (§2 et §3 de `SPRINT_CATEG.md`)

Le sprint fixe lui-même, dès son introduction, quatre dimensions (`CATEGORY`, `ACTIVITY_TYPE`,
`AUDIENCE`, `ORGANIZER_TYPE`), une règle d'identification (`ID` technique / `CODE` métier stable /
`LABEL` traduit), et **la liste des 9 catégories V1** (`CULTURE`, `GASTRONOMY`, `MARKETS`,
`COMMERCE`, `SPORT`, `LEISURE`, `NATURE`, `TOURISM`, `CITIZEN`) — cette liste n'est donc plus un
livrable de CAT-001, elle est une donnée d'entrée de ce sprint. Le rôle de CAT-000 est de confronter
l'existant à ces quatre dimensions et à cette liste, pas de proposer une autre liste.

**Contradiction identifiée dès l'audit** (règle du sprint : « identifier la contradiction, ne pas
décider arbitrairement, appliquer le modèle, documenter la divergence ») : les données réelles
(§4, extraction du 29/09/2026) contiennent un volume significatif d'activités à vocation
emploi/formation/insertion (~1 240 sur 5 642 activités catégorisées, soit le 2ᵉ volume après
culture/spectacle) qui ne correspond clairement à **aucune** des 9 catégories V1. Ce point est
documenté en §4 et §6 et n'est **pas résolu ici** : CAT-000 le signale, CAT-001 devra trancher
(rattachement à `CITIZEN`, à `LEISURE`, ou extension de la liste V1 — hors périmètre CAT-000).

## 1. Situation actuelle

Il existe aujourd'hui **un seul système de catégorisation réellement utilisé**, et il ne correspond
à aucune des quatre dimensions attendues (`CATEGORY`/`ACTIVITY_TYPE`/`AUDIENCE`/`ORGANIZER_TYPE`) ni
à la règle `ID`/`CODE`/`LABEL` :

| Dimension attendue (sprint) | Existe en base/code aujourd'hui ? | Constat |
|---|---|---|
| `CATEGORY` | **Partiellement** | `activity.category` : `VARCHAR(255)` libre, nullable — aucun `CODE` stable, pas de `LABEL` traduit (une seule langue, la chaîne brute). Table `category` (`id`, `name`, `description`) existe mais est **vide et orpheline** (aucune FK depuis `activity`, aucune donnée insérée, aucun code applicatif ne l'alimente hors sa propre lecture). |
| `ACTIVITY_TYPE` | **Absent** | Aucun champ, aucune table. Les mots-clés OpenAgenda au-delà du premier sont conservés dans `activity_tag` (libre, non structuré) — c'est la seule donnée qui s'approche d'un type/sous-type, mais sans modélisation. |
| `AUDIENCE` | **Absent** | Aucun champ, aucune table, aucune mention dans le code métier (recherche effectuée sur tout `backend/src/main/java`, aucune occurrence). |
| `ORGANIZER_TYPE` | **Absent** | Aucun champ, aucune table. L'entité `Source` (`source_type` : `API`/`RSS`/`MANUAL`) qualifie le **canal d'import**, pas le type d'organisateur de l'activité — à ne pas confondre (risque listé en §7). |
| Règle `ID`/`CODE`/`LABEL` | **Non respectée** | `Category.id` est le seul identifiant (technique, auto-incrémenté) ; il n'existe ni `code` métier stable ni `label` traduit distinct. `activity.category` fait office à la fois de « code » et de « label » (la chaîne brute sert aux deux usages, filtre et affichage), ce qui est précisément ce que le sprint interdit (§3 du sprint : « le LABEL ne doit jamais servir d'identifiant »). |

**Conséquence pour CAT-001/CAT-002** : il ne s'agit pas d'adapter un modèle existant partiel, mais
de construire les quatre dimensions depuis une base quasi vide — seule `CATEGORY` a un équivalent
fonctionnel (très imparfait) aujourd'hui ; `ACTIVITY_TYPE`, `AUDIENCE` et `ORGANIZER_TYPE` sont du
terrain neuf.

### 1.1 Flux de création actuel (rappel du fonctionnement)

```
Contribution manuelle : ContributionModal (input texte libre, required)
   → POST /api/v1/activities {category}  → ActivityService.createActivity
   → validateCategory (non blank si fournie) → INSERT activity.category (statut PENDING = modération)

OpenAgenda : keywords.fr[0]  → OpenAgendaCollector.firstKeyword
   → CollectedActivity.category → NormalizationService.normalize (isValidCategory : non blank)
   → ImportService.withSourceAndKey → save (statut PUBLISHED)
   (keywords.fr complets → CollectedActivity.tags → activity_tag, non exploités comme type)

Seed : V3__insert_demo_activities.sql (5 valeurs minuscules, jamais rejouée en historique réel)

FoodTruck : POST /api/v1/foodtrucks {category obligatoire, "type de cuisine"} → statut PUBLISHED direct
   (aucune classification, aucun lien avec activity.category ni avec la table category)
```

Il n'existe **aucun Seeder au sens d'une classe/outil dédié** : le seul mécanisme assimilable est la
migration Flyway `V3__insert_demo_activities.sql` (5 lignes de données de démonstration), à ne pas
confondre avec un composant réutilisable — point à clarifier si le sprint attend un vrai Seeder
(§7, risque).

## 2. Endroits concernés (inventaire des occurrences)

### 2.1 Producteurs (écritures de `activity.category` / `food_truck.category`)

**Producteurs identifiés :**

| # | Producteur | Fichier | Valeur écrite | Contrôle | Source unique ? |
|---|---|---|---|---|---|
| P1 | Import OpenAgenda | `OpenAgendaCollector#firstKeyword` (l. ~287, 392) | 1er mot-clé français brut, ou `null` si aucun | aucune normalisation (casse, accents, pluriel) | Oui — spécifique OpenAgenda |
| P2 | Normalisation import | `NormalizationService#normalize` (l. 125), `isValidCategory` (l. 383/408) | recopie | rejette seulement la chaîne blanche (`null` accepté) | — |
| P3 | Persistance import | `ImportService#withSourceAndKey` / `withStatus` (l. 424/434) | recopie | — | — |
| P4 | Contribution manuelle API | `ActivityController#createActivity` → `ActivityService#createActivity` / `validateCategory` (l. 733, 786) | chaîne libre de l'utilisateur | non blank si fournie ; `null` accepté côté API | Oui — spécifique à la saisie manuelle |
| P5 | Rattachement source supprimée | `SourceService#withSourceId` (l. 171) | recopie (ne change pas la valeur) | — | — |
| P6 | Seed démo | `V3__insert_demo_activities.sql` | `concert`, `marché`, `food truck`, `exposition`, `cinéma` | — | — |
| P7 | Food trucks | `FoodTruckController` / `FoodTruckService#validateCategory` | chaîne libre (« type de cuisine », obligatoire) | non blank | Oui — spécifique au module `foodtruck`, sémantique différente (§6 point 2) |
| P8 | Frontend (formulaire) | `ContributionModal.tsx` (`<input type="text" required>`), état `category` de `App.tsx` | saisie libre | `required` HTML | — |

**Catégories spécifiques à une source** : P1 (OpenAgenda, vocabulaire de l'éditeur, hors contrôle
LocalLife) et P4/P8 (saisie manuelle, vocabulaire de l'utilisateur) sont les deux seules sources
réelles de valeurs ; elles ne partagent aujourd'hui **aucun vocabulaire imposé** — un mapping
« catégorie source → catégorie LocalLife » (exigé par le modèle du sprint, §5) n'existe donc pas
pour P1, et P4/P8 n'ont aucune liste fermée à respecter. Autres sources : aucune (pas d'autre
collecteur : `collectors/` ne contient qu'un README ; pas d'import CSV, pas de script de seed hors
V3, pas de fixtures JSON/SQL de catégories).

### 2.2 Consommateurs (lectures)

**Consommateurs identifiés :**

### Backend
* `ActivityRepository` (2 requêtes : `findWithinRadius`, `findWithinBounds`) :
  `a.category = ANY(string_to_array(:categoriesCsv, ','))` — **égalité exacte, sensible à la casse
  et aux accents**.
* `ActivityService#findNearby` / `#findWithinBounds` / `normalizeCategories` : trim + retrait des
  valeurs vides ; une catégorie inconnue renvoie une liste vide (décision LL-4004 — **à confronter
  au nouveau modèle** : le sprint réécrit ne tranche pas explicitement ce point comme le faisait la
  version précédente ; voir §6 point 6 sur l'ambiguïté `PENDING`, à traiter en CAT-005/CAT-006).
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

### 2.3 Validateurs

**Validateurs identifiés :**

| Validateur | Règle | Limite |
|---|---|---|
| `ActivityService#validateCategory` | chaîne fournie ⇒ non blanche | aucune liste fermée, aucune longueur max (colonne 255 → erreur SQL possible), `null` accepté |
| `NormalizationService#isValidCategory` | `null` ou non blanche | idem ; pas de catégorie ⇒ import accepté (aucun équivalent `PENDING`) |
| `FoodTruckService#validateCategory` | obligatoire, non blanche | idem |
| Frontend | `required` sur l'input | contournable (API directe) |

Aucun validateur n'implémente ni ne peut implémenter aujourd'hui le triptyque
catégorie/type/visibilité : ils ne connaissent que la chaîne `category`.

### 2.4 Seeder / fixtures / tests

| Élément | Fichier | Constat |
|---|---|---|
| Seed historique | `backend/src/main/resources/db/migration/V3__insert_demo_activities.sql` | 5 activités, valeurs `concert`, `marché`, `food truck`, `exposition`, `cinéma` ; dernière migration : `V31` |
| Tests avec valeurs en dur | ~15 fichiers (`ActivityRepositoryIntegrationTest`, `ActivityControllerIntegrationTest`, `ActivityControllerTest`, `NonRegressionIntegrationTest`, `ImportServiceTest`, `NormalizationServiceTest`, `OpenAgendaCollectorTest`, `CategoryControllerTest`, `FoodTruckServiceTest`...) | Chaînes `concert`, `marché`, `sport`, `loisir`, `Category`/`Another Category` (placeholders non métier), mots-clés OpenAgenda de test (`brocante`, `atelier`, `noël`) |
| `CategoryControllerTest` | `backend/src/test/.../category/` | Teste uniquement des mocks (`Concert`, `Marché`) ; aucune donnée réelle, aucune cohérence vérifiée avec `activity.category` |

### 2.5 Publics (`AUDIENCE`) et organisateurs (`ORGANIZER_TYPE`)

Recherche explicite menée sur l'ensemble de `backend/src/main/java`, `frontend/src`, `mobile/src` :
**aucune occurrence** de notion de public cible (audience) ou de type d'organisateur en tant que
donnée métier structurée. Les seules correspondances textuelles trouvées (« organiser », contexte
`AuthService`) sont des faux positifs sans rapport. Ces deux dimensions sont donc **entièrement à
créer** (CAT-001/CAT-002), sans donnée existante à migrer — ce n'est pas une migration mais une
création ex nihilo, point à distinguer explicitement dans les tickets suivants.

## 3. Anciennes valeurs (inventaire qualifié)

**Valeurs identifiables dans le dépôt** (qualifiées selon les 4 dimensions du sprint ; la colonne « proposition CAT-001 » de la version précédente est obsolète, remplacée par la correspondance V1 du §5) :

| Valeur | Occurrences | Source | Variantes / remarques |
|---|---|---|---|
| `concert` | seed V3, tests (nombreux) | Seed, tests, OpenAgenda | variante `Concert` |
| `marché` | seed V3, tests (nombreux) | Seed, tests, OpenAgenda | `marche`, `Marché`, `marchés` ; recouvre marché de Noël, brocante |
| `food truck` | seed V3 (événement, ex. « Food Truck Festival ») | Seed | **à distinguer de `food_truck.category`** : ici c'est la catégorie d'une *activité* (rassemblement), pas le type de cuisine d'un commerce — voir §6 point 2 |
| `exposition` | seed V3 | Seed | |
| `cinéma` | seed V3 | Seed | `cinema` |
| `sport` | tests repository/intégration | Tests | |
| `loisir` | tests (`NonRegressionIntegrationTest`, `ActivityServiceTest`) | Tests | terme très générique, trop vague pour un mapping fiable (§6 point 5) |
| `brocante`, `atelier` | `OpenAgendaCollectorTest` (mots-clés) | Tests OpenAgenda | |
| `noël` | 2ᵉ mot-clé de test (conservé en tag, pas en catégorie) | Tests OpenAgenda | reste un tag, jamais une catégorie |
| `Category`, `Another Category` | `ActivityControllerTest` | Tests (placeholders) | valeurs non métier, à remplacer lors de la refonte des tests |
| `categorie-inexistante-xyz` | `ActivityControllerIntegrationTest` | Test filtre inconnu | comportement du filtre à revoir (§7) |
| `null` | activités OpenAgenda sans mot-clé (**confirmé en réel**, LL-7003/LL-7007, ex. ids 114, 128, 133) ; contributions API sans catégorie | OpenAgenda / API | a déjà causé un bug frontend (corrigé) ; 88,1 % du volume réel, voir §4 |

Les correspondances de ces valeurs vers les 9 catégories V1 du sprint sont proposées au §5 ; elles
ne figurent plus dans ce tableau pour éviter la duplication.

**Catégories trop fines / trop larges / inutilisables** (constat qualitatif, à confirmer par les
volumes réels, cf. §4) :

* **Trop larges / peu discriminantes** : `loisir` (ne dit rien de l'activité), un éventuel `divers`
  s'il apparaît en base.
* **Trop fines pour être l'une des 9 catégories V1** : `brocante`, `atelier`, `dégustation` — ce sont
  des candidats `ACTIVITY_TYPE` sous une catégorie plus large (`MARKETS`, `GASTRONOMY`), pas des
  valeurs de `CATEGORY` à elles seules.
* **Sans correspondance claire avec une des 9 catégories V1** : le domaine emploi/formation (§4, §6
  point 1) est le cas le plus net et le plus volumineux ; aucune autre valeur « bizarre » n'a été
  identifiée dans le dépôt à ce stade hors tests/placeholders.
* **`food truck` (activité) vs `food_truck.category` (type de cuisine)** : collision de vocabulaire
  entre deux concepts différents — voir §6 point 2, point d'attention explicite pour CAT-001.

## 4. Données réelles en base — extraction du 29/09/2026

**Résultat de l'extraction :**

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

## 4.1 Ce qu'il reste à extraire

Reste à extraire pour compléter CAT-000/alimenter CAT-001 :

| Extraction | Statut |
|---|---|
| `activity.category` par valeur et `source_type` | ✅ Fournie (§6.2 bis) |
| `activity.category` par valeur **et `source_name`** (pour distinguer les agendas `API` entre eux, notamment isoler l'agenda emploi des agendas culturels) | ❌ À fournir — la colonne `source_type` seule ne le permet pas |
| Top des tags `activity_tag` (tous les mots-clés, pas seulement le premier) | ❌ À fournir — nécessaire pour évaluer le potentiel de classification au-delà du premier mot-clé (§6.2 bis point 4) |
| `food_truck.category` | ❌ À fournir |
| Variantes casse/accents (`unaccent`) | Reconstituée par normalisation côté audit (§6.2 bis) à partir des 1075 lignes ; la requête SQL originale reste utile pour une ré-extraction future |

## 5. Correspondances possibles (anciennes valeurs → catégories V1 du sprint)

Les 9 catégories V1 sont fixées par le sprint (`CULTURE`, `GASTRONOMY`, `MARKETS`, `COMMERCE`,
`SPORT`, `LEISURE`, `NATURE`, `TOURISM`, `CITIZEN`). Ce tableau propose des correspondances
**indicatives**, à valider en CAT-001 — CAT-000 ne tranche pas, il documente les candidats et les
cas sans correspondance claire.

| Famille de valeurs observées (dépôt + extraction réelle) | Volume indicatif | Correspondance V1 candidate | Confiance |
|---|---|---|---|
| théâtre, spectacle, danse, cirque, opéra, marionnettes, conte, magie | ~390 | `CULTURE` | Haute |
| exposition, musée, patrimoine, visite (guidée/commentée), architecture, archéologie, conférence, lecture | ~213 | `CULTURE` | Haute |
| concert, musique, jazz | ~70+ | `CULTURE` (ou `LEISURE` selon contexte — à trancher) | Moyenne |
| cinéma, projection, film | faible en direct (7) | `CULTURE` | Haute |
| marché, brocante, vide-grenier | faible en direct (9) mais largement sous-représenté par la fragmentation | `MARKETS` | Haute |
| food truck (événement, rassemblement), dégustation | faible en direct | `GASTRONOMY` | Haute |
| `FoodTruck.category` (type de cuisine du commerçant, hors `activity.category`) | — | `GASTRONOMY` comme catégorie, en tant qu'`ACTIVITY_TYPE`/attribut d'acteur plutôt que comme activité datée (§6 point 2) | Moyenne — dépend du traitement du module `FoodTruck` lui-même (hors périmètre code de CAT-000) |
| sport, acronymes de clubs (MSB, MHB...) | ~38 | `SPORT` | Haute |
| jeune public, très jeune public, famille, jeux | ~193 | `LEISURE` (aucune catégorie V1 « famille » dédiée — à confirmer en CAT-001) | Moyenne |
| balade nature, jardins, plein air (hors lieu uniquement) | peu représenté en direct | `NATURE` | Moyenne |
| visite touristique, découverte du territoire *(peu distingué de « patrimoine » dans les données actuelles)* | non isolable des données actuelles | `TOURISM` | Faible — chevauchement avec `CULTURE`/`NATURE` à clarifier |
| braderie commerçante, nocturne, animation commerciale | non observé distinctement dans l'échantillon | `COMMERCE` | Faible — aucune occurrence directe trouvée, catégorie à vérifier à l'usage |
| réunion publique, forum associatif, institutionnel, démocratie locale | non observé distinctement dans l'échantillon | `CITIZEN` | Faible — aucune occurrence directe trouvée |
| **Recrutement, emploi, formation, insertion, apprentissage, alternance (`#TousMobilisés`, « 1 jeune 1 solution »...)** | **~1 240 — 2ᵉ volume après culture** | **Aucune correspondance claire** | **Voir §6 — contradiction à trancher en CAT-001, pas en CAT-000** |
| `loisir` (valeur de test, très générique) | tests uniquement | Trop générique pour une correspondance fiable ; candidat à rester non classifié plutôt que forcé | — |
| Noms de lieux (« Cameo Commanderie », « Le Star »...), d'artistes/auteurs (« Isabelle Lafon », « Shakespeare »), acronymes, codes internes (« NDMBX26 ») | ~2 890, 717 valeurs distinctes | **Aucune** — ce ne sont pas des indices de catégorie mais des identifiants (lieu, programmation) | — relèvent de `ACTIVITY_TYPE`/tags à la rigueur, jamais de `CATEGORY` |
| `null` (absence de valeur) | **41 696 — 88,1 % du total** | Aucune | — nécessite une classification par d'autres signaux (tags complets, titre, description), hors mandat de CAT-000 |

## 6. Données ambiguës

1. **Domaine emploi/formation (~1 240 activités) sans catégorie V1 correspondante.** C'est la
   divergence la plus significative entre les données réelles et le modèle du sprint. Options non
   tranchées ici : rattacher à `CITIZEN` (vie institutionnelle/locale), à `LEISURE` (par défaut
   générique), ou proposer une 10ᵉ catégorie en déviation documentée du sprint (le sprint autorise
   explicitement cette voie — « documenter la divergence si une modification de documentation est
   nécessaire »). **Décision à prendre en CAT-001, pas ici.**
2. **`FoodTruck.category` (type de cuisine) vs activité « food truck » (rassemblement/événement)** :
   collision de vocabulaire déjà identifiée (deux concepts différents portent le même mot). À
   distinguer explicitement dans le mapping CAT-001 : la première relève potentiellement d'un
   `ACTIVITY_TYPE` ou d'un attribut du module `FoodTruck` (hors `Activity`), la seconde d'une
   `CATEGORY` = `GASTRONOMY` comme n'importe quelle activité.
3. **Frontière `MARKETS`/`COMMERCE`** : aucune donnée réelle ne permet aujourd'hui de distinguer un
   marché de producteurs/brocante (`MARKETS`) d'une animation commerciale portée par des commerçants
   établis (`COMMERCE`) — la distinction dépendra de l'organisateur (`ORGANIZER_TYPE`, dimension
   absente aujourd'hui, §2.5), pas de la seule nature de l'événement.
4. **`CULTURE` vs `TOURISM`** : les visites patrimoniales (« visite guidée », « patrimoine »)
   pourraient relever des deux catégories selon l'intention (culturelle vs touristique) ; les
   données actuelles ne portent aucun signal permettant de trancher automatiquement.
5. **Catégorie `loisir`** (valeur de test très générique, et probablement présente en formulation
   libre dans d'authentiques contributions manuelles non encore extraites) : terme trop vague pour
   un mapping fiable vers l'une des 9 catégories V1 — signal d'alerte plutôt qu'indice de
   classification.
6. **`PENDING` de modération (`Activity.status`) vs absence de classification** : le statut
   `PENDING` existant qualifie une activité en attente de publication (modération), pas une activité
   dont la `CATEGORY`/l'`ACTIVITY_TYPE` serait indéterminée. Le sprint réécrit ne nomme pas
   explicitement un état de classification manquante (contrairement à la version précédente du
   sprint, qui utilisait `PENDING` dans ce sens) — **ambiguïté terminologique à lever en CAT-001**
   pour ne pas mélanger les deux notions dans l'implémentation.

## 7. Risques de migration

* **Volume massif de `null` (88,1 %)** : toute migration qui ne s'appuie que sur `activity.category`
  laissera la quasi-totalité des activités sans `CATEGORY`. Une classification complémentaire par
  tags complets (`activity_tag`, non encore extraits dans leur totalité — §4.1) et/ou titre/
  description sera nécessaire, et constitue un risque de délai/complexité pour CAT-004.
* **Fragmentation extrême sur les valeurs non nulles** : 959 valeurs distinctes pour 5 642 activités
  catégorisées, 61 % des valeurs normalisées n'apparaissant qu'une fois — un mapping valeur-à-valeur
  exhaustif n'est pas réaliste ; il faudra des règles par mots-clés/motifs plutôt qu'une table de
  correspondance figée valeur par valeur.
* **Confusion `Source.type` (`API`/`RSS`/`MANUAL`, canal technique) et `ORGANIZER_TYPE`** (qui
  organise l'activité, dimension métier à créer) : ce sont deux notions différentes qui partagent un
  nom proche ; un risque de les fusionner par erreur existe si CAT-001/CAT-002 ne les nomment pas
  distinctement.
* **Confusion des deux `PENDING`** (modération vs classification manquante) — §6 point 6.
* **Absence de vrai Seeder** : si le sprint attend un composant Seeder réutilisable (pas seulement
  une migration Flyway ponctuelle comme `V3`), sa création est un travail de structuration nouveau,
  pas une migration de l'existant — à clarifier avant de lancer CAT-005/CAT-006 (imports).
* **Ré-import écrasant** : le collecteur OpenAgenda réécrit `category` à chaque ré-import (même
  `importKey`) ; tant que CAT-006 n'adapte pas ce flux, un ré-import après une migration de données
  effacerait tout reclassement manuel.
* **Compatibilité API existante** : `category` (chaîne) est exposée par 3 DTO (`ActivityResponse`,
  `ActivitySummaryResponse`, `ActivityDetailResponse`) et consommée telle quelle par le frontend web
  et le mobile — le passage à `CATEGORY`/`ACTIVITY_TYPE`/`AUDIENCE`/`ORGANIZER_TYPE` structurés est
  un changement de contrat à documenter et versionner (CAT-002 et suivants), pas un simple
  renommage de champ.
* **`FoodTruck`** : module séparé, sans date, sans source, sans statut de modération — son
  rattachement au nouveau modèle (ne serait-ce que pour `CATEGORY = GASTRONOMY`) touche une
  architecture différente de celle d'`Activity` ; risque de sous-estimer l'effort si traité comme un
  simple ajout de champ.
* **Table `category` existante** : vide, orpheline, sans `code`/`label`/dimension. La réutiliser
  telle quelle pour porter `CATEGORY` V1 nécessiterait une refonte complète de son schéma (ajout de
  `code`, gestion multilingue du `label`, lien réel vers `activity`) — autant dire une nouvelle table
  en pratique ; risque de sous-estimer ce chantier en pensant « la table existe déjà ».

## 8. Fichiers à modifier (pour CAT-001 et suivants — inventaire, aucune modification ici)

| Domaine | Fichiers concernés |
|---|---|
| Modèle `Category` | `category/domain/Category.java`, `category/infrastructure/CategoryRepository.java`, `category/application/CategoryService.java`, `category/api/CategoryController.java` |
| Modèle `Activity` | `activity/domain/Activity.java`, `activity/application/ActivityService.java` (`validateCategory`, `normalizeCategories`), `activity/infrastructure/ActivityRepository.java` (requêtes `findWithinRadius`, `findWithinBounds`), `activity/api/ActivityController.java`, DTO (`ActivityResponse`, `ActivitySummaryResponse`, `ActivityDetailResponse`) |
| Import OpenAgenda | `collector/infrastructure/OpenAgendaCollector.java` (`firstKeyword`), `collector/application/NormalizationService.java` (`normalize`, `isValidCategory`), `collector/application/ImportService.java` |
| Rattachement source | `source/application/SourceService.java` (`withSourceId`) |
| FoodTruck | `foodtruck/domain/FoodTruck.java`, `foodtruck/application/FoodTruckService.java` (`validateCategory`), `foodtruck/api/FoodTruckController.java`, `foodtruck/infrastructure/FoodTruckRepository.java` |
| Base de données | nouvelles migrations Flyway après `V31` pour `CATEGORY`/`ACTIVITY_TYPE`/`AUDIENCE`/`ORGANIZER_TYPE` ; refonte ou remplacement de la table `category` existante |
| Frontend web | `frontend/src/utils/activityUtils.ts` (`buildCategoryOptions`, `matchesSearchQuery`), `components/explorer/ExplorerFilters.tsx`, `App.tsx` (état `category`, requête), `components/explorer/types.ts` (`category: string` → à revoir), `ContributionModal.tsx` (champ libre → à remplacer), affichages (`ActivityPreview.tsx`, `ActivityList.tsx`, `ActivityDetailModal.tsx`, `FavoritesPage.tsx`, `AdminPage.tsx`, `ExplorerMap.tsx`) |
| Mobile | `mobile/src/api/categories.ts`, `mobile/src/api/types.ts` (`Category`, `category: string`), `mobile/src/api/activities.ts`, `mobile/src/features/map/PreviewCard.tsx` |
| Tests | ~15 fichiers listés en §2.4 |
| Documentation | `docs/02_Architecture/GEO_SEARCH_CONTRACT.md`, `BOUNDING_BOX_SEARCH_CONTRACT.md`, `FOOD_TRUCK_CONTRACT.md`, `DATA_QUALITY_AUDIT.md`, `PROJECT_STATUS.md`, `ARCHITECTURE.md` |

## 9. Migrations nécessaires (inventaire, aucune création de migration ici)

1. **Nouvelles tables/colonnes** pour les 4 dimensions (`category` refondue avec `code`/`label(s)`,
   `activity_type`, `audience`, `organizer_type`), avec FK depuis `activity` — en remplacement de la
   table `category` orpheline actuelle.
2. **Migration de données** `activity.category` (chaîne libre) → `activity.category_id` (FK) selon
   le mapping validé en CAT-001 (§5), avec conservation de la valeur d'origine (nouvelle colonne
   d'archive ou table d'historique) pour ne pas perdre l'information source, en particulier pour les
   activités dont le mapping reste incertain (§6).
3. **Traitement du volume `null` (88,1 %)** : pas une migration de valeur à valeur mais une stratégie
   de classification à définir (CAT-004), avec un statut explicite distinct du `PENDING` de
   modération (§6 point 6).
4. **Food trucks** : migration séparée si `FoodTruck.category` doit être rattaché au référentiel
   (§6 point 2) — à ne pas mélanger avec la migration `Activity`.
5. **Table `activity_tag`** : conservée telle quelle a priori (pas de migration destructrice), mais
   à exploiter davantage comme source de signal pour `ACTIVITY_TYPE`/classification (CAT-004).

Aucune de ces migrations n'est créée par CAT-000 ; cette section sert de base à CAT-002.

## 10. Critères d'acceptation CAT-000 (repris du sprint réécrit)

* [x] Toutes les occurrences pertinentes ont été identifiées (§2, catégories, Seeder, imports,
  frontend, backend, tests, données historiques)
* [x] Le modèle actuel est documenté (§1, confronté explicitement aux 4 dimensions du sprint)
* [x] Seeder et imports sont audités (§1.1, §2.4 — absence de vrai Seeder notée comme risque, §7)
* [x] Frontend et backend sont audités (§2.2, §2.3, §8)
* [x] Les tests concernés sont identifiés (§2.4)
* [x] Les données historiques sont prises en compte (§4, extraction réelle du 29/09/2026 : 47 338
  activités, 88,1 % sans catégorie)
* [x] `CATEGORY_MIGRATION_MAP.md` est créé (ce document)
* [x] Les ambiguïtés sont explicitement listées (§6), y compris la divergence non résolue entre les
  données réelles et la liste fixe de 9 catégories V1 du sprint (domaine emploi/formation, §6 point 1)

**Non couvert par cette extraction** (à fournir avant de figer CAT-001, §4.1) : répartition par
`source_name` (pas seulement `source_type`), top complet des tags `activity_tag`, valeurs réelles de
`food_truck.category`.

## 11. CAT-007 — Migration des données existantes

> Ticket CAT-007 du sprint CATEG. Code : `LegacyCategoryMapper`
> (`backend/.../activityclassification/application/`), tests : `LegacyCategoryMapperTest`
> (rejoue l'extraction réelle du 29/09/2026, copie dans `backend/src/test/resources/legacy/`).

### 11.1 Ce qu'il y a (et n'y a plus) à migrer

La migration `V37__make_primary_category_mandatory.sql` (CAT-006, correctif voulu par Alex) a
exécuté `TRUNCATE TABLE activity CASCADE` : **les ~47 338 activités existantes ont été supprimées**
avant l'ajout de `activity.primary_category_id NOT NULL`. Il n'y a donc **plus d'activité à
reclassifier en base** et CAT-007 **ne crée volontairement aucune migration Flyway** (une
migration SQL de reprise sur une table vide serait sans effet, et un `UPDATE` de masse sur
des valeurs fragmentées irait contre « aucune classification arbitraire »).

Conséquence sur le critère « Les données existantes sont conservées » : il ne peut pas être
satisfait pour les activités, c'est le résultat assumé de V37. Ce qui est conservé : le fichier
`docs/data/categorie_source_type.csv` (valeurs d'origine et volumes), et les ré-imports
OpenAgenda qui rejouent les valeurs d'origine. CAT-007 livre donc la **correspondance
reproductible** à appliquer à ces valeurs, au lieu d'une copie de lignes qui n'existent plus.

### 11.2 Règle

Correspondance **exacte** après normalisation (minuscules, sans accents ni ponctuation), jamais
par sous-chaîne (« marché du travail » n'est pas un marché). Sortie : `MAPPED` (code V1),
`NEEDS_REVIEW` (avec raison, et candidats si l'ambiguïté en porte) ou `NO_VALUE` (null/blanc).
Aucune catégorie n'est créée.

| Résultat | Valeurs | Règle |
|---|---|---|
| `CULTURE` | théâtre, spectacle, concert, musique, jazz, cirque, danse, exposition, lecture, marionnettes, conte, poésie, opéra, clown, magie, cinéma, projection, film, culture (+ variantes listées dans le code) | `EXACT:CULTURE` |
| `MARKETS` | marché(s), brocante, vide-grenier(s) | `EXACT:MARKETS` |
| `GASTRONOMY` | food truck(s), dégustation, gastronomie | `EXACT:GASTRONOMY` |
| `SPORT` | sport(s), football, basket | `EXACT:SPORT` |
| `NATURE` | nature | `EXACT:NATURE` |
| `LEISURE` | jeux, jeux de société, jeux vidéo | `EXACT:LEISURE` |
| `NEEDS_REVIEW` / `AMBIGUOUS_CATEGORY` | patrimoine, JEP, visite(s) guidée/commentée/libre, architecture, archéologie → CULTURE ou TOURISM (§6.4) ; randonnée → SPORT ou NATURE ; `loisir` (§6.5) | `REVIEW:AMBIGUOUS_CATEGORY` |
| `NEEDS_REVIEW` / `AUDIENCE_VALUE` | jeune public, tout public, famille, adulte, jeunesse... : un public (dimension AUDIENCE), pas une catégorie | `REVIEW:AUDIENCE_VALUE` |
| `NEEDS_REVIEW` / `ACTIVITY_TYPE_VALUE` | atelier, conférence, rencontre, festival, animation... : un type (dimension ACTIVITY_TYPE) | `REVIEW:ACTIVITY_TYPE_VALUE` |
| `NEEDS_REVIEW` / `EMPLOYMENT_DOMAIN` | valeurs contenant un mot du domaine emploi/formation/insertion (§6.1, **toujours sans catégorie V1**) | `REVIEW:EMPLOYMENT_DOMAIN` |
| `NEEDS_REVIEW` / `UNRECOGNIZED` | lieux, artistes, codes internes, texte libre | `REVIEW:UNRECOGNIZED` |
| `NO_VALUE` | null ou blanc | `NO_VALUE` |

Écart avec le §5 : celui-ci proposait `patrimoine`/`visite` → `CULTURE` (confiance haute) et
`jeune public` → `LEISURE`. Ces propositions sont **remplacées par les arbitrages validés du §11.5**.

### 11.3 Résultat sur l'extraction réelle (47 338 activités, 950 valeurs normalisées)

| Résultat | Activités | Part |
|---|---|---|
| `MAPPED` | 854 | 1,8 % |
| `NEEDS_REVIEW` | 4 788 | 10,1 % |
| `NO_VALUE` (null) | 41 696 | 88,1 % |

Détail `MAPPED` : CULTURE 808, LEISURE 23, MARKETS 12, NATURE 7, GASTRONOMY 2, SPORT 2.
Détail `NEEDS_REVIEW` : UNRECOGNIZED 2 551, EMPLOYMENT_DOMAIN 1 578, AMBIGUOUS_CATEGORY 252,
AUDIENCE_VALUE 232, ACTIVITY_TYPE_VALUE 175. Les chiffres sont vérifiés par
`LegacyCategoryMapperTest#replayOnRealExtractionMatchesDocumentedFigures`.

Seules 1,8 % des activités sont classables à partir de `activity.category` seul. Les 88,1 % de
null et la longue traîne exigent une classification par tags, titre et description : c'est
CAT-008, pas une migration de valeur.

### 11.4 Critères d'acceptation CAT-007

* [x] Les anciennes données sont analysées (§4, §11.3)
* [x] Les correspondances sont documentées (§11.2)
* [x] Les données ambiguës sont identifiées (`NEEDS_REVIEW` + raison + candidats)
* [x] Aucune classification arbitraire n'est introduite (liste fermée, correspondance exacte)
* [x] La migration est reproductible (code déterministe + rejeu testé sur l'extraction)
* [ ] Les données existantes sont conservées : **non satisfait pour les activités**, supprimées
  par V37 (§11.1) — conséquence assumée de CAT-006, acceptée par Alex dans la validation de CAT-007

**Statut : CAT-007 validé sur la base des arbitrages du §11.5.** La décision de fond sur
emploi/formation a été prise par l'issue #49 (catégorie `EMPLOYMENT`, §11.5 point 2). Les règles ne sont pas modifiées sur la base du seul 1,8 % ; l'apport des tags doit
d'abord être mesuré (`CATEGORIES_CLASSIFICATION.md` §9).

**Reste pour CAT-008** : brancher `LegacyCategoryMapper` (ou ses règles) dans la classification
des imports, avec les statuts `UNCLASSIFIED`/`NEEDS_REVIEW`. La décision de fond sur le domaine
emploi/formation doit être prise **avant** CAT-008 (§11.5).

### 11.5 Arbitrages validés (Alex)

1. **Publics et attributs, pas des catégories.** `jeune public`, `très jeune public`, `famille`,
   `tout public` (et valeurs voisines : adulte, jeunesse...) décrivent un **public cible**
   (dimension `AUDIENCE`) ou un attribut de l'activité. Ce ne sont pas des catégories métier.
   Ils restent `NEEDS_REVIEW` (`AUDIENCE_VALUE`) et **aucune catégorie ne doit être créée ni
   déduite à partir de ces valeurs**.
2. **Emploi / formation : aucune catégorie V1 créée.** Le domaine reste `NEEDS_REVIEW`
   (`EMPLOYMENT_DOMAIN`). CAT-007 ne résout pas la contradiction du §6.1 ; elle fera l'objet
   d'une **décision de fond dédiée avant CAT-008**.
   **→ Décision prise (issue #49, option A)** : le domaine devient la catégorie `EMPLOYMENT`
   (« Emploi & formation », `V44__create_employment_category.sql`), cumulable avec les autres
   (modèle 1..N). `EMPLOYMENT_DOMAIN` n'existe plus ; voir `CATEGORIES_CLASSIFICATION.md` §12.
3. **Patrimoine, visites, JEP : pas de catégorie forcée.** Le référentiel V1 ne permet pas de
   trancher objectivement entre `CULTURE` et `TOURISM` (§6.4). Ces valeurs restent
   `NEEDS_REVIEW` (`AMBIGUOUS_CATEGORY`), candidats `CULTURE`/`TOURISM` conservés pour la relecture.
4. **Concert, musique, jazz → `CULTURE`.**

**Règle pour la suite** : le référentiel V1 n'est pas modifié au fil des cas ambigus. Tout
changement de fond (nouvelle catégorie, redéfinition d'une catégorie) passe par une décision
dédiée, pas par un ticket de migration ou de classification.
