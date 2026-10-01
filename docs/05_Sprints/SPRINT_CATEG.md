SPRINT CATEG — Référentiel canonique des activités V1

Objectif du sprint

Construire et intégrer le référentiel canonique des activités LocalLife V1.

Le référentiel doit permettre à LocalLife de gérer les activités actuelles et futures sans dépendre des catégories fournies par les sources externes et sans multiplier les catégories au fur et à mesure de l'évolution du produit.

Le référentiel doit être cohérent sur l'ensemble de la chaîne :

Base de données
      ↓
Backend / domaine
      ↓
API
      ↓
Frontend
      ↓
Seeder / imports

Le contrat métier doit être identique à tous les niveaux.

---

CAT-000 — Audit de l'existant

Priorité

P0

Complexité

M

Objectif

Identifier précisément toutes les valeurs actuellement utilisées dans :

- la base de données ;
- le Seeder ;
- les fixtures ;
- les tests ;
- le backend ;
- le frontend ;
- les imports ;
- les données historiques ;
- les contrats API existants.

Travail demandé

Rechercher toutes les valeurs actuellement utilisées pour :

- les catégories ;
- les types d'activité ;
- les publics ;
- les organisateurs ;
- les éventuelles valeurs codées en dur.

Critères d'acceptation

- [ ] Toutes les valeurs actuellement utilisées sont identifiées.
- [ ] Les occurrences dans le code sont identifiées.
- [ ] Les valeurs présentes en base sont identifiées.
- [ ] Les valeurs du Seeder sont identifiées.
- [ ] Les fixtures/tests sont identifiés.
- [ ] Les contrats API utilisant "category" sont identifiés.
- [ ] Les valeurs inconnues ou ambiguës sont listées.
- [ ] Une table de migration ancien → nouveau est produite.
- [ ] Aucun changement de données n'est effectué automatiquement par ce ticket.

Livrable

docs/CATEGORY_MIGRATION_MAP.md

---

CAT-001 — Référentiel canonique des catégories V1

Priorité

P0

Complexité

M

Objectif

Créer la source de vérité unique des catégories LocalLife.

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

Définitions

CULTURE

Arts, musique, spectacles, littérature, patrimoine, expositions et manifestations culturelles.

GASTRONOMY

Cuisine, alimentation, gastronomie, dégustations, producteurs, restauration événementielle et foodtrucks.

MARKETS

Marchés, brocantes, vide-greniers, foires et manifestations principalement orientées vers la vente ou l'échange.

COMMERCE

Animations commerciales, commerces locaux, portes ouvertes commerciales et événements liés à la vie commerciale locale.

SPORT

Pratique sportive, compétitions, matchs, tournois et manifestations sportives.

LEISURE

Loisirs, jeux, hobbies, ateliers et activités récréatives ne relevant pas principalement d'une autre catégorie.

NATURE

Nature, environnement, jardins, plein air, découverte de la faune/flore et activités de découverte naturelle.

TOURISM

Tourisme, découverte du territoire, visites guidées, circuits et activités destinées à découvrir un lieu ou son patrimoine.

CITIZEN

Vie citoyenne, solidarité, actions collectives, sensibilisation et initiatives locales.

Règles

- Les codes sont immuables une fois utilisés.
- Les libellés sont séparés des codes.
- Une source externe ne peut jamais créer automatiquement une catégorie.
- Une catégorie ne doit pas être créée simplement parce qu'un nouveau type d'activité apparaît.
- Le référentiel est centralisé.
- Aucun composant ne doit maintenir sa propre copie de la liste.

Critères d'acceptation

- [ ] Les 9 catégories existent.
- [ ] Chaque catégorie possède un code stable.
- [ ] Chaque catégorie possède un libellé.
- [ ] Chaque catégorie possède une description.
- [ ] Les catégories sont uniques.
- [ ] Les catégories sont accessibles au backend.
- [ ] Tests présents.

---

CAT-002 — Référentiel des types d'activité

Priorité

P0

Complexité

M

Objectif

Définir ce qui se passe réellement lors d'une activité.

Types V1

CONCERT
SPECTACLE
FESTIVAL
EXPOSITION
CONFERENCE
RENCONTRE
VISITE
VISITE_GUIDEE

ATELIER
INITIATION
FORMATION
STAGE

MARCHE
MARCHE_PRODUCTEURS
MARCHE_ARTISANAL
MARCHE_SPECIALISE
VIDE_GRENIER
BROCANTE
FOIRE

FOODTRUCK
DEGUSTATION
REPAS
ANIMATION_GASTRONOMIQUE

