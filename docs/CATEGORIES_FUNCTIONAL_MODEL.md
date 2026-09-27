# CATEGORIES_FUNCTIONAL_MODEL — Modèle fonctionnel de classification, visibilité et acteurs locaux

> Document de référence produit préparatoire à CAT-001.
>
> **Statut : validé conceptuellement comme base de travail — aucune implémentation technique n'est définie ici.**
>
> Ce document formalise les décisions prises après les premiers retours utilisateurs et l'étude PRO-000 des acteurs locaux. Il complète `CATEGORIES_AUDIT.md` et doit être lu avant toute modification du référentiel de catégories.

---

## 1. Problème produit

Les premiers retours utilisateurs montrent que LocalLife présente actuellement trop d'événements de natures différentes. Même si les catégories sont regroupées, cela ne réduit pas à lui seul le volume d'événements visibles.

Le problème à résoudre est donc double :

1. **classifier correctement** les contenus entrant dans LocalLife ;
2. **contrôler ce qui est visible par défaut** afin que l'utilisateur ne soit pas noyé dans un flux trop large.

La réduction du nombre de catégories est donc nécessaire, mais elle n'est pas suffisante.

### Principe directeur

> **LocalLife ne doit pas afficher automatiquement tout ce qu'il sait classifier.**

Un contenu peut être parfaitement compris et enregistré dans LocalLife tout en étant hors du flux par défaut.

---

## 2. Décision fondamentale : catégorie ≠ type de contenu ≠ visibilité

Ces trois notions doivent rester distinctes.

### 2.1 Catégorie

La catégorie est une **grande famille compréhensible par l'utilisateur**.

Exemples :

- Musique
- Culture & arts
- Gastronomie
- Sport
- Famille & enfants
- Marchés & brocantes
- Nature & plein air

Le nombre de catégories principales doit rester volontairement limité, avec une cible de l'ordre d'une dizaine. La liste définitive relève de CAT-001 et doit être issue de l'audit réel des données.

### 2.2 Type / sous-type

Le type décrit plus précisément ce qu'est le contenu.

Exemples :

```text
Gastronomie
├── foodtruck
├── restaurant
├── bar
├── dégustation
└── atelier cuisine
```

ou :

```text
Culture & arts
├── musée
├── exposition
├── conférence
├── patrimoine
└── visite guidée
```

Les types peuvent être beaucoup plus nombreux que les catégories principales. Ils ne doivent pas devenir automatiquement des filtres principaux.

### 2.3 Politique de visibilité

Chaque type de contenu doit disposer d'une politique explicite :

- `DEFAULT` : visible dans le flux général ;
- `OPT_IN` : conservé et disponible, mais visible uniquement lorsque l'utilisateur l'autorise ou le recherche ;
- `PENDING` : classification ou validation insuffisante ; non publié dans le flux utilisateur.

**Une nouvelle catégorie ou un nouveau type ne doit jamais devenir visible par défaut uniquement parce qu'il a été ajouté au référentiel.**

---

## 3. Classification à l'intégration

Tout contenu entrant doit passer par une étape de classification LocalLife, quelle que soit sa source.

Sources possibles :

- import externe (OpenAgenda, etc.) ;
- contribution d'un utilisateur ;
- publication par un acteur local ;
- création ou correction par un administrateur.

Flux conceptuel :

```text
CONTENU ENTRANT
      │
      ▼
Identification / normalisation
      │
      ▼
Classification LocalLife
      │
      ├── catégorie + type suffisamment déterminés
      │          │
      │          ▼
      │     politique de visibilité
      │          │
      │       ┌──┴──────┐
      │       ▼         ▼
      │   DEFAULT     OPT_IN
      │
      └── classification insuffisante
                  │
                  ▼
               PENDING
```

### Règle

> **Si LocalLife ne peut pas rattacher de manière suffisamment fiable le contenu à une catégorie/type connu, le contenu reste `PENDING` au lieu d'être publié automatiquement.**

`PENDING` ne signifie pas « mauvais événement ». Cela signifie : « LocalLife n'a pas encore suffisamment d'information ou de règle pour décider de son classement/publication ».

---

## 4. `PENDING` et `OPT_IN` sont deux notions différentes

Cette distinction est obligatoire.

### PENDING

Le système ne sait pas encore quoi faire du contenu.

Exemple :

> « Animation insolite du village »

avec des informations insuffisantes pour déterminer la catégorie.

