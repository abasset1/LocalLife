# Audit du référentiel catégories + mots-clés (issue #47)

> Objectif : améliorer la couverture de la classification **par les données du référentiel**, sans toucher
> au moteur (1..N catégories, déterministe, mots-clés en base — voir `CATEGORIES_CLASSIFICATION.md` §10-§11).
> Résultat : migration `V43__enrich_category_keywords_audit.sql` (données uniquement) et test de non-régression
> `KeywordReferentialAuditIntegrationTest`.

## 0. Données utilisées et limites

* **Données réelles disponibles** : l'extraction OpenAgenda du 29/09/2026 (`docs/data/categorie_source_type.csv`,
  aussi en ressource de test `legacy/categorie_source_type.csv`) — 47 338 activités, 1 074 valeurs distinctes de
  catégorie d'origine (1er mot-clé OpenAgenda), pondérées par leur nombre d'activités.
* **Pas de titres réels** : l'extraction ne contient que la catégorie d'origine. Or 41 696 activités sur 47 338
  (88 %) n'ont **aucune** catégorie d'origine et passent donc par le fallback titre — le seul chemin qu'alimente
  `category_keyword`. La mesure ci-dessous rejoue donc le fallback titre sur le **vocabulaire réel** des
  catégories d'origine (chaque valeur traitée comme un titre) : c'est une mesure indirecte, pas un réimport.
* **Réimport impossible depuis la session de travail** : la politique réseau de l'environnement refuse
  `api.openagenda.com` (et `public.opendatasoft.com`). Le protocole de mesure sur un vrai réimport est donné au §6,
  à exécuter sur l'environnement qui a accès à OpenAgenda.

## 1. Référentiel avant / après

9 catégories V1, toutes actives : CULTURE, GASTRONOMY, MARKETS, COMMERCE, SPORT, LEISURE, NATURE, TOURISM, CITIZEN.

| Catégorie | Mots-clés actifs avant (V39-V42) | Après V43 | Remarque |
|---|---:|---:|---|
| CULTURE | 34 | 101 | concentrait déjà 57 % des mots-clés |
| GASTRONOMY | 3 | 27 | |
| MARKETS | 3 | 12 | |
| COMMERCE | 1 | 8 | |
| SPORT | 5 | 46 | |
| LEISURE | 10 | 22 | `escape` seul désactivé |
| NATURE | 3 | 23 | |
| TOURISM | **0** | 14 | catégorie jamais attribuable par le titre avant V43 |
| CITIZEN | 1 | 19 | |
| **Total** | **60** | **272** | |

Mots-clés jugés trop génériques ou ambigus :

* `escape` (LEISURE) : titre de film, de spectacle… → **désactivé** (`escape game` reste actif).
* `marché` (MARKETS) : matche « Marché du travail » (49 activités, domaine emploi). Conservé : c'est le mot-clé
  principal de MARKETS, et le moteur n'a pas de mot-clé d'exclusion (hors périmètre). Côté catégorie d'origine,
  « travail » déclenche déjà `EMPLOYMENT_DOMAIN` ; le risque est un titre « Marché du travail » sans keyword source.
* `jeu` (LEISURE) : matche « jeu d'actrice » dans un libellé théâtral (2 activités). Conservé (« jeu de piste »,
  « fête du jeu »…), faux positif marginal.
* `sport` (SPORT, ajouté) : matche trois libellés emploi (« Recrutement par le sport », « Métiers du sport »,
  « Parcours de remobilisation par le sport », 11 activités). Conservé : indispensable pour les vrais événements
  sportifs ; ces libellés relèvent de toute façon de la décision emploi/formation (§4).
* `nature` (NATURE, V42) : peut matcher « nature morte ». Aucun cas dans les données ; conservé.

## 2. Audit des éléments non classés / à revoir

Rejeu du moteur sur la catégorie d'origine (`LegacyCategoryMapperTest#replayOnRealExtractionMatchesDocumentedFigures`) :

| Résultat | Activités | Part |
|---|---:|---:|
| sans catégorie d'origine → fallback titre (`UNCLASSIFIED` si aucun mot-clé) | 41 696 | 88,1 % |
| classées par la catégorie d'origine | 854 | 1,8 % |
| `NEEDS_REVIEW` — emploi/formation (`EMPLOYMENT_DOMAIN`) | 1 578 | 3,3 % |
| `NEEDS_REVIEW` — valeur inconnue (`UNRECOGNIZED`) | 2 551 | 5,4 % |
| `NEEDS_REVIEW` — ambiguë (`AMBIGUOUS_CATEGORY` : patrimoine, visite, randonnée…) | 252 | 0,5 % |
| `NEEDS_REVIEW` — public (`AUDIENCE_VALUE`) | 232 | 0,5 % |
| `NEEDS_REVIEW` — format (`ACTIVITY_TYPE_VALUE`) | 175 | 0,4 % |

Motifs récurrents des 5 642 activités avec une catégorie d'origine (hors emploi au sens du moteur : 4 064) :

| Motif | Activités | Exemples | Traitement |
|---|---:|---|---|
| couvert par le référentiel après V43 | 1 471 | théâtre, spectacle, concert, patrimoine, JEP, jardins | — |
| emploi/formation non détecté par les jetons du moteur | 792 | #TousMobilisés, Création d'entreprise, S'informer, Les clés pour réussir | décision §4 |
| format (pas un domaine) | 328 | atelier, visite, rencontre, festival, conférence | **volontairement non classé** |
| public (dimension AUDIENCE) | 242 | jeune public, adulte, tout public, famille | **volontairement non classé** |
| noms propres et divers | 1 231 | lieux (Cameo Commanderie, Le Star), artistes, hashtags, événements nommés | non classable par mot-clé |

