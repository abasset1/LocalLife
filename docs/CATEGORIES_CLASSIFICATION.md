# CATEGORIES_CLASSIFICATION — Classification des imports (CAT-008)

> Sprint CATEG, ticket CAT-008 (« Classification Seeder et imports »). Complète
> `CATEGORY_MIGRATION_MAP.md` §11 (CAT-007). Le référentiel V1 n'est **pas** modifié par ce ticket.

## 1. Principe

Toute classification d'un élément de source passe par un seul point :
`ClassificationService#decide` → `ActivityClassifier` (keywords source) ou `TitleKeywordClassifier` (titre)
→ `KeywordCategoryMatcher`, avec **un seul référentiel en base**, `category_keyword` (§13, issue #50 ; avant,
les keywords source passaient par `LegacyCategoryMapper`, liste fermée codée en Java, supprimée). Aucun
autre code ne traduit une valeur de source en catégorie. Un code obtenu est ensuite résolu vers la catégorie **existante** du référentiel
(`CategoryRepository#findByCode`, catégorie active) ; s'il n'existe pas, le résultat est
`NEEDS_REVIEW` (`CATEGORY_NOT_IN_REFERENTIAL`). **Aucune catégorie n'est jamais créée par une source.**

## 2. Statuts de classification (`ClassificationStatus`)

| Statut | Sens | Activité créée ? |
|---|---|---|
| `UNCLASSIFIED` | aucun signal (ni catégorie d'origine, ni tag) | non |
| `AUTO_CLASSIFIED` | une ou plusieurs catégories V1 déterminées (toutes conservées, §11) | oui |
| `VALIDATED` | catégorie confirmée par un humain | oui |
| `NEEDS_REVIEW` | seulement des signaux ambigus, aucune catégorie résolue dans le référentiel, valeurs non catégorielles | non |
| `REJECTED` | relecture terminée, élément écarté | non |

À ne pas confondre avec `Activity.status` (modération / cycle de vie). Persistance :
`classification_record` (`V38`), avec une contrainte base : `activity_id` est renseigné **si et
seulement si** le statut est `AUTO_CLASSIFIED` ou `VALIDATED`.

## 3. Règles de décision (`ActivityClassifier`)

Signaux : la catégorie d'origine et les tags de la source (pas la description : trop de faux positifs
par mot-clé). Le titre n'intervient qu'en **repli**, quand la source ne fournit aucun keyword : voir §10.

1. aucun signal → `UNCLASSIFIED` ;
2. chaque signal est comparé au référentiel `category_keyword` (portées `ALL` et `SOURCE`), avec la même
   règle que le titre (§13) ; une ou plusieurs catégories reconnues → `AUTO_CLASSIFIED` avec **toutes** ces
   catégories (ordre des signaux). Plusieurs catégories ne sont **jamais** un conflit ; un signal sans
   mot-clé (public, format, lieu...) n'en ajoute ni n'en bloque aucune ;
3. des signaux, mais aucun mot-clé reconnu → `NEEDS_REVIEW` (`UNRECOGNIZED`).

Les valeurs de public (jeune public, famille...) et de format (atelier, festival...) ne sont pas des
mots-clés : elles ne décident jamais la catégorie. Exemple du sprint : « Festival médiéval » ne peut pas
produire une catégorie `MEDIEVAL` (résultat : `NEEDS_REVIEW`).

## 4. Flux d'import (`ImportService`)