COMPETITION
TOURNOI
MATCH
RANDONNEE
BALADE
SORTIE_NATURE

FETE
ANIMATION
JEU
ACTION_COLLECTIVE
ACTION_SOLIDAIRE

PORTES_OUVERTES
SALON
FORUM
VENTE

Règle

Un type d'activité n'est pas une catégorie.

Exemples :

FOODTRUCK ≠ CATEGORY
RANDONNEE ≠ CATEGORY
VIDE_GRENIER ≠ CATEGORY

Critères d'acceptation

- [ ] Tous les types V1 sont définis.
- [ ] Chaque type possède un code stable.
- [ ] Les types sont centralisés.
- [ ] Les types ne dépendent d'aucune source externe.
- [ ] Tests unitaires présents.

---

CAT-003 — Référentiel des publics

Priorité

P1

Complexité

S

Objectif

Définir les publics auxquels une activité peut s'adresser.

Valeurs V1

ALL_PUBLIC
FAMILY
CHILDREN
TEENAGERS
ADULTS
SENIORS
PROFESSIONALS

Règles

"FAMILY" est un public et non une catégorie.

Une activité peut avoir plusieurs publics.

Critères d'acceptation

- [ ] Les valeurs sont centralisées.
- [ ] Chaque valeur possède un code stable.
- [ ] Une activité peut avoir plusieurs publics.
- [ ] "FAMILY" n'est jamais utilisé comme catégorie.
- [ ] Tests présents.

---

CAT-004 — Référentiel des organisateurs

Priorité

P1

Complexité

S

Objectif

Définir le type d'organisateur d'une activité.

Valeurs V1

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

L'organisateur ne détermine pas automatiquement la catégorie.

Exemple :

Association
+
Concert
=
CULTURE

et non :

ASSOCIATION

comme catégorie.

Critères d'acceptation

- [ ] Les valeurs sont centralisées.
- [ ] Chaque valeur possède un code stable.
- [ ] Une association n'est pas automatiquement une catégorie.
- [ ] Un commerce n'impose pas automatiquement "COMMERCE".
- [ ] Tests présents.

---

CAT-005 — Harmoniser IDs, codes et libellés

Priorité

P0

Complexité

M

Objectif

Définir une convention unique pour les IDs techniques, codes métier et libellés affichés.

Convention

ID
→ identifiant technique interne

CODE
→ identité métier stable

LABEL
→ représentation utilisateur

Exemple

id: 42
code: CULTURE
label: Culture
description: Arts, spectacles, musique, patrimoine et manifestations culturelles
active: true

Règles ID

- L'ID est uniquement technique.
- Il ne porte aucune signification métier.
- Il ne constitue pas un contrat API.
- Il reste stable pendant la durée de vie de l'entité.
- Il peut différer entre environnements.

Règles CODE

Les codes doivent être :

- uniques ;
- stables ;
- en majuscules ;
- en "SCREAMING_SNAKE_CASE" ;
- sans accent ;
- sans espace ;
- indépendants du libellé ;
- indépendants de la source ;
- indépendants de l'ID technique.

Exemples :

CULTURE
GASTRONOMY
VISITE_GUIDEE
MARCHE_PRODUCTEURS
ALL_PUBLIC
ASSOCIATION

Règles LABEL

Le libellé :

- est destiné à l'utilisateur ;
- peut être traduit ;
- peut évoluer ;
- ne constitue jamais une clé métier ;
- ne doit pas être utilisé dans les comparaisons métier.

Règle globale

DATABASE → ID
BUSINESS → CODE
UI → LABEL
API → CODE

Seeder

Le Seeder doit rechercher les entités par code et ne jamais supposer :

CULTURE = ID 1
SPORT = ID 5

API

Le contrat métier utilise les codes :

{
  "primaryCategory": "CULTURE",
  "activityTypes": ["FESTIVAL"],
  "audiences": ["FAMILY"],
  "organizerType": "ASSOCIATION"
}

et non les IDs techniques ou les libellés.

Critères d'acceptation

- [ ] La distinction ID/code/label est documentée.
- [ ] Une convention unique est adoptée.
- [ ] Les codes sont uniques.
- [ ] Les codes sont stables.
- [ ] Les codes sont en "SCREAMING_SNAKE_CASE".
- [ ] Les codes ne contiennent ni accents ni espaces.
- [ ] Les libellés sont indépendants des codes.
- [ ] Les IDs restent techniques.
- [ ] Les IDs ne sont pas utilisés comme contrat métier API.
- [ ] Le Seeder résout les IDs à partir des codes.
- [ ] Les tests couvrent ID/code/label.

---

CAT-006 — Évolution du modèle Activity

Priorité