Thèmes métier qui manquaient et qui sont désormais couverts : **patrimoine/tourisme** (TOURISM n'avait aucun
mot-clé), **musique et spectacle vivant** (spectacle, musique, marionnettes, conte…), **jardins/nature**,
**cinéma**, **sports nommés**, **gastronomie** (cuisine, vin, terroir…), **vie citoyenne** (conseil municipal,
don du sang…).

## 3. Couverture mesurée (vocabulaire réel, hors emploi au sens du moteur)

| | Avant (V42) | Après (V43) |
|---|---:|---:|
| Mots-clés actifs | 60 | 272 |
| Activités dont la valeur est reconnue | 797 / 4 064 (19,6 %) | 1 471 / 4 064 (36,2 %) |
| … rapportées au vocabulaire classable (hors formats, publics, emploi non détecté) | 797 / 2 702 (29,5 %) | 1 471 / 2 702 (54,4 %) |

Répartition après V43 (activités, une activité peut compter dans plusieurs catégories) : CULTURE 1 302,
TOURISM 150, NATURE 75, LEISURE 43, SPORT 24, MARKETS 15, GASTRONOMY 5, CITIZEN 4, COMMERCE 1.
Ces chiffres sont recalculés à chaque build par `KeywordReferentialAuditIntegrationTest` (seuil de non-régression : 35 %).

Les catégories GASTRONOMY, COMMERCE, CITIZEN et MARKETS restent peu présentes **dans cette extraction** (agendas
majoritairement culturels et emploi) : leurs mots-clés ont été choisis pour les titres d'événements locaux
courants (marchés, braderies, repas, conseils municipaux…), à valider sur un réimport réel (§6).

## 4. Taxonomie : décision

**Aucune catégorie ajoutée.** Les 9 catégories V1 couvrent les domaines observés hors emploi/formation ; les formats
(atelier, visite, festival…) et les publics ne deviennent pas des catégories (règle de l'issue).

Deux candidats, **non ajoutés**, à arbitrer :

1. **Emploi / formation / insertion** — de loin le premier domaine non couvert : 1 578 activités détectées par le
   moteur (`EMPLOYMENT_DOMAIN`) + ~792 non détectées, soit ~2 370 activités, **42 % des activités ayant une
   catégorie d'origine**. Non ajouté : décision de fond en attente depuis CAT-007 (§11.5 de
   `CATEGORY_MIGRATION_MAP.md`), et le moteur force aujourd'hui `NEEDS_REVIEW` pour ce domaine — l'ajouter
   demanderait de modifier le moteur, hors périmètre ici.
2. **Sciences / numérique** — environ 60 activités (IA, numérique, robots, imprimante 3D, FabLab, chimie,
   astronomie, cybersécurité). Volume trop faible pour justifier une catégorie à ce stade.

## 5. Enrichissement (`V43`)

Données uniquement, rejouable (`ON CONFLICT DO NOTHING`, désactivation idempotente). Mots-clés métier précis ;
singulier/pluriel ; graphies réellement observées (`theatre`, `théatre`, `cinema`) ; expressions multi-mots quand
le mot seul est générique (`course à pied`, `tir à l'arc`, `office de tourisme`, `conseil municipal`, `bourse aux`).
`patrimoine`, `jep`, `pays d'art et d'histoire`, `ville d'art et d'histoire` désignent **à la fois** CULTURE et
TOURISM (modèle 1..N). Exclus volontairement : formats (`visite`, `visite guidée`, `atelier`, `festival`,
`rencontre`…), publics, termes trop génériques (`environnement`, `château` — « château gonflable » —, `histoire`,
`église`, `fête`). Liste complète : la migration.

## 6. Protocole de mesure sur un réimport réel (à exécuter avec accès OpenAgenda)

Un ré-import ne reclasse jamais une activité **existante** ; en revanche un élément en `NEEDS_REVIEW` ou
`UNCLASSIFIED` n'a pas d'activité et est re-décidé à chaque import. La mesure se fait donc ainsi :

```sql
-- 1. Avant le déploiement de V43 : photographie des statuts
SELECT status, reason, count(*) FROM classification_record GROUP BY 1, 2 ORDER BY 3 DESC;

-- 2. Déployer V43, relancer l'import (POST admin / tâche planifiée), puis refaire la requête 1.

-- 3. Éléments passés en AUTO_CLASSIFIED grâce au titre, avec les mots-clés reconnus (contrôle des faux positifs)
SELECT title, category_codes, matched_keywords
FROM classification_record
WHERE classification_source = 'TITLE_KEYWORD' AND status = 'AUTO_CLASSIFIED'
ORDER BY updated_at DESC LIMIT 200;

-- 4. Titres encore non classés les plus fréquents (prochains mots-clés candidats, à arbitrer)
SELECT title, count(*) FROM classification_record
WHERE status = 'UNCLASSIFIED' GROUP BY title ORDER BY 2 DESC LIMIT 100;
```

## 7. Constat sur le moteur (hors périmètre, pour un ticket séparé)

Les **catégories d'origine et tags** de la source sont traduits par `LegacyCategoryMapper`, une **liste fermée
codée en Java**, et non par `category_keyword`. L'enrichissement du référentiel en base n'améliore donc que le
fallback titre (88 % des activités de l'extraction), pas les 12 % qui ont une catégorie d'origine (où
`UNRECOGNIZED` représente 2 551 activités). Faire passer aussi les keywords source par `category_keyword`
unifierait le référentiel, mais modifie le moteur : à traiter dans un ticket dédié.