* élément **déjà importé** : ses catégories sont conservées, aucune reclassification (un ré-import
  n'écrase pas une classification existante, y compris validée par un humain) ;
* **nouvel élément classé** : activité créée avec **toutes** les catégories décidées, dans la même
  transaction (`ActivityClassificationService#createActivity` → `activity_category`), trace
  `AUTO_CLASSIFIED` liée à l'activité ;
* **nouvel élément non classé** : **aucune activité créée**, trace consignée pour relecture, comptée
  dans le compteur dédié `ImportResult.needsReview` (distinct d'`ignored`, réservé aux données invalides).

### Pourquoi une file de relecture et pas une activité sans catégorie

Une activité a **au moins une catégorie** : contrainte réelle en base (`activity_category`, V42 ;
avant elle `activity.primary_category_id NOT NULL`, V37 — voulu par Alex : on ne contourne pas la
contrainte). Une activité ambiguë ne peut donc pas exister dans `activity` sans catégorie inventée,
ce que le ticket interdit. Elle est consignée dans `classification_record` (titre, valeur d'origine,
raison, candidats), mise à jour à chaque ré-import tant qu'elle n'est pas classée.

**Conséquence à connaître** : tant que la classification reste conservatrice, la majorité des
éléments OpenAgenda ne devient pas visible dans l'application. Sur l'extraction du 29/09/2026,
la catégorie d'origine seule classe 854 activités sur 47 338 (1,8 %) ; l'effet des tags n'est pas
mesuré (extraction des tags non fournie, `CATEGORY_MIGRATION_MAP.md` §4.1).

## 5. Seeder

Il n'existe **aucun Seeder** dans le code : le seul seed d'activités (`V3`) a été supprimé avec les
données par `V37`, et les 4 référentiels sont créés par migrations Flyway directement avec leurs
codes (`V32`-`V35`). Il n'y a donc rien à rendre compatible ; si un Seeder est créé, il devra passer
par `ClassificationService` comme les imports.

## 6. Critères d'acceptation CAT-008

* [x] Seeder compatible avec les codes : aucun Seeder (§5), référentiels seedés par codes
* [x] Imports compatibles avec les codes : `ImportService` n'écrit que des catégories existantes
* [x] Classification centralisée : `ClassificationService` / `ActivityClassifier`
* [x] Aucun mapping source → catégorie direct non contrôlé : seul chemin = le référentiel `category_keyword` (§13)
* [x] Statuts de classification présents : `ClassificationStatus` + `classification_record`
* [x] Cas ambigus gérés : `NEEDS_REVIEW` + raison + candidats, aucune activité créée
* [x] Aucune catégorie créée automatiquement par une source : code absent du référentiel → `NEEDS_REVIEW`

## 7. Non couvert (volontairement)

* **Contributions manuelles** : la catégorie y est déjà choisie par le contributeur parmi les codes
  V1 (`categoryCode`, CAT-006) ; aucune trace `VALIDATED` n'est encore écrite dans
  `classification_record`.
* **Relecture (dette / ticket suivant, voir §8)** : aucun écran ni endpoint pour passer un élément de `NEEDS_REVIEW` à `VALIDATED` ou
  `REJECTED` ; les statuts existent, le workflow reste à faire. Un élément validé devrait ensuite
  créer l'activité (ou être ré-importé).
* **Types, publics, organisateur** : seules les catégories sont classées.

## 8. Dette et tickets suivants

1. **Workflow de relecture** (ticket à créer, hors périmètre CAT-007/CAT-008) : passer un élément de
   `classification_record` de `NEEDS_REVIEW` à `VALIDATED` (catégorie choisie parmi les candidats, création
   de l'activité) ou `REJECTED`. Tant qu'il n'existe pas, les éléments restent consignés et visibles
   uniquement en base et via le compteur `needsReview`.
2. ~~**Décision de fond emploi/formation**~~ : prise par l'issue #49 → catégorie `EMPLOYMENT` (§12).
3. **Mesure de l'apport des tags** : voir §9. Les règles ne sont **pas** modifiées sur la base du seul
   1,8 % (catégorie d'origine seule).
4. Trace `VALIDATED` des contributions manuelles ; classification des types/publics/organisateur.

## 9. Mesurer l'apport des tags avant toute évolution des règles

L'extraction du 29/09/2026 ne contient pas les tags, et un élément non classé n'a pas d'activité donc
pas de ligne `activity_tag`. C'est pourquoi `classification_record.source_tags` conserve les tags de la
source de chaque élément (classé ou non). Après un import réel, la mesure se fait directement :

```sql
-- Répartition des résultats de classification
SELECT status, reason, count(*) FROM classification_record GROUP BY status, reason ORDER BY 3 DESC;

-- Tags les plus fréquents parmi les éléments à relire (candidats pour de nouvelles règles, à arbitrer)
SELECT trim(t) AS tag, count(*) AS nb
FROM classification_record, unnest(string_to_array(source_tags, ',')) AS t
WHERE status IN ('NEEDS_REVIEW', 'UNCLASSIFIED') AND source_tags IS NOT NULL
GROUP BY 1 ORDER BY nb DESC LIMIT 100;
```

Ne pas ajouter de règle tant que cette mesure n'a pas été faite et arbitrée : toute règle ajoutée est
une décision de classification, pas un réglage.

## 10. Fallback titre (`category_keyword`)

> Ticket : `TICKET_TITLE_KEYWORD_CLASSIFICATION.md` (règles de comparaison : `TICKET_CLASSIFICATION_KEYWORD_MATCHING.md`).

### Quand il s'applique

`ClassificationService#decide(title, sourceCategory, sourceTags)` : si la source fournit au moins un
keyword — une catégorie d'origine non blanche **ou** un tag non blanc (même condition que « aucun
signal » de `ActivityClassifier`) — la décision est **exactement** celle de §3 et le titre n'est pas lu.
Le titre n'est exploité que si les keywords source sont `null`, vides ou uniquement blancs. Un keyword
source présent mais inconnu reste `NEEDS_REVIEW` (`UNRECOGNIZED`) : le titre ne le remplace pas. La
description (`description`, `longDescription`) n'est jamais analysée.

### Référentiel de mots-clés (`V39`)

`category_keyword(id, category_id → category, keyword, active, created_at, updated_at)`, unicité
`(category_id, keyword)`, mot-clé non blanc. Les mots-clés métier sont **uniquement en base** ; aucune
catégorie n'est ajoutée (les 9 catégories V1), pas de priorité entre mots-clés (`priority`, ajoutée par
`V41`, supprimée par `V42`, §11). Jeu initial volontairement réduit (13 mots-clés) : `théâtre`, `exposition`, `concert`
(CULTURE) ; `dégustation` (GASTRONOMY) ; `marché`, `vide-grenier`, `brocante` (MARKETS) ; `braderie`
(COMMERCE) ; `football`, `rugby` (SPORT) ; `sortie nature` (NATURE) ; `jeux de société` (LEISURE) ;
`réunion publique` (CITIZEN). Ajouter, retirer ou désactiver (`active = false`) un mot-clé ne demande
aucune modification de code.

**Enrichissement `V43` (issue #47)** : 274 mots-clés actifs, toutes les catégories couvertes (TOURISM n'en avait
aucun), `escape` seul désactivé ; audit, mesures sur le vocabulaire réel et décisions de taxonomie dans
[`AUDIT_REFERENTIEL_CATEGORIES.md`](AUDIT_REFERENTIEL_CATEGORIES.md). Un même mot-clé peut désigner plusieurs
catégories (`patrimoine` → CULTURE + TOURISM).

### Comparaison (`KeywordMatcher`, point unique)

Texte et mot-clé sont découpés en mots ; le mot-clé matche si ses mots apparaissent **consécutivement
et entiers** dans le titre. Ignorés : casse, espaces de début/fin et multiples, séparateurs (tout ce
qui n'est ni lettre ni chiffre : tiret, ponctuation, apostrophes `'` `’` `ʼ`). **Jamais** : suppression
ou translittération des accents, stemming, synonymes, fuzzy matching, NLP/IA. Conséquences :
`marché` ≠ `marche` ; `art` ne matche pas `artisanat` mais matche `atelier d'art` ; `vide grenier`
matche `Grand vide-grenier municipal` ; `d'art` ≠ `dart`.

### Décision (`TitleKeywordClassifier`)

| Mots-clés reconnus dans le titre | Résultat |
|---|---|
| aucun | `UNCLASSIFIED`, règle `UNCLASSIFIED:NO_SIGNAL` |
| un ou plusieurs, **même** catégorie | `AUTO_CLASSIFIED`, règle `TITLE_KEYWORD:AUTO:<CODE>` |
| catégories **différentes** | `AUTO_CLASSIFIED` avec **toutes** les catégories (ordre du référentiel), règle `TITLE_KEYWORD:AUTO:<CODE1>+<CODE2>...` |

Les codes obtenus passent ensuite par la même résolution que §1 : une catégorie absente ou inactive est
écartée ; si aucune n'est résolue → `NEEDS_REVIEW` / `CATEGORY_NOT_IN_REFERENTIAL`. Les mots-clés d'une
catégorie inactive restent tracés dans `matched_keywords`.

### Traçabilité

`classification_record` (réutilisé) gagne `classification_source` (`TITLE_KEYWORD` si la décision
vient du titre, `NULL` pour les keywords source) et `matched_keywords` (mots-clés reconnus, séparés
par `|`). Le fallback sans correspondance n'écrit pas de source : rien n'a été classé à partir du titre.

### Limites connues

* Un mot-clé isolé peut produire un faux positif (« Marché de la poésie » → MARKETS) : le jeu initial
  est à valider et à étendre par arbitrage, pas par intuition (§9).
* Une activité déjà importée garde ses catégories : ajouter un mot-clé ne reclasse pas l'existant.
* Une requête sur `category_keyword` par élément sans keyword source (table de quelques dizaines de lignes).

## 11. 1..N catégories par activité (issue #45)

> Remplace le modèle « 1 principale + 0..N secondaires » de l'issue #43 (V41), supprimé.

Une activité a **1 à N catégories**, toutes équivalentes : aucune notion de catégorie principale ou
secondaire, aucun score, aucune priorité, aucun arbitrage. Toutes les catégories détectées sont
conservées ; plusieurs catégories ne sont **jamais** un conflit ni une raison de `NEEDS_REVIEW`. La
classification reste déterministe et fondée sur les mots-clés stockés en base : l'amélioration passe par
l'**enrichissement du référentiel** (catégories et mots-clés), pas par la complexification du moteur.

### Modèle (`V42`)

`activity_category(id, activity_id → activity ON DELETE CASCADE, category_id → category)`, unicité
`(activity_id, category_id)`. « Au moins une catégorie » est une contrainte **réelle en base** : trigger
de contrainte `DEFERRABLE INITIALLY DEFERRED`, vérifié au commit (une activité insérée sans catégorie,
ou dont la dernière catégorie est retirée, fait échouer la transaction). Une activité se crée donc avec
ses catégories dans une seule transaction : `ActivityClassificationService#createActivity` (imports et
contributions manuelles) ; `replaceCategories` remplace l'ensemble, jamais par une liste vide.

`V42` (rejouable) :
* crée `activity_category` et y reprend **toutes** les catégories existantes
  (`activity.primary_category_id` puis `activity_secondary_category`) ;
* renomme `classification_record.secondary_category_codes` en `category_codes` (toutes les catégories
  retenues, séparées par `,`) et le renseigne pour les activités existantes ;
* supprime `activity.primary_category_id`, `activity_secondary_category` et `category_keyword.priority` ;
* ajoute le mot-clé `nature` (NATURE). Les mots-clés ajoutés par `V41` (`biodiversité`, `vélo`,
  `randonnée(s)`) et la désactivation de `conférence(s)` (mot de format) sont conservés.

### Résolution

* chaque code est résolu vers une catégorie existante et active ; un code absent ou inactif est
  **écarté** (jamais créé), les autres sont conservés ;
* aucun code résolu → `NEEDS_REVIEW` (`CATEGORY_NOT_IN_REFERENTIAL`).

### Exemples (couverts par `TitleKeywordClassifierTest` et `TitleKeywordClassificationIntegrationTest`)

| Titre | Catégories |
|---|---|
| Marché gourmand avec dégustation et concert | MARKETS + GASTRONOMY + CULTURE |
| Balade nature à vélo | NATURE + SPORT |
| Exposition de peinture | CULTURE |
| Concert au marché de Noël | CULTURE + MARKETS |

## 12. Catégorie emploi & formation (issue #49)

Décision (option A) : le domaine emploi / formation / insertion devient une **catégorie à part entière**,
`EMPLOYMENT` (« Emploi & formation »), créée par `V44__create_employment_category.sql`. C'est la seule
catégorie ajoutée au référentiel V1 ; comme les autres, elle est exposée par `GET /api/v1/categories`.

* **Catégories d'origine et tags** (`LegacyCategoryMapper` à l'époque, repris en base par #50, §13) : un jeton emploi (mot entier : `emploi`,
  `recrutement`, `formation`, `alternance`, `metiers`, `travail`, `cv`, `entreprise`, `job`, `embauche`…) donne
  `EMPLOYMENT` (`TOKEN:EMPLOYMENT`) ; quelques libellés entiers propres aux agendas emploi (« S'informer »,
  « Se préparer », « Les clés pour réussir »…) aussi (`EXACT:EMPLOYMENT`). « Métiers d'art » reste CULTURE.
* **Titres** (`category_keyword`) : 34 mots-clés précis (`emploi`, `job dating`, `alternance`, `cv`,
  `formation professionnelle`, `découverte des métiers`, `marché du travail`, `création d'entreprise`…).
  Exclus car ambigus seuls : `métier(s)`, `formation`, `apprentissage`, `stage`.
* **Plus de `NEEDS_REVIEW` forcé** (`EMPLOYMENT_DOMAIN` supprimé) : `EMPLOYMENT` se cumule avec les autres
  catégories détectées (« Recrutement » + « théâtre » → EMPLOYMENT + CULTURE).
* Les éléments déjà consignés en `NEEDS_REVIEW` n'ont pas d'activité : ils sont re-décidés au prochain
  import, sans reprise de données.

Effet mesuré sur l'extraction du 29/09/2026 :

| Chemin | Avant | Après |
|---|---:|---:|
| Catégorie d'origine : activités classées | 854 | 3 162 (dont 2 308 EMPLOYMENT) |
| Catégorie d'origine : `NEEDS_REVIEW` | 4 788 (dont 1 578 `EMPLOYMENT_DOMAIN`) | 2 480 |
| Catégorie d'origine : `UNRECOGNIZED` | 2 551 | 1 821 |
| Vocabulaire réel reconnu par les mots-clés titre | 27,3 % | 50,8 % |

Faux positifs connus côté titre (le moteur n'a pas de mot-clé d'exclusion) : « Marché du travail » →
MARKETS + EMPLOYMENT, « Recrutement par le sport » → SPORT + EMPLOYMENT. Côté catégorie d'origine, ces
valeurs ne donnent que `EMPLOYMENT` (correspondance de la valeur entière).

## 13. Référentiel unique pour les keywords source et les titres (issue #50)

Avant : deux référentiels parallèles. Les catégories d'origine / tags passaient par `LegacyCategoryMapper`,
une liste fermée **codée en Java** (correspondance exacte sans accents, jetons emploi) ; les titres, par
`category_keyword` **en base**. Enrichir la base ne profitait qu'aux titres, et ajouter un mot-clé source
demandait un déploiement.

Désormais : **un seul référentiel, `category_keyword`**, et **une seule règle** (`KeywordCategoryMatcher`,
comparaison `KeywordMatcher` : casse, espaces et séparateurs ignorés, accents conservés, mots entiers) :

1. si le texte **entier** est exactement un mot-clé, lui seul décide (« Marché du travail » → EMPLOYMENT, pas
   MARKETS par « marché » ; « Métiers d'art » → CULTURE, pas EMPLOYMENT par « métiers ») ;
2. sinon, **toutes** les catégories des mots-clés reconnus (modèle 1..N).

La règle s'applique au titre et à **chaque** catégorie d'origine / tag de la source. Priorité source > titre
inchangée. `LegacyCategoryMapper` est supprimé ; plus aucun mot-clé métier n'est codé en Java.

### Portée (`category_keyword.scope`, `V45`)

| `scope` | S'applique à | Exemples |
|---|---|---|
| `ALL` (défaut) | titres **et** keywords source | `théâtre`, `marché`, `recrutement`, `job dating` |
| `SOURCE` | keywords source seulement | `formation`, `métiers`, `travail`, `insertion`, `s'informer`, `culture`, `projection` |

`SOURCE` sert aux termes fiables dans les mots-clés structurés d'un agenda (« Métiers du BTP », « Formation »)
mais ambigus dans un titre libre (« Formation aux premiers secours », « Travail du bois »).

### Reprise de la liste Java (`V45`)

Les correspondances de `LegacyCategoryMapper` absentes du référentiel ont été ajoutées : jetons et libellés
emploi (portée `SOURCE`), `culture` et `projection` (`SOURCE`), `métiers d'art` /
`journées européennes des métiers d'art` (CULTURE) et la graphie observée `theâtre`. Les listes
« non-catégorie » de la classe Java (valeurs ambiguës, publics, formats) sont **supprimées** : avec le modèle
1..N, `patrimoine` / `jep` donnent déjà CULTURE + TOURISM, et un public ou un format n'est simplement pas un
mot-clé. Conséquence : les raisons `AMBIGUOUS_CATEGORY`, `AUDIENCE_VALUE` et `ACTIVITY_TYPE_VALUE` disparaissent,
tout élément sans mot-clé reconnu est `NEEDS_REVIEW` / `UNRECOGNIZED`.

### Audit (extraction du 29/09/2026, 5 642 activités avec une catégorie d'origine)

| Règle évaluée | Classées | Perdues vs liste Java | Changées | Gagnées |
|---|---:|---:|---:|---:|
| Liste Java (`LegacyCategoryMapper`, avant) | 3 162 | — | — | — |
| Référentiel en base tel quel, mots sans accents | 2 872 | 920 | 73 | 630 |
| Valeur entière seulement, sans accents | 2 250 | 1 217 | 0 | 305 |
| **Retenu** : scope `SOURCE` + valeur entière prioritaire + mots, accents conservés (= titre) | **3 790** | **0** | 24 | 628 |

* **0 perte** : chacune des 130 valeurs (3 162 activités) que la liste Java classait reste classée avec au moins
  la même catégorie — vérifié par `SourceKeywordReplayIntegrationTest` contre un instantané figé avant la
  suppression (`legacy/legacy_mapper_snapshot.csv`, validé contre le code Java).
* **24 changements**, tous des ajouts de catégorie : « Métiers de la culture et du spectacle » → EMPLOYMENT +
  CULTURE, « Recrutement par le sport » → EMPLOYMENT + SPORT…
* **628 gains** : valeurs auparavant à relire, désormais classées par les mots-clés déjà validés pour les titres
  (`architecture`, `patrimoine` / JEP → CULTURE + TOURISM, `jardiner`, `archives`, `marionnettes`…).
* À relire : 2 480 → **1 852** (`UNRECOGNIZED`, essentiellement noms propres, publics et formats).
* Effet sur le titre : la priorité à la valeur entière corrige « Marché du travail » (EMPLOYMENT seul) ;
  couverture du vocabulaire inchangée (50,8 %).

### Traçabilité

`classification_record.classification_source` vaut `SOURCE_KEYWORD` (keywords source) ou `TITLE_KEYWORD`
(titre) ; `matched_keywords` liste les mots-clés reconnus dans les deux cas.

