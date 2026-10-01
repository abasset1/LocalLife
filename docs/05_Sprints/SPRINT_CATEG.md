SPRINT CATEG — Référentiel canonique des activités V1

1. Objectif du sprint

Mettre en place un référentiel canonique, stable, extensible et multilingue pour les activités LocalLife.

Le référentiel doit devenir la source unique de vérité pour :

- les catégories ;
- les types d’activités ;
- les publics ;
- les types d’organisateurs ;
- les imports ;
- le Seeder ;
- la base de données ;
- le backend ;
- l’API ;
- le frontend ;
- les tests ;
- les futures traductions.

L'objectif n'est pas simplement de remplacer quelques chaînes de caractères.

Il faut supprimer progressivement la dépendance actuelle aux catégories libres ou spécifiques aux sources et construire un modèle métier capable de représenter les activités futures de LocalLife.

---

2. Principe fondamental

LocalLife distingue quatre dimensions différentes.

CATEGORY

Ce dont relève principalement l'activité.

V1 :

- CULTURE
- GASTRONOMY
- MARKETS
- COMMERCE
- SPORT
- LEISURE
- NATURE
- TOURISM
- CITIZEN

ACTIVITY_TYPE

Ce qui se passe concrètement.

AUDIENCE

À qui l'activité s'adresse.

ORGANIZER_TYPE

Qui organise l'activité.

---

3. Règle fondamentale d'identification

Le système doit impérativement distinguer :

ID     = identifiant technique interne
CODE   = identité métier stable
LABEL  = représentation utilisateur localisée

Règle :

DB interne        → ID
Métier / API      → CODE
Interface         → LABEL traduit

Le "CODE" est la véritable identité métier.

Le "LABEL" ne doit jamais servir d'identifiant.

Le changement de langue ne doit jamais modifier le "CODE".

Exemple :

CODE = CULTURE

fr-FR → Culture
en-US → Culture
de-DE → Kultur

Le concept reste toujours :

CULTURE

---

CAT-000 — Audit de l'existant

Objectif

Établir précisément l'état actuel du système avant toute modification.

À auditer

Rechercher toutes les utilisations de :

- catégories ;
- types ;
- publics ;
- organisateurs ;
- labels ;
- IDs ;
- chaînes de catégories ;
- Seeder ;
- fixtures ;
- tests ;
- migrations ;
- modèles ;
- API ;
- frontend ;
- imports ;
- données historiques.

Rechercher notamment :

- catégories sous forme de "String";
- catégories libres ;
- valeurs comme "sport", "culture", etc. ;
- catégories propres à une source ;
- dépendances frontend à des chaînes ;
- tests dépendant de labels ;
- IDs utilisés directement dans du code métier.

Livrable

Créer :

docs/CATEGORY_MIGRATION_MAP.md

Le document doit préciser :

- situation actuelle ;
- endroits concernés ;
- anciennes valeurs ;
- correspondances possibles ;
- données ambiguës ;
- risques ;
- fichiers à modifier ;
- migrations nécessaires.

Règle

Ne pas modifier massivement le projet avant d'avoir compris l'existant.

Critères d'acceptation

- [ ] Toutes les occurrences pertinentes ont été identifiées.
- [ ] Le modèle actuel est documenté.
- [ ] Seeder et imports sont audités.
- [ ] Frontend et backend sont audités.
- [ ] Les tests concernés sont identifiés.
- [ ] Les données historiques sont prises en compte.
- [ ] "CATEGORY_MIGRATION_MAP.md" est créé.
- [ ] Les ambiguïtés sont explicitement listées.

---

CAT-001 — Référentiel des catégories V1

Objectif

Créer les catégories métier canoniques.

Catégories V1

CULTURE
GASTRONOMY
MARKETS
COMMERCE
SPORT
LEISURE
NATURE
TOURISM
CITIZEN

Règles

Les codes sont :

- stables ;
- uniques ;
- indépendants de la langue ;
- indépendants de la source ;
- indépendants de l'ID DB ;
- indépendants du label.

Format :

SCREAMING_SNAKE_CASE

Exemple :

CULTURE

et jamais :

culture
Culture
culture_locale

sauf lorsqu'il s'agit explicitement d'une donnée source ou d'un label.

Critères

- [ ] Les 9 catégories existent.
- [ ] Chaque catégorie possède un code stable.
- [ ] Les codes sont uniques.
- [ ] Aucune catégorie source n'est créée automatiquement.
- [ ] Les labels sont gérés selon CAT-005.
- [ ] Les descriptions suivent également la stratégie de traduction.

---

CAT-002 — Référentiel des types d'activités V1

