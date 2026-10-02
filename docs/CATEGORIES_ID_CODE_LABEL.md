# ID / CODE / LABEL — convention d'harmonisation (CAT-005)

> Sprint CATEG, ticket CAT-005 (« Harmoniser IDs, codes, labels et
> traductions »). Ce document fixe, pour les 4 référentiels créés par
> CAT-001 à CAT-004 (`Category`, `ActivityType`, `Audience`,
> `OrganizerType`), la convention définitive décrite par
> `docs/05_Sprints/SPRINT_CATEG.md` et confirme qu'elle est effectivement
> appliquée de façon identique aux 4.

## 1. Séparation ID / CODE / LABEL

| | Rôle | Exemple | Où |
|---|---|---|---|
| **ID** | Identifiant technique interne, auto-généré, sans signification métier | `1`, `2`, `3`... | Colonne `id` de chaque table référentiel. **Jamais exposé en API** (voir §4). |
| **CODE** | Identité métier stable — obligatoire, unique, indépendante de la langue, du label, de la source et de l'ID | `CULTURE`, `FESTIVAL`, `FAMILY`, `ASSOCIATION` | Colonne `code`, format `SCREAMING_SNAKE_CASE`. C'est la seule valeur à utiliser dans toute logique métier, tout import, tout filtre. |
| **LABEL** | Texte affiché à l'utilisateur, toujours lié à une locale | `fr-FR` → *Culture*, `en-US` → *Culture*, `de-DE` → *Kultur* | Table `*_translation`, colonne `label` (+ `description`). **Jamais utilisé comme clé métier.** |

Les 4 référentiels respectent cette séparation à l'identique :

| Référentiel | Table | Table de traduction | Ticket |
|---|---|---|---|
| Catégories | `category` | `category_translation` | CAT-001 |
| Types d'activité | `activity_type` | `activity_type_translation` | CAT-002 |
| Publics | `audience` | `audience_translation` | CAT-003 |
| Organisateurs | `organizer_type` | `organizer_type_translation` | CAT-004 |

Chaque table référentiel : `id BIGSERIAL PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, active BOOLEAN, created_at, updated_at`. Chaque table de traduction : `id, <referentiel>_id (FK), locale, label, description, UNIQUE(<referentiel>_id, locale)`.

## 2. Stratégie de traduction

Aucun système i18n générique n'existait dans le projet avant ce sprint (vérifié : aucune table ni mécanisme de traduction ailleurs dans le code) — le modèle `*_TRANSLATION` ci-dessus a donc été créé une fois et appliqué aux 4 référentiels, plutôt que de créer quatre systèmes concurrents.

**Convention de locale (V1)** : `fr-FR` exclusivement. Jamais `fr`, `FR` ou `French` — ces formes ne sont utilisées nulle part dans le schéma ni dans le code. Les locales futures suivront le même format (`xx-XX`, BCP 47).

## 3. Fallback (résolution d'un label)

Chaîne de repli, implémentée identiquement pour les 4 référentiels :

1. locale demandée (correspondance exacte, insensible à la casse) ;
2. langue de base correspondante (ex. `fr-BE` → `fr`, comparée à la langue de base de chaque traduction disponible) ;
3. `fr-FR` ;
4. `CODE` (si aucune traduction n'existe pour l'entité).

Exemple du sprint, vérifié par test (`LocaleResolverTest`) :

```
locale demandée = es-ES
es-ES absent
↓
es absent
↓
fr-FR disponible
↓
"CULTURE" → "Culture"
```

**Implémentation centralisée** : les étapes 1 à 3 sont factorisées dans `com.locallife.backend.common.i18n.LocaleResolver` (classe utilitaire sans état), appelée par les 4 services (`CategoryService`, `ActivityTypeService`, `AudienceService`, `OrganizerTypeService`). Avant CAT-005, chaque service dupliquait une résolution à 2 maillons seulement (locale demandée → `fr-FR`), sans couvrir le maillon « langue de base » — c'est le changement de comportement apporté par ce ticket, en plus de la factorisation. L'étape 4 (repli sur le `CODE`) reste dans chaque service, qui est seul à connaître le code de l'entité résolue.

