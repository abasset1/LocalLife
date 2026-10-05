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

Signaux : la catégorie d'origine et les tags de la source (pas le titre ni la description : trop de
faux positifs par mot-clé).

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
  dans `ImportResult.ignored`.

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
* **Relecture** : aucun écran ni endpoint pour passer un élément de `NEEDS_REVIEW` à `VALIDATED` ou
  `REJECTED` ; les statuts existent, le workflow reste à faire. Un élément validé devrait ensuite
  créer l'activité (ou être ré-importé).
* **Types, publics, organisateur** : seule la catégorie principale est classée.
* **Domaine emploi/formation** : reste `NEEDS_REVIEW` ; la décision de fond est à prendre séparément (CAT-007 §11.5).
* **Compteur dédié** : `ImportResult` n'a pas de champ « à relire » (les non classés sont dans `ignored`).