Le contenu attend une classification, une correction ou une décision administrative.

### OPT_IN

Le système sait parfaitement ce qu'est le contenu, mais il n'est pas souhaitable de l'ajouter au flux général.

Exemple :

> Foodtruck → Gastronomie → Foodtruck → `OPT_IN`

Le contenu est valide et publié dans LocalLife, mais un utilisateur ne souhaitant pas de gastronomie ne doit pas le subir dans son flux.

---

## 5. Exemple : foodtruck, restaurant et bar

Le cas foodtruck est un cas de référence pour le modèle.

### Classification

```text
Foodtruck
→ catégorie : Gastronomie
→ type : FOOD_TRUCK
→ visibilité : OPT_IN
```

```text
Restaurant
→ catégorie : Gastronomie
→ type : RESTAURANT
→ visibilité : OPT_IN
```

```text
Bar
→ catégorie : Gastronomie / Vie locale selon le référentiel final
→ type : BAR
→ visibilité : OPT_IN
```

Le fait d'ajouter ultérieurement restaurant ou bar ne doit donc pas augmenter automatiquement le flux général.

Les contenus existent dans LocalLife, sont recherchables et peuvent être activés dans les préférences utilisateur.

---

## 6. Préférences utilisateur

La consultation simple de LocalLife reste gratuite et accessible sans compte.

Pour les utilisateurs ayant un compte, LocalLife peut mémoriser des préférences de catégories et, lorsque cela est pertinent, de types.

### Comportement cible

#### Utilisateur non connecté

Affichage des contenus dont la politique est `DEFAULT`, selon les autres filtres actifs.

#### Utilisateur connecté sans préférence

Même comportement par défaut, avec possibilité de proposer une sélection de centres d'intérêt.

#### Utilisateur connecté avec préférences

Le flux est filtré selon ses catégories préférées et, à terme, ses types `OPT_IN` explicitement activés.

#### Toujours possible

L'utilisateur doit pouvoir demander une vue plus large, par exemple :

> Toutes les catégories

Le système ne doit pas enfermer l'utilisateur dans ses préférences.

---

## 7. Les préférences ne remplacent pas les filtres manuels

Trois mécanismes doivent rester distincts :

1. **préférences personnelles** : ce que l'utilisateur aime habituellement ;
2. **filtres temporaires** : ce qu'il souhaite voir maintenant ;
3. **recherche** : ce qu'il cherche explicitement.

Exemple : un utilisateur qui n'a jamais activé « Foodtruck » doit tout de même pouvoir rechercher un foodtruck précis.

---

## 8. Acteurs locaux : ne pas limiter le modèle aux « professionnels »

Le terme fonctionnel retenu est **acteur local**.

Un acteur local peut être :

- entreprise ;
- commerce ;
- restaurant ;
- foodtruck ;
- association ;
- indépendant ;
- organisateur ;
- institution ;
- collectivité ;
- équipement culturel ou sportif ;
- particulier organisateur.

Le compte utilisateur n'est pas nécessairement l'acteur lui-même.

### Principe

```text
UTILISATEUR
     │
     │ gère
     ▼
ACTEUR LOCAL
```

Un acteur local peut être géré par plusieurs utilisateurs et un utilisateur peut, à terme, gérer plusieurs acteurs.

Ce principe doit être préservé même si la première version n'implémente qu'un cas simple.

---

## 9. Besoins communs des acteurs locaux

Un acteur local peut avoir besoin de gérer :

### Identité

- nom ;
- description ;
- image/logo ;
- coordonnées ;
- site web ;
- liens externes.

### Classification

- catégorie ;
- type / sous-type ;
- éventuellement tags complémentaires.

### Localisation

- un lieu permanent ;
- plusieurs établissements ;
- des lieux variables selon les occurrences ;
- aucun lieu permanent.

### Temporalité

- événement ponctuel ;
- plusieurs occurrences ;
- récurrence ;
- saison ;
- exception à une récurrence ;
- annulation ou déplacement.

### Publication

- brouillon ;
- en attente ;
- publié ;
- suspendu / rejeté ;
- archivé.

---

## 10. Familles d'acteurs et besoins spécifiques

### 10.1 Restauration mobile

Exemples : foodtruck, camion pizza, crêpier.

Besoin principal : « où et quand suis-je présent ? »

Besoins :

