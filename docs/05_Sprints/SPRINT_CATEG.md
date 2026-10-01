SPRINT CATEG — Référentiel canonique des activités V1

Objectif du sprint

Construire et intégrer le référentiel canonique des activités LocalLife V1.

Le référentiel doit permettre à LocalLife de gérer les activités actuelles et futures sans dépendre des catégories fournies par les sources externes et sans multiplier les catégories au fur et à mesure de l'évolution du produit.

Le sprint doit notamment couvrir :

- culture ;
- gastronomie ;
- foodtrucks ;
- marchés ;
- brocantes ;
- vide-greniers ;
- commerce ;
- sport ;
- loisirs ;
- nature ;
- associations ;
- professionnels ;
- tourisme ;
- famille ;
- vie citoyenne ;
- événements hybrides.

Problème actuel

Le modèle actuel utilise une catégorie essentiellement sous forme de chaîne libre.

Exemple :

category = "sport"

Cette approche pose plusieurs problèmes :

- aucune liste canonique réellement imposée ;
- risque de variantes ("sport", "SPORT", "sports", etc.) ;
- impossibilité de distinguer catégorie, type d'activité, public et organisateur ;
- dépendance potentielle aux catégories des sources externes ;
- difficulté à gérer les événements hybrides ;
- difficulté à faire évoluer le référentiel sans casser les données existantes ;
- risque de duplication de la logique entre Seeder, backend, imports et frontend.

Principe directeur

Le référentiel doit séparer quatre dimensions :

CATEGORY
ACTIVITY_TYPE
AUDIENCE
ORGANIZER_TYPE

CATEGORY

Répond à :

«De quoi parle principalement l'activité ?»

ACTIVITY_TYPE

Répond à :

«Qu'est-ce qui se passe ?»

AUDIENCE

Répond à :

«Pour qui ?»

ORGANIZER_TYPE

Répond à :

«Qui organise ?»

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
- les éventuels imports ;
- les données historiques.

L'objectif est de connaître l'écart exact entre l'existant et le nouveau référentiel.

Travail demandé

Rechercher toutes les valeurs actuellement utilisées pour les catégories.

Exemples :

sport
culture
loisir
marché
...

Identifier également les catégories implicites ou codées en dur dans le frontend ou les tests.

Critères d'acceptation

- [ ] Toutes les valeurs actuellement utilisées sont identifiées.
- [ ] Les occurrences dans le code sont identifiées.
- [ ] Les valeurs présentes en base sont identifiées.
- [ ] Les valeurs du Seeder sont identifiées.
- [ ] Les fixtures/tests sont identifiés.
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

- Les IDs sont immuables.
- Les libellés sont séparés des IDs.
- Une source externe ne peut jamais créer automatiquement une catégorie.
- Une catégorie ne doit pas être créée simplement parce qu'un nouveau type d'activité apparaît.
- Le référentiel est centralisé.
- Aucun composant ne doit maintenir sa propre copie de la liste.

Critères d'acceptation

- [ ] Les 9 catégories existent.
- [ ] Chaque catégorie possède un ID stable.
- [ ] Chaque catégorie possède un libellé.
- [ ] Chaque catégorie possède une description.
- [ ] Les catégories sont uniques.
- [ ] Le référentiel est accessible au backend.
- [ ] Le référentiel est accessible au Seeder.
- [ ] Les tests du référentiel sont présents.

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

Règles

Un type d'activité n'est pas une catégorie.

Exemples :

FOODTRUCK ≠ CATEGORY
RANDONNEE ≠ CATEGORY
VIDE_GRENIER ≠ CATEGORY
FAMILLE ≠ CATEGORY
ASSOCIATION ≠ CATEGORY

Critères d'acceptation

- [ ] Tous les types V1 sont définis.
- [ ] Les IDs sont stables.
- [ ] Les types sont centralisés.
- [ ] Les types ne sont pas dupliqués dans le frontend.
- [ ] Les types ne sont pas dépendants d'une source externe.
- [ ] Tests unitaires présents.

---

CAT-003 — Référentiel des publics

Priorité

P1

