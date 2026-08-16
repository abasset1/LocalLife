# Audit de la qualité des données (LL-6001)

Audit des données produites par le Sprint 5 (`SPRINT_6.md` : « analyser
les données produites par le Sprint 5 avant d'ajouter de nouvelles
fonctionnalités »), avant le renforcement de la validation prévu en
LL-6002. Pas de code à ce stade — uniquement constat et documentation
des règles de validation à appliquer, comme `SOURCE_CONTRACT.md`
(LL-5001) l'a fait avant `LL-5002`.

⚠️ Limite de méthode : aucun accès à une base PostgreSQL réelle en
sandbox (même limitation que tous les tickets précédents — voir
`PROJECT_STATUS.md`). Cet audit est donc fait par relecture du code
(modèle `Activity`, migrations Flyway, `ActivityService`,
`NormalizationService`, `OpenAgendaCollector`) plutôt que par requête
sur des données réellement en base. Il identifie ce qui *peut*
actuellement entrer en base sans être bloqué, pas ce qui s'y trouve
effectivement — à confirmer par Alex avec un accès réel à
l'environnement.

Deux chemins alimentent `activity` aujourd'hui : la contribution
manuelle (`ActivityController#createActivity` → `ActivityService`,
source réservée `MANUAL`) et l'import OpenAgenda
(`OpenAgendaCollector` → `NormalizationService` → `ImportService`,
Sprint 5). Chacun est audité séparément ci-dessous, point par point,
selon la liste de `SPRINT_6.md`.

---

## Titres

* **Import (OpenAgenda → `NormalizationService`)** : `title` non
  vide/non nul est déjà exigé — une donnée collectée sans titre est
  rejetée (`Optional.empty()`), voir LL-5005. ✅ Couvert.
* **Contribution manuelle (`ActivityService#createActivity`)** : aucune
  validation. `request.title()` (venant du corps JSON, voir
  `ActivityController.CreateActivityRequest`) est transmis tel quel à
  `new Activity(...)`, y compris `null` ou une chaîne vide/blanche.
  **Problème identifié.**
* **Base** : colonne `title VARCHAR(255)` (`V2__create_activity_table.sql`),
  nullable, aucune contrainte `NOT NULL` ni `CHECK`. Une longueur au-delà
  de 255 caractères échouerait au niveau SQL (troncature ou erreur selon
  le driver), pas au niveau applicatif — aucun message d'erreur clair
  pour l'utilisateur dans ce cas. **Problème identifié.**

## Descriptions

* Champ optionnel partout (import et contribution), cohérent avec le
  modèle (`description` nullable sur `Activity`, `TEXT` en base — pas
  de limite de longueur). Aucune anomalie constatée : un champ vide/nul
  est un cas valide, pas une donnée invalide.
* Aucune validation de contenu (HTML/script, longueur excessive). Hors
  du périmètre strict de « qualité des données » au sens du sprint
  (relèverait plutôt de la sécurité) — mentionné pour mémoire, pas
  retenu comme problème de ce ticket.

## Dates

* **Import** : `startDate` non nul est exigé par `NormalizationService`
  (LL-5005) ; `endDate` reste optionnel, cohérent avec `Activity`. ✅
  Couvert pour la présence.
* **Contribution manuelle** : `startDate` fixée automatiquement à la
  date de soumission (`LocalDateTime.now()`), `endDate` toujours `null`
  — aucune saisie utilisateur possible pour l'instant, donc rien à
  valider ici (comportement documenté depuis LL-2012).
* **Aucune cohérence `startDate`/`endDate` vérifiée**, ni à l'import ni
  à la contribution : une donnée collectée avec `endDate` antérieure à
  `startDate` serait acceptée par `NormalizationService` (seule la
  non-nullité de `startDate` est testée) et persistée telle quelle.
  **Problème identifié** — impacte directement le filtre par date
  (LL-4005), qui utilise `COALESCE(end_date, start_date)` : une période
  inversée fausserait silencieusement les résultats de recherche.
* Aucune contrainte de plage (passé lointain, futur lointain) : une
  date manifestement aberrante (ex. `1900-01-01`, coquille probable
  côté source) serait acceptée sans signalement.

## Coordonnées (latitude/longitude)

* **Import** : bornes `-90/90` et `-180/180` déjà vérifiées par
  `NormalizationService` (dupliquées depuis `ActivityService`, décision
  documentée en LL-5005). ✅ Couvert pour la plage.