- fiche d'activité ;
- occurrences multiples ;
- lieux variables ;
- récurrence ;
- annulation / changement ponctuel ;
- calendrier de présence.

### 10.2 Établissement fixe

Exemples : restaurant, bar, musée, salle de spectacle, salle de sport.

Besoin principal : « voici mon établissement et ce qui s'y passe ».

Besoins :

- fiche permanente ;
- lieu permanent ;
- horaires d'ouverture ou informations pratiques à terme ;
- événements associés ;
- éventuellement programmation.

### 10.3 Programmateur

Exemples : théâtre, salle de concert, centre culturel.

Besoin principal : gérer une programmation comprenant plusieurs représentations.

Besoins :

- événement logique ;
- plusieurs occurrences ;
- plusieurs horaires ;
- exceptions ;
- page de programmation.

### 10.4 Organisateur

Exemples : festival, agence événementielle, organisateur privé.

Besoin principal : gérer plusieurs événements, éventuellement répartis sur plusieurs lieux et plusieurs jours.

### 10.5 Association

Peut cumuler :

- activités récurrentes ;
- événements ponctuels ;
- sorties ;
- compétitions ;
- actions locales.

Peut nécessiter plusieurs utilisateurs gestionnaires.

### 10.6 Collectivité / institution

Peut centraliser les contenus de plusieurs structures :

- mairie ;
- office de tourisme ;
- médiathèque ;
- musée ;
- équipements sportifs ;
- associations partenaires.

Ce cas doit être compatible à terme avec une gestion multi-acteurs ou multi-contributeurs.

### 10.7 Commerce

Peut publier :

- événements ;
- animations ;
- ateliers ;
- participations à des marchés ;
- autres contenus commerciaux à étudier séparément.

Le concept de « promotion commerciale » n'est **pas** introduit dans ce modèle tant qu'un besoin produit spécifique n'est pas validé.

### 10.8 Particulier organisateur

Un particulier peut créer un événement local sans être une entreprise.

Le modèle ne doit donc pas imposer un statut professionnel pour publier un contenu autorisé.

---

## 11. Distinction fixe / mobile / multi-sites / sans lieu propre

Le modèle fonctionnel doit couvrir les quatre cas :

### Acteur fixe

```text
Acteur
 └── lieu permanent
      └── événements / activités
```

### Acteur mobile

```text
Acteur
 └── activité
      ├── occurrence → lieu A
      ├── occurrence → lieu B
      └── occurrence → lieu C
```

### Acteur multi-sites

```text
Acteur
 ├── établissement A
 ├── établissement B
 └── établissement C
```

### Organisateur sans lieu propre

```text
Acteur
 └── événement
      └── lieu propre à l'événement
```

**Une adresse permanente ne doit donc jamais être une obligation universelle de l'acteur.**

---

## 12. Événement, activité et occurrence

Le modèle riche déjà présent dans LocalLife permet de représenter plusieurs horaires, lieux et occurrences. Le modèle fonctionnel confirme la distinction suivante :

### Activité / événement logique

Ce que l'utilisateur identifie comme le contenu :

> « Cours de yoga débutant »

### Occurrence

Une réalisation concrète :

> Mardi 18h30, salle X.

Une même activité peut donc posséder plusieurs occurrences.

Exemple :

```text
Cours de yoga débutant
 ├── 02/10 — 18h30 — Salle A
 ├── 09/10 — 18h30 — Salle A
 ├── 16/10 — 18h30 — Salle A
 └── 23/10 — annulé
```

Cette distinction doit être conservée dans CAT-001 et les tickets ultérieurs.

---

## 13. Récurrence

Les acteurs locaux peuvent avoir besoin de publier des activités récurrentes :

- tous les mardis ;
- tous les week-ends ;
- tous les premiers dimanches du mois ;
- pendant une saison ;
- selon un calendrier particulier.

Le système doit permettre à terme de représenter :

```text
série récurrente
       │
       ├── occurrence normale
       ├── occurrence annulée
       └── occurrence déplacée
```

Le modèle actuel des `Schedule` / `Occurrence` est la base technique existante à prendre en compte ; ce document ne demande pas de la modifier dans CAT-001.

---

## 14. Import externe et publication directe doivent converger

LocalLife peut recevoir un contenu depuis :

```text
OpenAgenda
     │
Autre source
     │
Utilisateur
     │
Acteur local
     │
Administrateur
     │
     ▼
MODÈLE LOCALIFE
```