Objectif

Séparer clairement la nature de l'activité de sa catégorie.

Types V1

Culture / découverte

CONCERT
SPECTACLE
FESTIVAL
EXPOSITION
CONFERENCE
RENCONTRE
VISITE
VISITE_GUIDEE

Transmission

ATELIER
INITIATION
FORMATION
STAGE

Marchés / commerce

MARCHE
MARCHE_PRODUCTEURS
MARCHE_ARTISANAL
MARCHE_SPECIALISE
VIDE_GRENIER
BROCANTE
FOIRE

Gastronomie

FOODTRUCK
DEGUSTATION
REPAS
ANIMATION_GASTRONOMIQUE

Sport / nature

COMPETITION
TOURNOI
MATCH
RANDONNEE
BALADE
SORTIE_NATURE

Animation / citoyen

FETE
ANIMATION
JEU
ACTION_COLLECTIVE
ACTION_SOLIDAIRE

Professionnel / institutionnel

PORTES_OUVERTES
SALON
FORUM
VENTE

Règles

Un type d'activité n'est jamais utilisé comme catégorie.

Exemple :

FESTIVAL

est un type.

La catégorie peut être :

CULTURE

Critères

- [ ] Tous les types sont référencés.
- [ ] Chaque type possède un code stable.
- [ ] Aucun type n'est utilisé comme catégorie.
- [ ] Les traductions suivent CAT-005.

---

CAT-003 — Référentiel des publics

Publics V1

ALL_PUBLIC
FAMILY
CHILDREN
TEENAGERS
ADULTS
SENIORS
PROFESSIONALS

Règles

"FAMILY" est un public.

Ce n'est jamais une catégorie.

Exemples :

Chasse au trésor enfant
CATEGORY = LEISURE
TYPE = JEU
AUDIENCE = CHILDREN, FAMILY

Atelier cuisine parent-enfant
CATEGORY = GASTRONOMY
TYPE = ATELIER
AUDIENCE = FAMILY

Critères

- [ ] Les publics sont indépendants des catégories.
- [ ] Les codes sont stables.
- [ ] Les traductions suivent CAT-005.

---

CAT-004 — Référentiel des organisateurs

Organisateurs V1

MUNICIPALITY
ASSOCIATION
COMPANY
BUSINESS
PROFESSIONAL
INDIVIDUAL
SPORTS_CLUB
TOURISM_OFFICE
INSTITUTION

Règles

Un organisateur n'est jamais une catégorie.

Exemple :

ASSOCIATION

ne signifie pas que l'activité appartient à une catégorie "association".

Critères

- [ ] Les types sont référencés.
- [ ] Les codes sont stables.
- [ ] Les traductions suivent CAT-005.
- [ ] Aucune logique métier ne dépend du label.

---

CAT-005 — Harmoniser IDs, codes, labels et traductions

Objectif

Définir définitivement la séparation entre identité technique, identité métier et représentation utilisateur.

Règle fondamentale

ID
↓
identifiant technique interne

CODE
↓
identité métier stable

LABEL
↓
texte affiché à l'utilisateur

CODE

Le code :

- est obligatoire ;
- est unique ;
- est stable ;
- est indépendant de la langue ;
- est indépendant du label ;
- est indépendant de la source ;
- est indépendant de l'ID.

Exemple :

CULTURE

LABEL

Le label est toujours lié à une locale.

Exemple :

CULTURE
├── fr-FR → Culture
├── en-US → Culture
└── de-DE → Kultur

Le label ne doit jamais être utilisé comme clé métier.

---

Stratégie de traduction

La traduction ne doit pas être stockée comme un unique label figé dans la table métier si cela empêche l'évolution multilingue.

Modèle logique recommandé :

CATEGORY
- id
- code
- active
- created_at
- updated_at

CATEGORY_TRANSLATION
- id
- category_id
- locale
- label
- description

Même principe pour :

ACTIVITY_TYPE_TRANSLATION
AUDIENCE_TRANSLATION
ORGANIZER_TYPE_TRANSLATION

Si le projet possède déjà un système i18n générique adapté, celui-ci doit être réutilisé plutôt que de créer quatre systèmes concurrents.

Locale

Le projet doit choisir une convention unique.

V1 :

fr-FR

Les autres langues pourront être ajoutées ultérieurement.

Ne jamais mélanger :

fr
fr-FR
FR
French

sans règle explicite.

---

Fallback

La résolution d'un label suit cet ordre :

1. locale demandée
2. langue de base correspondante
3. fr-FR
4. CODE

Exemple :

locale demandée = es-ES

es-ES absent
↓
es absent
↓
fr-FR disponible
↓
"CULTURE" → "Culture"