P0

Complexité

L

Dépendances

CAT-001
CAT-002
CAT-003
CAT-004
CAT-005

Objectif

Faire évoluer "Activity" afin d'intégrer le nouveau référentiel.

Modèle cible

Activity
├── primaryCategory
├── secondaryCategories[]
├── activityTypes[]
├── audiences[]
├── organizerType
└── ...

Contraintes

1 catégorie principale
0..N catégories secondaires
0..N types d'activité
0..N publics
0..1 type d'organisateur

Exemple

title: "Festival médiéval"

primaryCategory: CULTURE

secondaryCategories:
  - TOURISM
  - MARKETS

activityTypes:
  - FESTIVAL

audiences:
  - ALL_PUBLIC
  - FAMILY

organizerType: ASSOCIATION

Critères d'acceptation

- [ ] Une catégorie principale est obligatoire pour une activité classifiée.
- [ ] Les catégories secondaires sont supportées.
- [ ] Les types d'activité sont supportés.
- [ ] Les publics sont supportés.
- [ ] L'organisateur est supporté.
- [ ] Les contraintes d'intégrité sont définies.
- [ ] Les tests existants sont adaptés.
- [ ] Aucun nouveau code métier ne dépend d'une catégorie libre.

---

CAT-007 — Migration des données existantes

Priorité

P0

Complexité

M

Dépendances

CAT-000
CAT-001
CAT-005
CAT-006
CAT-012

Objectif

Migrer les anciennes catégories vers le référentiel canonique sans perte de données.

Règles

- Aucun événement ne doit être supprimé.
- Une valeur inconnue ne doit pas être transformée arbitrairement.
- Toute migration doit être reproductible.
- Les données historiques doivent rester traçables.
- Les mappings doivent utiliser les nouveaux codes.

Exemple

sport
→ SPORT

culture
→ CULTURE

Cas ambigu :

autre
→ NEEDS_REVIEW

Critères d'acceptation

- [ ] Toutes les anciennes valeurs ont un mapping ou un statut explicite.
- [ ] Les mappings utilisent les codes canoniques.
- [ ] Les données existantes sont migrées.
- [ ] Aucun événement n'est perdu.
- [ ] Les valeurs ambiguës sont détectables.
- [ ] Les données migrées sont vérifiées.
- [ ] Les tests de migration passent.
- [ ] La migration est documentée.
- [ ] La migration respecte les contraintes de base.

---

CAT-008 — Classification des imports et du Seeder

Priorité

P0

Complexité

L

Dépendances

CAT-001
CAT-002
CAT-003
CAT-004
CAT-005
CAT-006

Pipeline cible

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

Statuts

UNCLASSIFIED
AUTO_CLASSIFIED
VALIDATED
NEEDS_REVIEW
REJECTED

Règle fondamentale

Une source externe ne définit jamais directement une catégorie LocalLife.

Exemple :

Source :
"Marché gourmand nocturne"

Classification :

primaryCategory: GASTRONOMY

secondaryCategories:
  - MARKETS

activityTypes:
  - MARCHE

Critères d'acceptation

- [ ] Le Seeder utilise le référentiel.
- [ ] Les imports utilisent le référentiel.
- [ ] Une source externe ne peut pas créer une catégorie.
- [ ] Les codes canoniques sont utilisés.
- [ ] Les cas ambigus sont détectables.
- [ ] Les classifications sont traçables.
- [ ] Les tests de classification passent.
- [ ] Aucun composant ne possède une liste parallèle.

---

CAT-009 — Frontend basé sur le référentiel

Priorité

P1

Complexité

M

Dépendances

CAT-005
CAT-006
CAT-013

Objectif

Supprimer la saisie libre des catégories et utiliser le référentiel via le contrat API.

Règles

Le frontend :

affiche → LABEL
manipule → CODE

Exemple

L'utilisateur voit :

Culture

L'application manipule :

CULTURE

Critères d'acceptation

- [ ] La catégorie n'est plus saisie librement.
- [ ] Les catégories proviennent de l'API.
- [ ] Les catégories secondaires utilisent le même référentiel.
- [ ] Les libellés affichés sont français.
- [ ] Les codes canoniques sont envoyés au backend.
- [ ] Les IDs internes ne sont pas utilisés comme contrat frontend.
- [ ] Le frontend ne contient pas de liste codée en dur.
- [ ] Les tests frontend passent.

---

CAT-010 — Tests de couverture des futurs cas

Priorité

P0

Complexité

M

Objectif

Vérifier que le référentiel couvre les futures activités prévues par LocalLife.

Culture

Concert
Théâtre
Exposition
Festival médiéval
Rencontre avec un auteur