La source du contenu doit rester traçable, mais il ne doit pas exister deux modèles fonctionnels incompatibles :

- « événement importé » ;
- « événement professionnel ».

Ils doivent pouvoir utiliser les mêmes concepts de catégorie, type, lieu, occurrence, statut et visibilité.

---

## 15. Cas de revendication d'une fiche importée

Un acteur local peut découvrir qu'une fiche existe déjà parce qu'elle a été importée depuis une source externe.

Cas cible :

```text
Fiche importée
      │
      ▼
« Revendiquer cette fiche »
      │
      ▼
Vérification
      │
      ▼
Acteur local associé
      │
      ▼
Gestion par l'acteur
```

Ce mécanisme n'est pas à implémenter dans CAT-001, mais le référentiel ne doit pas empêcher cette évolution.

---

## 16. Politique de visibilité : règles de référence

| Situation | Catégorie connue | Type connu | Visibilité cible |
|---|---:|---:|---|
| Concert correctement classifié | Oui | Oui | DEFAULT |
| Exposition correctement classifiée | Oui | Oui | DEFAULT |
| Foodtruck correctement classifié | Oui | Oui | OPT_IN |
| Restaurant correctement classifié | Oui | Oui | OPT_IN |
| Bar correctement classifié | Oui | Oui | OPT_IN |
| Type nouveau mais clairement identifié | Oui | Oui | à décider explicitement, jamais implicite |
| Catégorie inconnue | Non | éventuellement | PENDING |
| Informations insuffisantes | Non | Non | PENDING |
| Contenu rejeté par modération | Oui/Non | Oui/Non | REJECTED / non publié |

`DEFAULT` et `OPT_IN` sont des politiques de visibilité métier ; `PENDING` et `REJECTED` sont des états de publication/modération. Ils ne doivent pas être confondus dans le modèle final.

---

## 17. Catégories principales : principe de conception

CAT-001 doit rechercher un référentiel principal :

- court ;
- stable ;
- compréhensible ;
- indépendant des sources externes ;
- indépendant des noms de types détaillés ;
- compatible avec les préférences utilisateur.

La liste actuelle proposée dans `SPRINT_CATEG.md` est une **base de travail**, pas une décision définitive.

CAT-001 doit pouvoir :

- conserver une catégorie ;
- fusionner plusieurs catégories ;
- supprimer une catégorie principale devenue inutile ;
- créer une catégorie si les données réelles le justifient ;
- déplacer un concept vers un type/sous-type plutôt que vers une catégorie principale.

### Règle d'or

> **Une catégorie principale existe parce qu'elle constitue un choix utile pour l'utilisateur, pas simplement parce qu'une source de données possède cette valeur.**

---

## 18. Règle spécifique pour `OTHER`

`OTHER` ne doit pas devenir un moyen de publier automatiquement tout ce qui n'est pas compris.

Il doit être défini explicitement dans CAT-001/CAT-002.

En particulier :

- contenu non classifiable → `PENDING` si une décision est nécessaire ;
- contenu réellement admissible mais ne justifiant pas une catégorie dédiée → `OTHER` seulement si la politique produit l'autorise ;
- `OTHER` ne doit pas devenir un « fourre-tout » permanent.

---

## 19. Tags et données sources

La classification LocalLife ne doit pas détruire les informations fournies par la source.

Exemple :

```text
Source OpenAgenda
keywords = ["festival", "musique", "été", "plein air"]

→ catégorie LocalLife = MUSIC
→ type = FESTIVAL / MUSIC_EVENT selon les règles
→ tags conservés = festival, musique, été, plein air
```

La normalisation sert à classifier ; elle ne doit pas remplacer les données originales utiles à la recherche ou à l'audit.

---

## 20. Conséquences pour CAT-001

CAT-001 ne doit donc pas se limiter à produire une liste `code / nom / description`.

Il doit définir au minimum :

1. le référentiel des catégories principales ;
2. la frontière entre catégorie et type/sous-type ;
3. les cas qui ne doivent pas devenir des catégories principales ;
4. la politique de visibilité associée aux types identifiés ;
5. la place de `PENDING` ;
6. la place de `OTHER` ;
7. les catégories visibles par défaut ;
8. les catégories/types pouvant être `OPT_IN` ;
9. les règles permettant de faire évoluer le référentiel sans augmenter automatiquement le flux par défaut ;
10. les besoins de compatibilité avec les acteurs locaux et leurs contenus.