* **Contribution manuelle** : pas de vérification de plage applicative,
  mais les coordonnées proviennent systématiquement du géocodage
  Nominatim (`GeocodingService`, LL-3012), qui ne peut par construction
  renvoyer que des coordonnées réelles — aucun risque pratique
  identifié sur ce chemin.
* **Aucune vérification d'exploitabilité au-delà de la plage** : un
  couple `(0, 0)` (« Null Island », point au large du golfe de Guinée,
  souvent un artefact de donnée manquante mal convertie côté source
  externe) ou toute coordonnée valide mais situées à des milliers de
  kilomètres de Marseille passerait la validation actuelle sans
  signalement, alors que `SPRINT_6.md` demande explicitement de
  détecter les « activités sans localisation exploitable ».
  **Problème identifié** — pertinent en particulier pour l'import
  OpenAgenda, seule source externe actuelle.
* **Base** : colonnes `DOUBLE PRECISION` nullables
  (`V2__create_activity_table.sql`), sans `CHECK` sur la plage — la
  garantie de plage ne repose donc que sur le code applicatif, jamais
  sur la base elle-même. Incohérence avec le type Java `double`
  (primitif, jamais `null`) : `Activity.latitude()`/`longitude()` ne
  peuvent jamais être `null` en mémoire, mais la colonne SQL correspondante
  l'autorise — un accès direct à la base (hors chemin applicatif normal)
  pourrait produire une ligne dont la lecture échouerait. **Problème
  identifié** (incohérence de contrainte, pas un bug observé).

## Catégories

* Champ libre (`String`) des deux côtés, sans lien avec la table
  `category` (constat déjà documenté en LL-4004/LL-4007 — non repris
  ici comme nouveau problème, mais rappelé car directement pertinent
  pour l'audit de qualité).
* **Import** : `category` dérivée du premier mot-clé français
  (`keywords.fr[0]`) par `OpenAgendaCollector` (décision LL-5006) —
  aucune normalisation (casse, accents, singulier/pluriel) : deux
  événements portant respectivement les mots-clés `"Concert"` et
  `"concert"` produiraient deux valeurs de catégorie distinctes du
  point de vue du filtre `category` (comparaison exacte, LL-4004).
  **Problème identifié.**
* **Contribution manuelle** : aucune valeur imposée, l'utilisateur
  saisit une chaîne libre sans liste de référence — source probable de
  fragmentation (variantes orthographiques) constatée dans les données
  de démonstration elles-mêmes (`V3__insert_demo_activities.sql` :
  `"concert"`, `"marché"`, `"food truck"`, `"exposition"`, `"cinéma"`,
  déjà une liste fermée de fait mais jamais formalisée).

## URLs

* `CollectedActivity.sourceUrl` (LL-5004) est bien produit par
  `OpenAgendaCollector` (URL reconstruite, décision LL-5006), mais
  **`NormalizationService` ne le reporte jamais sur `Activity`** —
  `Activity` (record, LL-5008) n'a tout simplement **aucun champ pour
  une URL**. La donnée est collectée puis silencieusement perdue à la
  normalisation. **Problème identifié** — le plus significatif de cet
  audit : aucune URL source n'est donc jamais consultable depuis
  LocalLife pour une activité importée, alors que
  `docs/02_Architecture/COLLECTOR_OPERATIONS.md` et `SPRINT_5.md` ne
  signalent cette perte nulle part.
* Corollaire : impossible de valider un format d'URL aujourd'hui côté
  `Activity`, puisqu'aucune colonne n'existe pour la porter.

## Doublons

* **Entre imports d'une même source** : couvert par
  `DeduplicationService` (LL-5007) + index unique partiel
  `(source_id, import_key) WHERE import_key IS NOT NULL`
  (`V9__link_activity_to_source.sql`). ✅ Couvert.
* **Entre une contribution manuelle et une activité déjà importée (ou
  une autre contribution manuelle)** : **aucune détection.**
  `import_key` reste `null` pour toute activité manuelle (décision
  LL-5008), donc hors du périmètre de l'index unique partiel — deux
  contributions manuelles identiques (même titre, mêmes coordonnées,
  même date) créeraient deux lignes distinctes sans qu'aucun mécanisme
  ne le détecte. **Problème identifié.**
* **Entre deux sources externes différentes** décrivant le même
  événement réel (ex. si un second collecteur est ajouté un jour) :
  hors périmètre de `DeduplicationService`, qui clé uniquement par
  `source` + `externalId`/composite — non applicable aujourd'hui (un
  seul collecteur), mais à garder en tête pour un futur sprint
  (`SPRINT_6.md` exclut explicitement l'ajout d'un second collecteur).