Le comportement doit être déterministe.

---

Seeder

Le Seeder doit travailler avec :

CODE

et jamais avec :

ID fixe
LABEL
position dans la table

Exemple :

CULTURE

permet de retrouver l'ID correspondant.

Les traductions sont insérées séparément.

---

Imports

Les sources externes ne doivent jamais définir directement les labels LocalLife.

Pipeline :

SOURCE
↓
RAW DATA
↓
NORMALISATION
↓
CLASSIFICATION LOCALIFE
↓
VALIDATION
↓
DEDUPLICATION
↓
ACTIVITY

La classification produit des "CODE".

Les labels sont résolus ensuite selon la locale.

---

Critères d'acceptation

- [ ] ID, CODE et LABEL sont clairement séparés.
- [ ] Le CODE est indépendant de la langue.
- [ ] Le LABEL n'est jamais utilisé comme identifiant.
- [ ] Les traductions sont stockées séparément ou via le système i18n existant.
- [ ] "fr-FR" est obligatoire en V1.
- [ ] La convention de locale est unique.
- [ ] Le fallback est déterministe.
- [ ] Les traductions supplémentaires ne nécessitent pas de migration métier.
- [ ] Le Seeder utilise uniquement les codes.
- [ ] Les imports utilisent uniquement les codes.
- [ ] La documentation est mise à jour.

---

CAT-006 — Nouveau modèle métier Activity

Objectif

Faire évoluer Activity vers le nouveau référentiel.

Le modèle cible doit permettre :

primaryCategory
secondaryCategories[]
activityTypes[]
audiences[]
organizerType

Règles

Une activité possède :

1 catégorie principale
0..N catégories secondaires
0..N types
0..N publics
0..1 type d'organisateur

Exemple

Festival communal :

primaryCategory = CULTURE

secondaryCategories =
[
  MARKETS,
  GASTRONOMY,
  LEISURE
]

activityTypes =
[
  FESTIVAL,
  CONCERT,
  MARCHE,
  FOODTRUCK,
  ANIMATION
]

audiences =
[
  ALL_PUBLIC,
  FAMILY
]

organizerType =
MUNICIPALITY

Critères

- [ ] Le modèle supporte toutes les dimensions.
- [ ] La catégorie principale est obligatoire.
- [ ] Les secondaires sont facultatives.
- [ ] Les types sont séparés des catégories.
- [ ] Les publics sont séparés des catégories.
- [ ] L'organisateur est séparé des catégories.
- [ ] Les relations sont cohérentes et sans doublons.

---

CAT-007 — Migration des données existantes

Objectif

Migrer les données existantes vers le référentiel canonique.

Exemples :

sport
→ SPORT

culture
→ CULTURE

Les données ambiguës ne doivent pas être inventées.

Elles doivent être signalées :

NEEDS_REVIEW

Règles

La migration doit être :

- reproductible ;
- traçable ;
- sans perte ;
- testable ;
- réversible si techniquement nécessaire.

Créer ou compléter :

docs/CATEGORY_MIGRATION_MAP.md

avec les correspondances.

Critères

- [ ] Les anciennes données sont analysées.
- [ ] Les correspondances sont documentées.
- [ ] Les données ambiguës sont identifiées.
- [ ] Aucune classification arbitraire n'est introduite.
- [ ] La migration est reproductible.
- [ ] Les données existantes sont conservées.

---

CAT-008 — Classification Seeder et imports

Objectif

Faire respecter le référentiel par toutes les sources de données.

Statuts de classification

UNCLASSIFIED
AUTO_CLASSIFIED
VALIDATED
NEEDS_REVIEW
REJECTED

Règles

Une source externe ne peut jamais créer automatiquement une nouvelle catégorie.

Une classification doit être réalisée vers les codes existants.

Exemple :

"Festival médiéval"

peut produire :

primaryCategory = CULTURE
activityTypes = [FESTIVAL, ANIMATION]

mais la source ne peut pas créer :

MEDIEVAL

comme nouvelle catégorie.

Cas ambigu

Si le système ne peut pas déterminer correctement :

NEEDS_REVIEW

et non une classification inventée.

Critères

- [ ] Seeder compatible avec les codes.
- [ ] Imports compatibles avec les codes.
- [ ] Classification centralisée.
- [ ] Aucun mapping source → catégorie directe non contrôlé.
- [ ] Statuts de classification présents.
- [ ] Cas ambigus gérés.
- [ ] Aucune catégorie créée automatiquement par une source.

---

CAT-009 — Frontend basé sur le référentiel

Objectif