Le comportement est déterministe : à entrées égales (mêmes traductions, même locale demandée), la résolution renvoie toujours le même résultat — aucune dépendance à l'ordre d'insertion ou à un tri non spécifié (la recherche se fait par égalité de locale, pas par position).

## 4. Règle API : CODE, jamais ID ni structure de traduction

Les 4 contrôleurs (`CategoryController`, `ActivityTypeController`, `AudienceController`, `OrganizerTypeController`) exposent un DTO (`CategoryResponse`, etc.) contenant `code`, `label`, `description`, `active` — jamais l'`id` technique, jamais la liste brute des traductions. Le `label`/`description` exposés sont déjà résolus pour la locale demandée (paramètre `locale`, `fr-FR` par défaut) au moment de la réponse.

## 5. Seeder : uniquement des CODE

Il n'existe pas de composant Seeder dédié dans le projet (confirmé par l'audit CAT-000, `docs/CATEGORY_MIGRATION_MAP.md` §1.1) : les référentiels sont peuplés par migration Flyway (`V32` à `V35`). Ces migrations respectent la règle du ticket :

- les 4 tables référentiel sont peuplées par `INSERT ... (code) VALUES (...)`, avec un `id` auto-généré par la base — **aucun ID fixe n'est écrit en dur** ;
- les traductions sont **insérées séparément**, en rejoignant sur le `code` (`JOIN ... ON v.code = category.code`) — jamais sur une position dans la table ni sur un ID supposé.

Si un Seeder réutilisable est créé par un ticket ultérieur, il devra suivre la même règle (résoudre par `code`, jamais par ID fixe, LABEL, ou position).

## 6. Imports : les sources externes ne définissent jamais directement un label

Le pipeline cible décrit par le sprint (`SOURCE → RAW DATA → NORMALISATION → CLASSIFICATION LOCALIFE → VALIDATION → DEDUPLICATION → ACTIVITY`) n'est **pas encore implémenté** : CAT-001 à CAT-005 ne créent que les référentiels et leur résolution de traduction, pas la classification des imports. Ce point reste à vérifier lors de CAT-006 (nouveau modèle `Activity`) et CAT-008 (classification Seeder et imports), qui devront produire des `CODE` (jamais des labels) à partir des données sources. Il n'y a aujourd'hui aucune violation de cette règle, puisqu'aucun code d'import ne touche encore ces référentiels.

## 7. Critères d'acceptation CAT-005

* [x] ID, CODE et LABEL sont clairement séparés (§1, appliqué identiquement aux 4 référentiels).
* [x] Le CODE est indépendant de la langue (`SCREAMING_SNAKE_CASE`, jamais traduit).
* [x] Le LABEL n'est jamais utilisé comme identifiant (aucune comparaison sur `label` dans tout le code — vérifié par recherche sur les 4 modules).
* [x] Les traductions sont stockées séparément (tables `*_translation` dédiées — aucun système i18n générique préexistant à réutiliser, §2).
* [x] `fr-FR` est obligatoire en V1 (seule locale insérée par les migrations `V32`-`V35`).
* [x] La convention de locale est unique (`fr-FR` partout, jamais de forme alternative).
* [x] Le fallback est déterministe (§3, `LocaleResolver`, couvert par `LocaleResolverTest`).
* [x] Les traductions supplémentaires ne nécessitent pas de migration métier (ajouter une locale = une ligne `INSERT INTO *_translation`, aucune modification de `category`/`activity_type`/`audience`/`organizer_type` ni de code Java).
* [x] Le Seeder (migrations Flyway, en l'absence de composant dédié) utilise uniquement les codes (§5).
* [~] Les imports utilisent uniquement les codes : **non vérifiable à ce stade**, aucun code d'import ne produit encore de classification vers ces référentiels (§6) — à confirmer en CAT-006/CAT-008.
* [x] La documentation est mise à jour (ce document).