## Activités sans localisation exploitable

* Les événements OpenAgenda sans `location` (lieu physique absent) sont
  déjà ignorés par `OpenAgendaCollector` (décision documentée en
  LL-5006) — avant même d'atteindre `NormalizationService`. ✅ Couvert
  pour l'absence totale de lieu.
* Pour le reste (coordonnées présentes mais non exploitables — voir
  section « Coordonnées » ci-dessus), aucune détection. Renvoie au même
  problème que « Null Island »/coordonnées hors zone pertinente.

---

## Synthèse des problèmes identifiés

| # | Problème | Chemin concerné | Sévérité constatée |
| - | -------- | ---------------- | ------------------- |
| 1 | `title` non validé (vide/nul accepté) | Contribution manuelle | Haute |
| 2 | Pas de longueur max applicative sur `title` (255 en base, non vérifiée en amont) | Contribution manuelle + import | Basse |
| 3 | Aucune cohérence `startDate`/`endDate` vérifiée | Import (et contribution si une date de fin est ajoutée un jour) | Moyenne |
| 4 | Aucune date manifestement aberrante détectée | Import | Basse |
| 5 | Aucune détection de coordonnées non exploitables (ex. `(0,0)`, hors zone pertinente) au-delà de la plage `-90/90`/`-180/180` | Import principalement | Moyenne |
| 6 | Colonnes `latitude`/`longitude` nullables en base malgré un type Java non nullable (`double`) | Base (les deux chemins) | Basse (incohérence de contrainte, pas de cas observé) |
| 7 | `category` non normalisée (casse/accents), fragmentation probable | Import + contribution | Moyenne |
| 8 | `sourceUrl` collecté puis perdu à la normalisation — aucune URL persistée sur `Activity` | Import | Haute |
| 9 | Aucune détection de doublon pour les contributions manuelles | Contribution manuelle | Moyenne |

Aucune modification du modèle métier n'a été faite dans ce ticket
(conforme à `AI_RULES.md` et au critère d'acceptation explicite de
LL-6001) — les problèmes ci-dessus sont des constats, pas des
correctifs.

---

## Règles de validation proposées pour LL-6002

À valider par Alex avant implémentation (LL-6002 dépend explicitement
de cet audit) :

* `title` : obligatoire, non vide après `trim()`, longueur ≤ 255
  caractères (alignée sur la colonne existante).
* `latitude`/`longitude` : bornes `-90/90` et `-180/180` déjà
  appliquées à l'import ; à étendre à la contribution manuelle par
  cohérence (même si le géocodage rend le cas peu probable en
  pratique) — voir critère « coordonnées valides » de `SPRINT_6.md`.
  L'exclusion de coordonnées non exploitables comme `(0, 0)` est une
  décision produit à trancher séparément (risque de faux positifs :
  une activité réellement située à `(0, 0)` est possible en théorie,
  même si peu probable pour Marseille) — signalée ici, pas tranchée.
* `startDate`/`endDate` : cohérence `endDate >= startDate` quand les
  deux sont renseignées.
* `category` : `SPRINT_6.md` demande une « catégorie valide si
  renseignée » — nécessite de trancher au préalable ce que signifie
  « valide » tant que `category` reste un champ libre sans lien avec
  la table `category` (question posée à Alex, pas de décision prise
  ici : introduire une liste fermée serait un changement de modèle
  métier hors périmètre de LL-6002 tel que cadré aujourd'hui).
* `url` (le cas échéant) : format d'URL basique si le champ est ajouté
  au modèle `Activity` — voir point ci-dessous.

Un point requiert une décision d'Alex avant LL-6002, en dehors du
strict renforcement de validation : faut-il, dans ce sprint ou un
suivant, ajouter un champ `url` à `Activity` pour cesser de perdre
`sourceUrl` à la normalisation (problème #8) ? `SPRINT_6.md` mentionne
« URL valide lorsqu'elle est fournie » parmi les critères de LL-6002,
ce qui suppose qu'un tel champ existe — actuellement, ce n'est pas le
cas.

---

## Hors périmètre de LL-6001

Conformément à `SPRINT_6.md` et `AI_RULES.md` (un ticket = une seule
responsabilité) :

* aucune modification du modèle `Activity` (y compris l'ajout d'un
  champ `url`) ;
* aucune migration Flyway ;
* aucun code de validation (LL-6002) ;
* aucun statut de modération (LL-6003) ;
* aucune décision tranchée sur les points signalés « à valider » —
  seulement identifiés et documentés, comme l'exige le critère
  d'acceptation de ce ticket.