Complexité

S

Objectif

Définir le public auquel une activité s'adresse.

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

Exemple :

audiences:
  - FAMILY
  - CHILDREN

Critères d'acceptation

- [ ] Les valeurs sont centralisées.
- [ ] Une activité peut avoir plusieurs publics.
- [ ] "FAMILY" n'est jamais utilisé comme catégorie.
- [ ] Les valeurs inconnues ne sont pas inventées.
- [ ] Tests présents.

---

CAT-004 — Référentiel des organisateurs

Priorité

P1

Complexité

S

Objectif

Définir le type d'organisateur.

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
- [ ] Une association n'est pas automatiquement classée comme catégorie.
- [ ] Un commerce n'impose pas automatiquement "COMMERCE".
- [ ] Tests présents.

---

CAT-005 — Évolution du modèle Activity

Priorité

P0

Complexité

L

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

Une activité possède :

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

- [ ] Une catégorie principale est obligatoire lorsque l'activité est classifiée.
- [ ] Les catégories secondaires sont supportées.
- [ ] Les types d'activité sont supportés.
- [ ] Les publics sont supportés.
- [ ] L'organisateur est supporté.
- [ ] Les contraintes d'intégrité sont définies.
- [ ] Les tests existants sont adaptés.
- [ ] Les nouveaux composants n'utilisent plus une catégorie libre arbitraire.

---

CAT-006 — Migration des données existantes

Priorité

P0

Complexité

M

Dépendances

CAT-000
CAT-001
CAT-005

Objectif

Migrer les anciennes catégories vers le référentiel canonique sans perte de données.

Exemple

sport
→ SPORT

culture
→ CULTURE

Cas ambigu

ancienne_valeur = "autre"
→ NEEDS_REVIEW

Règles

- Aucun événement ne doit être supprimé.
- Une valeur inconnue ne doit pas être transformée arbitrairement.
- Toute migration doit être reproductible.
- La migration doit être testée.
- Les anciennes données doivent rester traçables pendant la transition.

Critères d'acceptation

- [ ] Toutes les anciennes valeurs ont un mapping ou un statut explicite.
- [ ] Les données existantes sont migrées.
- [ ] Aucun événement n'est perdu.
- [ ] Les valeurs inconnues sont détectables.
- [ ] Les données migrées sont vérifiées.
- [ ] Les tests de migration passent.
- [ ] La migration est documentée.

---

CAT-007 — Classification des imports et du Seeder

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

Faire du référentiel LocalLife la source de vérité utilisée par les données générées et importées.

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

Règle fondamentale

Une source externe ne définit jamais directement une catégorie LocalLife.

Exemple :

Source :
"Marché gourmand nocturne"

peut être classifié :

primaryCategory: GASTRONOMY

secondaryCategories:
  - MARKETS

activityTypes:
  - MARCHE

Statut de classification

UNCLASSIFIED
AUTO_CLASSIFIED
VALIDATED
NEEDS_REVIEW
REJECTED

Gestion de l'incertitude

Si les données ne permettent pas une classification fiable :

classificationStatus = NEEDS_REVIEW

Le système ne doit pas inventer.

Critères d'acceptation

- [ ] Le Seeder utilise le référentiel.
- [ ] Les imports utilisent le référentiel.
- [ ] Aucune catégorie source n'est injectée directement comme catégorie LocalLife.
- [ ] Les cas ambigus sont détectables.
- [ ] Les classifications sont traçables.
- [ ] Les tests de classification passent.
- [ ] Aucun composant ne possède sa propre liste parallèle de catégories.

---

CAT-008 — Frontend basé sur le référentiel

Priorité

P1

Complexité

M

Dépendances

CAT-001
CAT-002
CAT-003
CAT-005

Objectif

Supprimer la saisie libre des catégories.

Règle

Le frontend doit utiliser le référentiel.

La catégorie ne doit plus être saisie comme texte libre.

Comportement attendu

Catégorie
[ Culture ▼ ]

Les valeurs transmises au backend sont les IDs canoniques.

Critères d'acceptation