Supprimer les catégories codées en dur dans le frontend.

Le frontend doit récupérer le référentiel depuis l'API ou le système i18n prévu.

Il doit :

- manipuler les codes ;
- afficher les labels traduits ;
- utiliser la locale utilisateur ;
- ne jamais utiliser le label comme identité.

Exemple :

CODE :
SPORT

Affichage :

fr-FR → Sport
en-US → Sport

Critères

- [ ] Aucun référentiel métier dupliqué dans le frontend.
- [ ] Les filtres utilisent les codes.
- [ ] Les labels affichés sont localisés.
- [ ] Le changement de langue ne modifie pas les codes.
- [ ] Les traductions ne sont pas dispersées dans les composants.

---

CAT-010 — Tests de couverture métier

Objectif

Vérifier que le référentiel couvre les activités réelles et futures de LocalLife.

Les tests doivent couvrir au minimum :

Culture

concert
festival
exposition
spectacle

Gastronomie

dégustation
repas
foodtruck

Marchés

marché
marché producteurs
vide-grenier
brocante
foire

Commerce

vente
portes ouvertes
salon

Sport

match
tournoi
compétition

Loisirs

jeu
fête
animation

Nature

randonnée
balade
sortie nature

Citoyen

action collective
action solidaire

Tourisme

visite
visite guidée

Famille

Vérifier que la famille est correctement représentée par :

AUDIENCE = FAMILY

et non par une catégorie.

Professionnels

Vérifier :

AUDIENCE = PROFESSIONALS

et/ou :

ORGANIZER_TYPE = PROFESSIONAL

selon le cas.

Hybride

Tester les activités pouvant appartenir à plusieurs univers.

Tests traduction

Tester obligatoirement :

- [ ] résolution "fr-FR" ;
- [ ] résolution d'une autre locale ;
- [ ] fallback langue ;
- [ ] fallback "fr-FR" ;
- [ ] fallback vers CODE ;
- [ ] unicité "(reference_id, locale)" ;
- [ ] changement de langue sans changement de CODE.

---

CAT-011 — Documentation et gouvernance

Objectif

Documenter le référentiel comme une partie officielle du domaine LocalLife.

Créer ou compléter :

docs/CATEGORY_REFERENCE.md

La documentation doit contenir :

- catégories ;
- types ;
- audiences ;
- organisateurs ;
- codes ;
- traductions ;
- règles de classification ;
- modèle de données ;
- stratégie de fallback ;
- API ;
- règles Seeder/imports ;
- gouvernance ;
- procédure d'ajout d'une nouvelle valeur.

Gouvernance

Aucune nouvelle catégorie ne doit être créée simplement parce qu'une source externe utilise un nouveau terme.

Une nouvelle catégorie nécessite une décision explicite du projet.

Critères

- [ ] Le référentiel est documenté.
- [ ] Les règles de classification sont documentées.
- [ ] La stratégie de traduction est documentée.
- [ ] La gouvernance est documentée.
- [ ] Les règles d'évolution sont documentées.

---

CAT-012 — Modèle DB et intégrité

Objectif

Implémenter proprement le référentiel en base.

Modèle cible

CATEGORY

id
code
active
created_at
updated_at

CATEGORY_TRANSLATION

id
category_id
locale
label
description

ACTIVITY_TYPE

id
code
active
created_at
updated_at

ACTIVITY_TYPE_TRANSLATION

id
activity_type_id
locale
label
description

AUDIENCE

id
code
active
created_at
updated_at

AUDIENCE_TRANSLATION

id
audience_id
locale
label
description

ORGANIZER_TYPE

id
code
active
created_at
updated_at

ORGANIZER_TYPE_TRANSLATION

id
organizer_type_id
locale
label
description

Si un système i18n générique existe déjà et répond au besoin, il doit être utilisé à la place de ces tables spécifiques.

Contraintes

Les codes doivent être uniques.

Les traductions doivent être uniques par :

(reference_id, locale)

Les relations doivent disposer de contraintes d'intégrité adaptées.

Les relations multiples ne doivent pas accepter de doublons.

Les références utilisées ne doivent pas être supprimées physiquement sans stratégie de migration.

Préférer :

active = false

pour désactiver une référence déjà utilisée.

Migrations

Les migrations doivent fonctionner :

- sur une base vide ;
- sur une base existante ;
- avec les données existantes ;
- sans perte de données.

Critères

- [ ] Modèle DB implémenté.
- [ ] Traductions correctement modélisées.
- [ ] Unicité des codes.
- [ ] Unicité des traductions par locale.
- [ ] Foreign keys ou équivalent.
- [ ] Intégrité des relations.
- [ ] Migration existante compatible.
- [ ] Base vide compatible.
- [ ] Données existantes préservées.