Gastronomie

Foodtruck
Marché gourmand
Dégustation
Atelier cuisine

Marchés

Marché traditionnel
Marché artisanal
Vide-grenier
Brocante

Commerce

Animation commerciale
Portes ouvertes

Sport

Match de football
Tournoi
Course
Randonnée

Loisirs

Atelier poterie
Jeux de société
Stage photo

Nature

Sortie nature
Observation des oiseaux
Visite d'un jardin

Associations / citoyen

Forum des associations
Collecte solidaire
Action collective

Professionnels

Formation professionnelle
Salon professionnel
Rencontre professionnelle

Tourisme

Visite guidée
Circuit touristique
Découverte du patrimoine

Famille

Spectacle enfant
Atelier parent/enfant
Chasse au trésor familiale

Événements hybrides

Festival + marché + foodtrucks
Fête de village
Journée vélo
Marché de producteurs
Portes ouvertes + dégustation

Critères d'acceptation

- [ ] Tous les cas sont représentables.
- [ ] Aucun cas ne nécessite une catégorie ad hoc.
- [ ] Les événements hybrides supportent plusieurs catégories.
- [ ] Le public est séparé de la catégorie.
- [ ] L'organisateur est séparé de la catégorie.
- [ ] Le type d'activité est séparé de la catégorie.
- [ ] Les tests sont automatisés.

---

CAT-011 — Documentation et gouvernance

Priorité

P1

Complexité

S

Objectif

Empêcher la dérive future du référentiel.

Documentation

Créer ou mettre à jour :

docs/CATEGORY_REFERENCE.md

Le document doit contenir :

1. les catégories ;
2. les types d'activité ;
3. les publics ;
4. les organisateurs ;
5. les conventions ID/code/label ;
6. les règles de classification ;
7. les règles de multi-catégories ;
8. les règles d'ajout d'une nouvelle valeur ;
9. les règles de migration ;
10. les exemples d'événements hybrides ;
11. le modèle de données ;
12. le contrat API.

Règle d'ajout d'une catégorie

Avant de créer une nouvelle catégorie :

1. Le besoin peut-il être représenté par un type ?
2. Le besoin peut-il être représenté par un public ?
3. Le besoin peut-il être représenté par un organisateur ?
4. Le besoin peut-il être représenté par une catégorie existante ?
5. Une nouvelle catégorie est-elle réellement nécessaire ?

Une nouvelle catégorie doit rester une exception.

Critères d'acceptation

- [ ] La documentation existe.
- [ ] Les catégories sont documentées.
- [ ] Les types sont documentés.
- [ ] Les publics sont documentés.
- [ ] Les organisateurs sont documentés.
- [ ] ID/code/label sont documentés.
- [ ] Les règles de classification sont documentées.
- [ ] Les règles de gouvernance sont documentées.

---

CAT-012 — Intégrité et modèle de base de données

Priorité

P0

Complexité

L

Dépendances

CAT-001
CAT-002
CAT-003
CAT-004
CAT-005
CAT-006

Objectif

Faire de la base de données une garantie d'intégrité du référentiel.

Modèle logique

CATEGORY
--------
id
code
label
description
active
created_at
updated_at

ACTIVITY_TYPE
-------------
id
code
label
description
active
created_at
updated_at

AUDIENCE
--------
id
code
label
active

ORGANIZER_TYPE
--------------
id
code
label
active

Relations Activity

ACTIVITY
   │
   ├── primary_category_id
   │
   ├── activity_categories
   │       └── category_id
   │
   ├── activity_types
   │       └── activity_type_id
   │
   ├── activity_audiences
   │       └── audience_id
   │
   └── organizer_type_id

Contraintes obligatoires

CATEGORY.code UNIQUE
ACTIVITY_TYPE.code UNIQUE
AUDIENCE.code UNIQUE
ORGANIZER_TYPE.code UNIQUE

Relations :

(activity_id, category_id) UNIQUE
(activity_id, activity_type_id) UNIQUE
(activity_id, audience_id) UNIQUE

Intégrité

- Une activité ne peut pas référencer une catégorie inexistante.
- Une activité ne peut pas référencer un type inexistant.
- Une activité ne peut pas référencer un public inexistant.
- Une activité ne peut pas référencer un organisateur inexistant.
- Les doublons sont interdits.
- Les valeurs de référentiel utilisées ne doivent pas être supprimées physiquement sans stratégie d'historisation.

Privilégier :

active = false

pour désactiver une valeur.

Migrations