- [ ] La catégorie n'est plus saisie librement.
- [ ] Les catégories proviennent du référentiel.
- [ ] Les catégories secondaires utilisent le même référentiel.
- [ ] Les libellés affichés sont français.
- [ ] Les IDs envoyés au backend sont canoniques.
- [ ] Le frontend ne contient pas de liste codée en dur.
- [ ] Les tests frontend passent.

---

CAT-009 — Tests de couverture des futurs cas

Priorité

P0

Complexité

M

Objectif

Vérifier que le référentiel couvre les activités futures prévues par LocalLife.

Cas obligatoires

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

Match
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

Formation
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

CAT-010 — Documentation et gouvernance du référentiel

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
5. les règles de classification ;
6. les règles de multi-catégories ;
7. les règles d'ajout d'une nouvelle valeur ;
8. les règles de migration ;
9. les exemples d'événements hybrides.

Règle d'ajout d'une catégorie

Avant de créer une nouvelle catégorie, vérifier :

1. Le besoin peut-il être représenté par un type ?
2. Le besoin peut-il être représenté par un public ?
3. Le besoin peut-il être représenté par un organisateur ?
4. Le besoin peut-il être représenté par une catégorie existante ?
5. Une nouvelle catégorie est-elle réellement nécessaire ?

Une nouvelle catégorie doit rester une exception.

Critères d'acceptation

- [ ] La documentation existe.
- [ ] Les règles sont documentées.
- [ ] Les catégories sont documentées.
- [ ] Les types sont documentés.
- [ ] Les publics sont documentés.
- [ ] Les organisateurs sont documentés.
- [ ] Les règles de gouvernance sont documentées.

---

ORDRE D'EXÉCUTION

Les tickets doivent être réalisés dans cet ordre :

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
Modèle Activity
        ↓
CAT-006
Migration
        ↓
CAT-007
Seeder + imports
        ↓
CAT-008
Frontend
        ↓
CAT-009
Tests de couverture
        ↓
CAT-010
Documentation / gouvernance

Dépendances principales

CAT-000 → CAT-001
CAT-001 → CAT-005
CAT-002 → CAT-005
CAT-003 → CAT-005
CAT-004 → CAT-005
CAT-005 → CAT-006
CAT-005 → CAT-007
CAT-005 → CAT-008
CAT-001..004 → CAT-009
CAT-006..009 → CAT-010

---

Definition of Done du sprint

Le sprint CATEG est terminé uniquement lorsque :

- [ ] Le référentiel canonique V1 est implémenté.
- [ ] Les 9 catégories sont définies.
- [ ] Les types d'activité sont définis.
- [ ] Les publics sont définis.
- [ ] Les organisateurs sont définis.
- [ ] "Activity" utilise le nouveau modèle.
- [ ] Les anciennes catégories ont été auditées.
- [ ] Les anciennes données ont été migrées.
- [ ] Le Seeder utilise le référentiel.
- [ ] Les imports utilisent le référentiel.
- [ ] Le frontend utilise le référentiel.
- [ ] Les événements hybrides sont correctement représentables.
- [ ] Les cas futurs définis dans CAT-009 sont couverts par des tests.
- [ ] Les cas ambigus ne sont pas classifiés arbitrairement.
- [ ] La documentation est à jour.
- [ ] Aucun composant ne maintient une liste parallèle de catégories.
- [ ] La CI passe intégralement.
- [ ] Aucune donnée existante n'a été perdue.

Hors périmètre

Ce sprint ne doit pas :

- ajouter de nouvelles sources RSS ;
- développer le moteur de recommandation ;
- modifier la géolocalisation ;
- modifier le système de recherche ;
- développer les fonctionnalités sociales ;
- créer des catégories spécifiques à une source externe ;
- créer une catégorie pour chaque nouveau type d'activité.

Résultat attendu

À la fin du sprint CATEG, LocalLife dispose d'un référentiel unique, stable, extensible et indépendant des sources, utilisable par :

Seeder
Imports RSS/API
Backend
Base de données
Frontend
Administration
Tests

Le référentiel devient la source de vérité unique de la classification des activités LocalLife.