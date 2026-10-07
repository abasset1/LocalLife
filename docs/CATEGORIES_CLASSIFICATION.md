# CATEGORIES_CLASSIFICATION — Classification des imports (CAT-008)

> Sprint CATEG, ticket CAT-008 (« Classification Seeder et imports »). Complète
> `CATEGORY_MIGRATION_MAP.md` §11 (CAT-007). Le référentiel V1 n'est **pas** modifié par ce ticket.

## 1. Principe

Toute classification d'un élément de source passe par un seul point :
`ClassificationService#decide` → `ActivityClassifier` (règles pures) → `LegacyCategoryMapper`
(liste fermée, correspondance exacte, CAT-007). Aucun autre code ne traduit une valeur de source en
catégorie. Un code obtenu est ensuite résolu vers la catégorie **existante** du référentiel
(`CategoryRepository#findByCode`, catégorie active) ; s'il n'existe pas, le résultat est
`NEEDS_REVIEW` (`CATEGORY_NOT_IN_REFERENTIAL`). **Aucune catégorie n'est jamais créée par une source.**

## 2. Statuts de classification (`ClassificationStatus`)

| Statut | Sens | Activité créée ? |
|---|---|---|
| `UNCLASSIFIED` | aucun signal (ni catégorie d'origine, ni tag) | non |
| `AUTO_CLASSIFIED` | un seul code V1, sans signal contradictoire | oui |
| `VALIDATED` | catégorie confirmée par un humain | oui |
| `NEEDS_REVIEW` | signaux ambigus, conflictuels, hors référentiel, emploi/formation | non |
| `REJECTED` | relecture terminée, élément écarté | non |

À ne pas confondre avec `Activity.status` (modération / cycle de vie). Persistance :
`classification_record` (`V38`), avec une contrainte base : `activity_id` est renseigné **si et
seulement si** le statut est `AUTO_CLASSIFIED` ou `VALIDATED`.

## 3. Règles de décision (`ActivityClassifier`)

Signaux : la catégorie d'origine et les tags de la source (pas la description : trop de faux positifs
par mot-clé). Le titre n'intervient qu'en **repli**, quand la source ne fournit aucun keyword : voir §10.

1. aucun signal → `UNCLASSIFIED` ;
2. un signal emploi/formation → `NEEDS_REVIEW` (`EMPLOYMENT_DOMAIN`), même si un autre signal est classable ;
3. plusieurs catégories V1 différentes → `NEEDS_REVIEW` (`CONFLICTING_SIGNALS`, candidats listés) ;
4. une seule catégorie V1 et aucun signal ambigu qui l'exclut → `AUTO_CLASSIFIED` ;
5. sinon → `NEEDS_REVIEW` (`AMBIGUOUS_CATEGORY`, `AUDIENCE_VALUE`, `ACTIVITY_TYPE_VALUE` ou `UNRECOGNIZED`).

Les valeurs de public (jeune public, famille...) et de type (atelier, festival...) ne décident jamais
la catégorie et ne la bloquent pas non plus. Exemple du sprint : « Festival médiéval » ne peut pas
produire une catégorie `MEDIEVAL` (résultat : `NEEDS_REVIEW`).

## 4. Flux d'import (`ImportService`)

* élément **déjà importé** : sa catégorie principale est conservée, aucune reclassification (un
  ré-import n'écrase pas une classification existante, y compris validée par un humain) ;
* **nouvel élément classé** : activité créée avec la catégorie décidée, trace `AUTO_CLASSIFIED` liée à l'activité ;
* **nouvel élément non classé** : **aucune activité créée**, trace consignée pour relecture, comptée
  dans le compteur dédié `ImportResult.needsReview` (distinct d'`ignored`, réservé aux données invalides).

### Pourquoi une file de relecture et pas une activité sans catégorie

`activity.primary_category_id` est `NOT NULL` (V37, voulu par Alex : on ne contourne pas la
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
* [x] Aucun mapping source → catégorie direct non contrôlé : seul chemin = `LegacyCategoryMapper` (liste fermée)
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
* **Types, publics, organisateur** : seule la catégorie principale est classée.
* **Domaine emploi/formation** : reste `NEEDS_REVIEW` ; la décision de fond est à prendre séparément (CAT-007 §11.5).

## 8. Dette et tickets suivants

1. **Workflow de relecture** (ticket à créer, hors périmètre CAT-007/CAT-008) : passer un élément de
   `classification_record` de `NEEDS_REVIEW` à `VALIDATED` (catégorie choisie parmi les candidats, création
   de l'activité) ou `REJECTED`. Tant qu'il n'existe pas, les éléments restent consignés et visibles
   uniquement en base et via le compteur `needsReview`.
2. **Décision de fond emploi/formation** : à arbitrer avant de considérer CAT-007 clos (voir
   `CATEGORY_MIGRATION_MAP.md` §11.5). Aucune catégorie n'est créée en attendant.
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
catégorie n'est ajoutée (les 9 catégories V1), pas de colonne `priority` (une ambiguïté n'est jamais
masquée). Jeu initial volontairement réduit (13 mots-clés) : `théâtre`, `exposition`, `concert`
(CULTURE) ; `dégustation` (GASTRONOMY) ; `marché`, `vide-grenier`, `brocante` (MARKETS) ; `braderie`
(COMMERCE) ; `football`, `rugby` (SPORT) ; `sortie nature` (NATURE) ; `jeux de société` (LEISURE) ;
`réunion publique` (CITIZEN). Ajouter, retirer ou désactiver (`active = false`) un mot-clé ne demande
aucune modification de code.

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
| catégories **différentes** | `NEEDS_REVIEW`, raison `CONFLICTING_SIGNALS`, candidats listés, règle `TITLE_KEYWORD:REVIEW:CONFLICTING_SIGNALS` |

Le code obtenu passe ensuite par la même résolution que §1 (catégorie existante et active, sinon
`NEEDS_REVIEW` / `CATEGORY_NOT_IN_REFERENTIAL`). Les mots-clés d'une catégorie inactive restent des
candidats : ils ne servent jamais à écarter silencieusement un conflit.

### Traçabilité

`classification_record` (réutilisé) gagne `classification_source` (`TITLE_KEYWORD` si la décision
vient du titre, `NULL` pour les keywords source) et `matched_keywords` (mots-clés reconnus, séparés
par `|`). Le fallback sans correspondance n'écrit pas de source : rien n'a été classé à partir du titre.

### Limites connues

* Un mot-clé isolé peut produire un faux positif (« Marché de la poésie » → MARKETS) : le jeu initial
  est à valider et à étendre par arbitrage, pas par intuition (§9).
* Un titre avec un mot-clé ET un mot-clé d'une autre catégorie part en relecture, par construction.
* Une activité déjà importée garde sa catégorie : ajouter un mot-clé ne reclasse pas l'existant.
* Une requête sur `category_keyword` par élément sans keyword source (table de quelques dizaines de lignes).