---

CAT-013 — Contrat API du référentiel

Objectif

Expose le référentiel au frontend et aux autres consommateurs.

Les routes doivent respecter les conventions API déjà utilisées dans le projet.

Exemple logique :

GET /api/v1/referential/categories
GET /api/v1/referential/activity-types
GET /api/v1/referential/audiences
GET /api/v1/referential/organizer-types

Locale

L'API doit pouvoir recevoir une locale.

Exemple :

GET /api/v1/referential/categories?locale=fr-FR

Réponse :

{
  "code": "CULTURE",
  "label": "Culture"
}

Le "code" reste identique quelle que soit la langue.

Exemple

{
  "code": "CULTURE",
  "label": "Culture",
  "active": true
}

Contrat Activity

Exemple :

{
  "primaryCategory": "CULTURE",
  "secondaryCategories": [
    "TOURISM"
  ],
  "activityTypes": [
    "FESTIVAL"
  ],
  "audiences": [
    "FAMILY"
  ],
  "organizerType": "ASSOCIATION"
}

L'API doit refuser

- code inconnu ;
- code inactif ;
- valeur appartenant au mauvais référentiel ;
- doublon ;
- catégorie principale absente ;
- valeur invalide.

Les erreurs doivent être structurées et explicites.

Si le projet utilise OpenAPI/Swagger, le contrat doit être mis à jour.

Critères

- [ ] Les endpoints existent.
- [ ] Les codes sont exposés.
- [ ] Les labels sont localisés.
- [ ] La locale est prise en compte.
- [ ] Le fallback est appliqué.
- [ ] Les codes invalides sont rejetés.
- [ ] Les valeurs inactives sont rejetées.
- [ ] Le contrat Activity utilise les codes.
- [ ] La documentation API est à jour.

---

4. Ordre d'exécution obligatoire

Claude doit respecter cet ordre :

CAT-000
↓
CAT-001
↓
CAT-002
↓
CAT-003
↓
CAT-004
↓
CAT-005
↓
CAT-006
↓
CAT-012
↓
CAT-007
↓
CAT-013
↓
CAT-008
↓
CAT-009
↓
CAT-010
↓
CAT-011

Pourquoi cet ordre ?

"CAT-000" établit l'état réel.

Les "CAT-001" à "CAT-004" définissent le vocabulaire métier.

"CAT-005" fixe la règle fondamentale :

ID ≠ CODE ≠ LABEL

et définit la traduction.

"CAT-006" adapte le modèle métier.

"CAT-012" implémente le modèle DB.

"CAT-007" peut alors migrer les données.

"CAT-013" expose proprement le nouveau modèle.

"CAT-008" adapte Seeder/imports.

"CAT-009" adapte le frontend.

Les derniers tickets consolident tests et documentation.

---

5. Definition of Done du sprint

Le sprint est terminé uniquement lorsque :

- [ ] Le référentiel canonique existe.
- [ ] Les catégories sont définies.
- [ ] Les types d'activités sont définis.
- [ ] Les publics sont définis.
- [ ] Les organisateurs sont définis.
- [ ] Les IDs sont séparés des codes.
- [ ] Les codes sont stables et indépendants de la langue.
- [ ] Les labels sont localisés.
- [ ] "fr-FR" est disponible pour toutes les références V1.
- [ ] Le fallback est testé.
- [ ] Le modèle DB est migré.
- [ ] Les données existantes sont migrées.
- [ ] Le Seeder utilise les codes.
- [ ] Les imports utilisent les codes.
- [ ] Les sources ne peuvent pas créer de nouvelles catégories.
- [ ] Le frontend utilise le référentiel.
- [ ] L'API expose les codes et labels localisés.
- [ ] Les tests métier passent.
- [ ] Les tests de traduction passent.
- [ ] Les tests de migration passent.
- [ ] La documentation est complète.
- [ ] Aucune catégorie libre ne reste utilisée comme identité métier.

---

6. Hors périmètre

Ce sprint ne doit pas introduire :

- nouvelles sources RSS ;
- nouveau moteur de recommandation ;
- géolocalisation ;
- recherche avancée ;
- fonctionnalités sociales ;
- scoring ;
- nouvelle logique métier sans rapport avec le référentiel ;
- catégories spécifiques à une source ;
- IDs DB utilisés comme API métier.

---

7. Règle finale du référentiel

La règle à conserver dans toute l'architecture LocalLife est :

«Le CODE identifie le concept.
La LOCALE détermine le libellé.
Le LABEL ne détermine jamais le concept.»