- [ ] Les migrations sont versionnées.
- [ ] Elles fonctionnent sur une base vide.
- [ ] Elles fonctionnent sur une base existante.
- [ ] Elles respectent l'ordre des dépendances.
- [ ] Elles ne détruisent aucune donnée existante.
- [ ] Les contraintes sont testées.

Critères d'acceptation

- [ ] Les tables/références existent.
- [ ] Les codes sont uniques.
- [ ] Les foreign keys ou contraintes équivalentes sont en place.
- [ ] Les doublons sont impossibles.
- [ ] Les valeurs invalides sont rejetées.
- [ ] Les migrations sont testées.
- [ ] Les tests d'intégrité passent.
- [ ] Les IDs restent techniques et les codes métier restent indépendants des IDs.

---

CAT-013 — Contrat API du référentiel

Priorité

P0

Complexité

L

Dépendances

CAT-001
CAT-002
CAT-003
CAT-004
CAT-005
CAT-006
CAT-012

Objectif

Exposer un contrat API stable permettant au frontend et aux futurs clients d'utiliser le référentiel sans dupliquer les valeurs.

Endpoints

Les endpoints doivent respecter le conventionnement API déjà présent dans le projet.

Exemple cible :

GET /api/v1/referential/categories
GET /api/v1/referential/activity-types
GET /api/v1/referential/audiences
GET /api/v1/referential/organizer-types

Réponse

Exemple :

{
  "code": "CULTURE",
  "label": "Culture",
  "description": "Arts, spectacles, musique, patrimoine et manifestations culturelles",
  "active": true
}

Contrat Activity

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

Validation

L'API doit refuser :

- un code inconnu ;
- une valeur inactive ;
- un doublon ;
- une valeur appartenant au mauvais référentiel ;
- une valeur vide lorsqu'elle est obligatoire ;
- une catégorie principale inexistante.

Exemple :

{
  "code": "INVALID_REFERENCE_VALUE",
  "field": "primaryCategory",
  "value": "FOODTRUCK",
  "message": "FOODTRUCK is not a valid category"
}

Règle importante

Les IDs internes de la base ne constituent pas le contrat métier de l'API.

Le contrat est basé sur les codes.

Documentation

Le contrat doit être documenté dans OpenAPI/Swagger ou dans le mécanisme documentaire déjà utilisé par le projet.

Critères d'acceptation

- [ ] Les endpoints du référentiel existent.
- [ ] Les réponses sont documentées.
- [ ] Les codes métier sont stables.
- [ ] Les IDs internes ne sont pas nécessaires au frontend.
- [ ] Les valeurs inconnues sont rejetées.
- [ ] Les valeurs inactives sont rejetées.
- [ ] Les valeurs appartenant au mauvais référentiel sont rejetées.
- [ ] Les erreurs sont structurées.
- [ ] Le contrat OpenAPI/équivalent est à jour.
- [ ] Les tests API valides passent.
- [ ] Les tests API invalides passent.
- [ ] Les événements hybrides sont testés.

---

ORDRE D'EXÉCUTION

CAT-000
Audit de l'existant
        ↓
CAT-001
Catégories V1
        ↓
CAT-002
Types d'activité
        ↓
CAT-003
Publics
        ↓
CAT-004
Organisateurs
        ↓
CAT-005
IDs / Codes / Libellés
        ↓
CAT-006
Modèle Activity
        ↓
CAT-012
Base de données
        ↓
CAT-007
Migration
        ↓
CAT-013
Contrat API
        ↓
CAT-008
Seeder + imports
        ↓
CAT-009
Frontend
        ↓
CAT-010
Tests de couverture
        ↓
CAT-011
Documentation / gouvernance

---

Dépendances

CAT-000 → CAT-001

CAT-001 → CAT-005
CAT-002 → CAT-005
CAT-003 → CAT-005
CAT-004 → CAT-005

CAT-001..005 → CAT-006

CAT-005 → CAT-012
CAT-006 → CAT-012

CAT-001..006 → CAT-007

CAT-001..006 → CAT-013
CAT-012 → CAT-013

CAT-005 → CAT-008
CAT-006 → CAT-008
CAT-012 → CAT-008
CAT-013 → CAT-008

CAT-005 → CAT-009
CAT-012 → CAT-009
CAT-013 → CAT-009

CAT-001..013 → CAT-010
CAT-001..013 → CAT-011

---

Definition of Done du sprint

Le sprint CATEG est terminé uniquement lorsque :

Référentiel

- [ ] Les 9 catégories V1 sont définies.
- [ ] Les types d'activité sont définis.
- [ ] Les publics sont définis.
- [ ] Les organisateurs sont définis.
- [ ] Les codes sont stables et documentés.

IDs / Codes / Libellés

- 