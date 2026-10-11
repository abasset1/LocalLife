# Audit de la recherche V1 (#93)

Parent : #89. Périmètre :
- endpoint `GET /api/v1/activities/search` (#91, correctif multi-champs #96) ;
- intégration web (#92) ;
- suggestions de communes (#88, #90/#94) ;
- non-régression de `/nearby`, `/within-bounds` et `GET /api/v1/activities`.

Audit réalisé le 2026-10-11 sur `main` à `37366cb` (après la fusion de #97).

## Couverture

### Backend

`ActivitySearchIntegrationTest` (HTTP et PostgreSQL/PostGIS réels) compte 57 cas, dont 12 ajoutés par #93.

| Exigence de #93 | Tests |
|---|---|
| Visibilité `PUBLISHED` | `onlyPublishedActivitiesAreFound` ; `unpublishedActivities_AreNeverCounted_EvenThroughOccurrencesOrFilters` (#93) : les activités non publiées avec occurrence et lieu sont absentes, même avec catégorie, date et position |
| Champs, accents, casse, ponctuation | `searchesEachField_…`, `searchesCategoryLabelAndCode_…`, `searchesTheCityOfAnEffectiveOccurrenceLocation`, `ignoresCaseAccentsPunctuationAndExtraSpaces`, `multiField_IgnoresAccentsAndCaseInEveryField` |
| Filtres catégorie et date, pagination et total | `filtersByCategory_…`, `filtersByDateAndPeriod_…`, `withoutDate_…`, `paginatesWithAStableOrderAndTotal` |
| Occurrences récurrentes, annulées, exception de date, changement de lieu | `severalOccurrences_…`, `cancelledOccurrences_AreIgnored`, `locationExceptionOfAnOccurrence_…`, `periodFilter_…` ; `recurringSeries_WithADateException_MatchesTheActualDateOfEachOccurrence` (#93) : série hebdomadaire dont une séance est déplacée, trouvée à sa date réelle et plus à sa date habituelle |
| Pertinence puis proximité ; ordre déterministe sans coordonnées ; résultats éloignés atteignables | `ranksByRelevanceLevelBeforeDistance`, `ranksByDistanceWithinALevel_WithoutAnyMaximumRadius`, `withoutCoordinates_ordersByStartDateThenId_…`, `multiField_SecondaryOrder_…` ; `farResults_RemainReachableByPagination` (#93) : un résultat à plus de 430 km est atteint en dernière page |
| Coordonnées manquantes, aucune position effective | `activityWithoutAnyEffectivePosition_…` ; `resultWithoutEffectivePosition_HasAReadableDetail` (#93) : la fiche `GET /api/v1/activities/{id}` de ce résultat se charge |
| Sécurité | `likeWildcardsAlone_AreRejected_NeverMatchEverything` (#93, 6 cas) ; `likeWildcardsAndSqlInjection_AreInertText` (#93) ; `searchingWithAPosition_PersistsNothing` (#93) : le nombre de lignes est identique dans toutes les tables du schéma `public` avant et après une recherche avec position |
| Requêtes invalides | `invalidRequests_AreRejectedWithTheStandardErrorFormat` (17 cas), `queryLongerThan120Characters_IsRejected` |

`GeographicEndpointsNonRegressionIntegrationTest` (nouveau, 4 tests) :
- fige les champs JSON des éléments de `/nearby` et `/within-bounds` (20 champs, `ActivityResponse`) et de `GET /api/v1/activities` (10 champs, résumé) ;
- vérifie que `/nearby` garde son rayon et `/within-bounds` sa zone, sans activité `PENDING` ;
- vérifie que la recherche globale trouve aussi l'activité éloignée que ces deux endpoints écartent.

Les tests existants restent en place : `ActivityControllerIntegrationTest` (contrats, erreurs, filtres de `/nearby` et `/within-bounds`) et `NonRegressionIntegrationTest`.

### Frontend (Vitest + jsdom + Testing Library)

50 tests au total.

| Exigence de #93 | Tests |
|---|---|
| Suggestions de communes : saisie modifiée pendant le debounce ou pendant une requête en vol ; réponse ancienne jamais affichée ni sélectionnable | `useCitySuggestions.test.tsx`, `ExplorerSearch.test.tsx` (#94) |
| Recherche globale, résultat hors zone, filtres conservés entre liste et carte, pagination, sélection et centrage | `App.search.test.tsx` (11 parcours, carte simulée), `useActivitySearch.test.tsx` |
| États chargement, vide, erreur, réessai, effacement, texte trop court | `App.search.test.tsx` ; `SearchResultBanner.test.tsx` (#93) |
| Clavier, focus, attributs accessibles | `ExplorerSearch.test.tsx` (combobox ARIA, flèches, Entrée, Échap) ; `SearchResultBanner.test.tsx` (#93) : `role=status`/`alert`, navigation « Pages de résultats », boutons de bornes désactivés, **focus rendu après un changement de page** ; `ActivityList.test.tsx` (#93) : résultat activable au clavier |
| Position absente ou présente | `App.search.test.tsx` : sans position, pas de `latitude` ; avec géolocalisation acceptée, ou après le choix d'une commune, `latitude`/`longitude` envoyées (#93) |
| Coordonnées de résultat nulles | `App.search.test.tsx` ; `ActivityList.test.tsx` (#93) : « Lieu non précisé », « Date non précisée », jamais « NaN » ; `globalSearch.test.ts` |
| Liste de la carte (non-régression) | `ActivityList.test.tsx` (#93) : regroupement par ville, sans distance |

Mobile et desktop : la mise en page n'est pas testable dans jsdom. Elle a été vérifiée dans Chromium (Playwright) en 1280×800 et 390×844 lors de #92 (51 vérifications, voir la PR #97), puis de nouveau pour le focus au clavier (voir la PR #93).

## Défaut corrigé par cet audit

**Focus perdu au clavier après « Suivants » ou « Précédents ».** Pendant le chargement de la page, le bandeau remplace les boutons de pagination par « Recherche… ». Le bouton utilisé disparaissait, et le focus retombait sur `<body>`, ce qui renvoyait l'utilisateur clavier ou lecteur d'écran en haut de la page.

Désormais, une fois la page affichée, le focus revient :
- sur le bouton utilisé ;
- sur l'autre bouton si le premier est désactivé, en dernière page ;
- sur « Réessayer » en cas d'erreur.

Trois tests couvrent ce comportement et échouent sur le code antérieur.

## Performance

Mesure faite avec `EXPLAIN (ANALYZE, BUFFERS)` de la requête réelle, extraite de `ActivitySearchRepository`, sur PostgreSQL 16 / PostGIS 3.4 en local. Jeu de données généré dans une transaction annulée :
- 20 000 activités, dont 18 000 publiées ;
- 10 000 horaires, 30 000 occurrences (5 % annulées, un tiers avec un lieu d'exception) ;
- 3 000 lieux répartis sur 10 villes ;
- descriptions d'environ 500 caractères.

Trois exécutions par cas :

| Cas | Temps d'exécution |
|---|---|
| 1 terme fréquent (« concert ») | 282 à 287 ms |
| 1 terme et position | 275 à 331 ms |
| Multi-champs avec position (« concert lyon ») | 218 à 223 ms |
| Catégorie et période | 179 à 194 ms |
| Terme rare | 215 à 299 ms |
| Page lointaine (`offset=2000`) | 309 à 330 ms |

Le temps de planification est d'environ 2 ms. Ces chiffres sont cohérents avec #91 et #96 (~100 à 350 ms).

**Plan :**
- parcours séquentiels de `activity`, `schedule`, `occurrence` et `location`, avec jointures par hachage ;
- tris en mémoire (quicksort, 1,6 Mo au plus) ;
- **aucun débordement sur disque** (« external merge » absent des 18 plans).

Aucun index n'est employé pour la correspondance de texte. C'est attendu : `LIKE '%terme%'` ne peut pas utiliser un index B-tree, et une recherche globale lit toutes les activités publiées. L'index GiST de `activity.location` ne sert pas, car la distance n'est calculée que pour les activités retenues, sans filtre de rayon.

**Seuil et piste.** Le coût croît linéairement avec le nombre d'activités publiées, et d'occurrences non terminées pour les activités avec horaires. Au-delà d'environ 100 000 activités publiées, la piste mesurable reste un index trigramme (GIN `pg_trgm`) sur les colonnes `search_*`. Cela demande une migration dédiée et une décision : l'extension n'est pas installée, et ce serait gratuit (pas de service externe).

## Sécurité

- **Injection SQL : aucune.** Toutes les valeurs passent par des paramètres nommés (`NamedParameterJdbcTemplate`) : `q`, `latitude`/`longitude` typées `DOUBLE`, catégories, dates typées `DATE`, `limit`/`offset`, fuseau et mots vides. Aucune chaîne utilisateur n'est concaténée dans le SQL.
- **Jokers `LIKE` : neutres.** Les motifs sont construits en SQL à partir du texte normalisé par `ll_search_normalize`, qui ne garde que lettres, chiffres et espaces. `%`, `_` et `\` deviennent des espaces, et une requête faite uniquement de jokers est refusée (400). Une recherche ne peut donc jamais tout faire correspondre. Ces deux points sont testés.
- **Activités non publiées : jamais renvoyées ni comptées par la recherche.** Le filtre `status = 'PUBLISHED'` s'applique dès la première étape de la requête, avant les occurrences et le calcul du total. C'est testé avec les quatre autres statuts.
- **Position : jamais persistée ni journalisée.** Le dépôt de recherche ne fait que des `SELECT`. Le service et le contrôleur ne journalisent rien, et aucun filtre de journalisation des requêtes HTTP n'existe. Côté web, la position reste dans l'état React. C'est testé (comptage des lignes de toutes les tables).
- **Aucun moteur ni service externe** pour la recherche texte : PostgreSQL seul. Les suggestions de communes utilisent `geo.api.gouv.fr`, gratuit et sans clé (#88).

## Risques résiduels et constats hors périmètre

1. **`GET /api/v1/activities` (liste) renvoie aussi les activités non publiées** (`PENDING`, `REJECTED`, `CANCELLED`, `ARCHIVED`), sans authentification. Ce comportement est antérieur à la recherche. La réponse n'expose pas le statut, mais bien le titre, les coordonnées et l'adresse de contributions en attente ou refusées. L'application mobile utilise cet endpoint pour sa liste. **Non corrigé ici** : cela changerait le comportement d'un endpoint existant ; c'est une décision à prendre. Le test de non-régression vérifie les champs de cet endpoint, sans figer ni masquer ce comportement.
2. **`GET /api/v1/activities/{id}` répond 500** pour une activité dont `latitude`/`longitude` sont nulles en base : le constructeur du record `Activity` attend des `double`. Aucun chemin d'écriture de l'application ne produit ce cas (création, import et modification passent par ce record). Seules des données historiques ou insérées à la main peuvent le provoquer. Vérification possible en production : `SELECT COUNT(*) FROM activity WHERE latitude IS NULL OR longitude IS NULL;`.
3. **Pas de limitation de débit** sur les endpoints publics (constat global à l'application). Une recherche coûte environ 0,2 à 0,3 s de base de données à ce volume ; un appelant abusif peut charger la base. À traiter au niveau de l'infrastructure (proxy) ou de l'application, séparément.
4. **Volume** : voir la section Performance (seuil indicatif de 100 000 activités publiées).
5. **Pertinence V1** : correspondance par sous-chaîne, sans fautes de frappe ni synonymes, accents de l'alphabet latin seulement (voir `GLOBAL_SEARCH_CONTRACT.md`, Limites connues).
6. **Lecteur d'écran** : rôles et noms accessibles vérifiés par les tests. Aucun audit automatique (axe) et aucun test avec un vrai lecteur d'écran (NVDA, VoiceOver) n'ont été faits.