### CAT-001 ne doit pas encore

- implémenter le modèle SQL ;
- modifier `Activity` ;
- modifier le pipeline d'import ;
- modifier les filtres frontend ;
- implémenter les préférences utilisateur ;
- implémenter les comptes acteurs locaux ;
- implémenter la revendication de fiches.

Ces sujets appartiennent à des tickets ultérieurs.

---

## 21. Décisions fonctionnelles à considérer comme acquises

Les décisions suivantes sont désormais la base de conception :

1. Le nombre de catégories principales doit rester limité.
2. La réduction des catégories ne suffit pas à réduire le volume visible.
3. Les types/sous-types permettent de conserver une classification fine sans multiplier les catégories principales.
4. Un contenu peut être correctement classifié mais `OPT_IN`.
5. Un contenu insuffisamment classifié reste `PENDING`.
6. `PENDING` n'est pas synonyme de `OPT_IN`.
7. Les foodtrucks, restaurants, bars et autres types similaires ne doivent pas être affichés par défaut simplement parce qu'ils existent dans la base.
8. Les utilisateurs connectés peuvent disposer de préférences mémorisées.
9. Les préférences ne remplacent ni la recherche ni les filtres temporaires.
10. Un acteur local peut être une entreprise, association, institution, collectivité, organisateur ou particulier autorisé.
11. Un acteur local peut avoir plusieurs lieux et plusieurs contenus.
12. Un compte utilisateur et un acteur local sont deux concepts distincts.
13. Une activité peut avoir plusieurs occurrences.
14. Une occurrence peut avoir son propre lieu.
15. Les contenus importés et créés directement doivent converger vers le même modèle fonctionnel LocalLife.
16. La revendication d'une fiche importée est une évolution à préserver.
17. L'ajout d'une nouvelle catégorie ou d'un nouveau type ne doit jamais entraîner implicitement son affichage dans le flux général.

---

## 22. Questions volontairement laissées à CAT-001 ou aux tickets suivants

Les points suivants ne sont pas tranchés ici afin d'éviter de transformer ce document préparatoire en spécification technique prématurée :

- liste exacte des catégories principales ;
- liste exacte des types/sous-types ;
- politique `DEFAULT` / `OPT_IN` de chaque type ;
- modèle technique exact de Category ;
- relation technique Category ↔ Activity ;
- statut exact de publication et de modération ;
- interface de sélection des préférences ;
- règles de vérification d'un acteur local ;
- rôles et permissions multi-utilisateurs ;
- modèle commercial des comptes acteurs ;
- traitement des promotions/offres commerciales ;
- gestion détaillée des fiches revendiquées.

---

## 23. Références du dépôt

Ce document doit être lu avec :

- `docs/CATEGORIES_AUDIT.md` — état réel du système actuel ;
- `docs/05_Sprints/SPRINT_CATEG.md` — tickets CAT ;
- `docs/02_Architecture/FOOD_TRUCK_CONTRACT.md` — état et historique du modèle foodtruck ;
- `docs/ADR-0005-import-pipeline-enrichment.md` — enrichissement du pipeline d'import ;
- `docs/ADR-0010-cas-reels-programmation.md` — occurrences, récurrences et exceptions ;
- `docs/05_Sprints/SPRINT_11.md` — modèle événementiel riche et cas réels de programmation.

---

## 24. Résumé opérationnel

LocalLife doit évoluer d'un système où une chaîne `activity.category` détermine directement ce que l'utilisateur voit vers un système où :

```text
SOURCE / ACTEUR LOCAL
          │
          ▼
      CLASSIFICATION
          │
     ┌────┴─────┐
     ▼          ▼
 CATEGORY      TYPE
     │          │
     └────┬─────┘
          ▼
 POLITIQUE DE VISIBILITÉ
      │       │       │
      ▼       ▼       ▼
   DEFAULT  OPT_IN  PENDING
      │       │
      └──┬────┘
         ▼
  PRÉFÉRENCES / FILTRES
         │
         ▼
   CONTENU AFFICHÉ
```

**La classification répond à la question « qu'est-ce que c'est ? ».**

**La politique de visibilité répond à « qui doit le voir par défaut ? ».**

**Les préférences utilisateur répondent à « qu'est-ce que cette personne souhaite voir ? ».**

Cette séparation constitue la base fonctionnelle à respecter lors de la rédaction de CAT-001 et des tickets suivants.
