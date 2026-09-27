# SPRINT CATEG — Référentiel de catégories et visibilité LocalLife

**Statut :** À faire
**Priorité :** Haute
**Domaine :** Classification / qualité des données / UX
**Version :** 1.0

---

# 1. Objectif du sprint

Construire le référentiel de classification de LocalLife afin de résoudre le problème de surcharge constaté lors des premiers retours utilisateurs.

Le système doit permettre à LocalLife de :

* classer les contenus provenant de différentes sources ;
* limiter le nombre de catégories principales visibles par l'utilisateur ;
* conserver des types et sous-types plus précis sans les exposer systématiquement ;
* empêcher qu'un nouveau type de contenu augmente automatiquement le volume affiché ;
* distinguer clairement classification, publication et visibilité ;
* préparer la personnalisation du contenu selon les préférences utilisateur ;
* permettre l'évolution future du référentiel sans modification structurelle majeure du logiciel.

L'objectif n'est **pas** de réduire artificiellement le nombre d'événements présents dans la base.

L'objectif est de contrôler **ce qui est proposé à l'utilisateur par défaut**.

---

# 2. Problème utilisateur à résoudre

Les premiers retours montrent que le volume et l'hétérogénéité des événements peuvent rendre LocalLife difficile à comprendre.

Un utilisateur peut actuellement être confronté à un mélange important de contenus :

* événements culturels ;
* concerts ;
* activités sportives ;
* foodtrucks ;
* commerces ;
* marchés ;
* animations ;
* activités très spécialisées ;
* etc.

La conséquence recherchée par ce sprint est :

> **LocalLife doit pouvoir contenir beaucoup de contenus sans obliger tous les utilisateurs à les voir tous.**

Un contenu pertinent pour un utilisateur peut être inutile pour un autre.

---

# 3. Documents de référence obligatoires

Avant toute modification liée aux catégories, l'IA doit lire :

* `docs/CATEGORIES_FUNCTIONAL_MODEL.md`
* `AI_RULES.md`
* `docs/PROJECT_STATUS.md`
* le présent sprint ;
* la documentation de `CAT-000` ;
* les données et résultats produits par `CAT-000`.

`docs/CATEGORIES_FUNCTIONAL_MODEL.md` constitue la **référence fonctionnelle principale** pour ce sprint.

En cas de contradiction entre une ancienne hypothèse du projet et ce document, l'IA doit :

1. identifier la contradiction ;
2. ne pas décider arbitrairement ;
3. appliquer le modèle fonctionnel ;
4. documenter la divergence si une modification de documentation est nécessaire.

---

# 4. Principes fondamentaux

## 4.1 Catégorie, type et visibilité sont différents

Le système doit distinguer au minimum :

* catégorie principale ;
* type / sous-type ;
* politique de visibilité.

Exemple :

```text
Catégorie : Gastronomie
Type      : Foodtruck
Visibilité: OPT_IN
```

Le type `Foodtruck` n'est donc pas nécessairement une catégorie principale.

---

## 4.2 Le nombre de catégories principales doit rester limité

LocalLife doit disposer d'un nombre volontairement limité de catégories principales.

La cible est de l'ordre de **8 à 12 catégories**, sans imposer artificiellement un nombre exact.

Les catégories doivent être :

* compréhensibles par le grand public ;
* suffisamment larges ;
* mutuellement cohérentes ;
* utiles pour filtrer le contenu ;
* suffisamment stables pour ne pas changer constamment.

Le référentiel doit être déterminé à partir des données réelles analysées par `CAT-000`.

---

## 4.3 Les types peuvent être nombreux

Les types permettent de conserver une classification plus précise sans multiplier les catégories principales.

Exemple :

```text
Gastronomie
├── Foodtruck
├── Restaurant
├── Bar
├── Dégustation
└── Atelier cuisine
```

Autre exemple :

```text
Culture
├── Musée
├── Exposition
├── Conférence
├── Patrimoine
└── Visite guidée
```

---

# 5. Politique de visibilité

Chaque catégorie ou type exploitable par le système doit avoir une politique de visibilité explicite.

## `DEFAULT`

Le contenu peut apparaître dans le flux général par défaut.

## `OPT_IN`

Le contenu est correctement classifié et peut être publié, mais il n'apparaît pas dans le flux général tant que l'utilisateur ne l'a pas sélectionné ou demandé.

## `PENDING`

La classification ou la décision de traitement n'est pas suffisamment déterminée.

Le contenu ne doit pas être considéré comme publiable normalement tant que son traitement n'est pas résolu.

---

# 6. Distinction obligatoire entre PENDING et OPT_IN

Ces états ne doivent jamais être confondus.

### `PENDING`

Signifie :

> LocalLife ne sait pas encore suffisamment bien comment traiter ce contenu.

### `OPT_IN`

Signifie :

> LocalLife sait parfaitement ce qu'est ce contenu, mais choisit de ne pas l'afficher à tous les utilisateurs par défaut.

Exemple :

```text
Foodtruck
→ Gastronomie
→ classification valide
→ OPT_IN
```

est un cas normal.

Alors que :

```text
"Animation XYZ"
→ classification impossible
→ PENDING
```

est un cas différent.

---

# 7. Un contenu OPT_IN reste un contenu valide

Un contenu `OPT_IN` :

* peut être publié ;
* peut être conservé ;
* peut être recherché ;
* peut être consulté directement ;
* peut apparaître dans une recherche ciblée ;
* peut être proposé à un utilisateur ayant activé ce type ;
* peut appartenir à un acteur local ;
* ne doit simplement pas apparaître dans le flux général par défaut.

---

# 8. Règle fondamentale d'évolution

L'ajout d'un nouveau type de contenu ne doit **jamais** provoquer automatiquement son affichage dans le flux général.

Exemple :

Si LocalLife ajoute demain :

```text
Restaurant
Bar
Foodtruck
Coiffeur
Marché
```

ces types doivent chacun recevoir explicitement une politique :

```text
DEFAULT
OPT_IN
PENDING
```

Aucun comportement implicite ne doit être appliqué.

---

# 9. Cas Foodtruck

Le Foodtruck est un cas d'usage important mais **ne constitue pas un modèle métier autonome dans ce sprint**.

Il doit être traité comme un type d'activité.

Exemple :

```text
Catégorie : Gastronomie
Type      : Foodtruck
Visibilité: OPT_IN
```

Aucun système spécifique Foodtruck ne doit être créé dans ce sprint.

Le même modèle devra pouvoir être utilisé ultérieurement pour :

* restaurant ;
* bar ;
* commerce ;
* marché ;
* association ;
* théâtre ;
* musée ;
* activité sportive ;
* etc.

---

# 10. Classification des sources externes

Les catégories fournies par une source externe ne doivent pas être reprises automatiquement comme catégories LocalLife.

Le système doit permettre un mapping :

```text
Source externe
      ↓
Catégorie source
      ↓
Mapping LocalLife
      ↓
Catégorie principale
      ↓
Type / sous-type
      ↓
Politique de visibilité
```

Exemple :

```text
OpenAgenda : "Food truck"
        ↓
LocalLife : Gastronomie
        ↓
Type : Foodtruck
        ↓
OPT_IN
```

---

# 11. Catégorie inconnue

Lorsqu'un événement entrant ne peut pas être correctement classé dans le référentiel LocalLife :

```text
Événement entrant
       ↓
classification impossible
       ↓
PENDING
```

Il ne doit pas être forcé arbitrairement dans une catégorie existante simplement pour éviter `PENDING`.

L'objectif est de préserver la qualité du référentiel.

---

# 12. Conservation de l'information source

La classification LocalLife ne doit pas détruire inutilement l'information provenant de la source.

Le système doit conserver, lorsque cela est pertinent :

* catégorie source ;
* type source ;
* source de l'information ;
* mapping LocalLife appliqué.

Cela permettra :

* d'améliorer ultérieurement les mappings ;
* de corriger les classifications ;
* de comprendre l'origine d'un contenu ;
* de traiter de nouvelles catégories sources.

---

# 13. Préférences utilisateur

Le référentiel doit être conçu pour permettre ultérieurement à un utilisateur connecté de sélectionner ses centres d'intérêt.

Exemple :

```text
Musique       ✓
Culture       ✓
Famille       ✓
Sport         ✗
Foodtruck     ✓
Restaurant    ✗
```

Les préférences pourront notamment permettre d'activer certains contenus `OPT_IN`.

Ce sprint ne doit pas nécessairement implémenter l'interface complète des préférences utilisateur si celle-ci appartient à un autre sprint.

En revanche, le modèle de catégories ne doit pas empêcher cette fonctionnalité.

---

# 14. Utilisateur non connecté

Le comportement par défaut doit rester simple.

Un utilisateur non connecté doit recevoir uniquement les contenus correspondant à la politique de visibilité générale prévue par LocalLife.

Les contenus `OPT_IN` ne doivent pas devenir visibles uniquement parce que l'utilisateur n'est pas connecté.

---

# 15. Utilisateur connecté

Un utilisateur connecté pourra ultérieurement disposer de préférences personnalisées.

Le système devra permettre :

```text
Flux général
     +
préférences utilisateur
     ↓
contenus pertinents
```

Le mécanisme précis de personnalisation appartient au sprint qui implémentera les préférences utilisateur.

---

# 16. Acteurs locaux

Ce sprint doit tenir compte du futur modèle des acteurs locaux.

LocalLife pourra ultérieurement accueillir :

* entreprises ;
* commerçants ;
* foodtrucks ;
* restaurants ;
* bars ;
* associations ;
* artistes ;
* organisateurs ;
* collectivités ;
* établissements culturels ;
* structures sportives ;
* particuliers organisateurs.

Le système de catégories ne doit pas dépendre d'un type d'acteur particulier.

Un acteur local pourra produire des contenus `DEFAULT` ou `OPT_IN`.

---

# 17. Import et création manuelle

Un contenu importé et un contenu créé directement dans LocalLife doivent pouvoir utiliser le même référentiel de classification.

Exemple :

```text
IMPORT
  ↓
classification
  ↓
LocalLife
```

et :

```text
CRÉATION DIRECTE
  ↓
classification
  ↓
LocalLife
```

Le système de catégories ne doit pas créer deux référentiels parallèles.

---

# 18. Objectifs techniques

CAT doit produire un référentiel permettant :

* d'identifier une catégorie LocalLife ;
* d'identifier un type/sous-type ;
* d'associer une politique de visibilité ;
* de mapper une catégorie externe ;
* de gérer les catégories inconnues ;
* de faire évoluer le référentiel sans modification structurelle majeure du code.

Le stockage peut être technique ou configuré selon l'architecture existante.

Aucune technologie particulière ne doit être imposée par le présent sprint si l'architecture existante permet déjà de répondre au besoin.

---

# 19. Tickets

## CAT-000 — Audit des catégories existantes

**Priorité : Haute**

### Objectif

Analyser les catégories et types réellement présents dans les données actuelles.

### À analyser

* catégories ;
* types ;
* fréquence ;
* variantes orthographiques ;
* synonymes ;
* catégories trop fines ;
* catégories trop larges ;
* catégories inutilisables ;
* catégories sans correspondance claire ;
* catégories spécifiques à une source ;
* volume d'événements par catégorie ;
* exemples représentatifs.

### Critères d'acceptation

* les catégories réelles sont inventoriées ;
* les volumes sont connus ;
* les principales anomalies sont documentées ;
* les regroupements potentiels sont identifiés ;
* aucune suppression arbitraire n'est effectuée ;
* les données servent de base à CAT-001.

### Livrable

Un document d'audit exploitable par CAT-001.

---

# CAT-001 — Définir le référentiel de catégories LocalLife

**Priorité : Critique**

**Dépendance : CAT-000**

### Objectif

Définir les catégories principales et les types LocalLife à partir des données réelles et du modèle fonctionnel.

### Le ticket doit produire

1. la liste des catégories principales ;
2. la justification de chaque catégorie ;
3. les types/sous-types associés ;
4. les mappings des catégories sources ;
5. la politique `DEFAULT / OPT_IN / PENDING` ;
6. les règles de traitement des catégories inconnues ;
7. les cas particuliers ;
8. les règles d'évolution du référentiel.

### Critères d'acceptation

* les catégories principales sont limitées ;
* elles sont compréhensibles par l'utilisateur ;
* les types sont séparés des catégories ;
* chaque type dispose d'une politique de visibilité ;
* les cas Foodtruck, Restaurant et Bar peuvent être représentés sans créer de catégorie principale obligatoire ;
* un nouveau type ne devient pas automatiquement visible ;
* les catégories sources peuvent être mappées ;
* une catégorie inconnue peut rester `PENDING` ;
* le référentiel est documenté ;
* les décisions sont justifiées à partir des données de CAT-000.

### Interdiction

CAT-001 ne doit pas créer le système complet des acteurs locaux ou des préférences utilisateur.

---

# CAT-002 — Définir le modèle de données du référentiel

**Priorité : Haute**

**Dépendance : CAT-001**

### Objectif

Traduire le référentiel fonctionnel validé en modèle de données compatible avec l'architecture existante.

### À définir

* catégorie ;
* type ;
* sous-type si nécessaire ;
* politique de visibilité ;
* relation entre catégories et types ;
* mapping des catégories sources ;
* gestion des valeurs actives/inactives.

### Critères d'acceptation

* modèle documenté ;
* aucune duplication inutile ;
* ajout d'un nouveau type possible sans modification structurelle majeure ;
* compatibilité avec les données existantes ;
* stratégie de migration définie si nécessaire.

---

# CAT-003 — Implémenter le référentiel LocalLife

**Priorité : Haute**

**Dépendance : CAT-002**

### Objectif

Implémenter le référentiel défini par CAT-001 et CAT-002.

### Critères d'acceptation

* catégories persistées ;
* types persistés ;
* politique de visibilité persistée ;
* relations fonctionnelles ;
* données initiales intégrées ;
* tests unitaires ;
* aucun comportement implicite pour les nouvelles catégories.

---

# CAT-004 — Implémenter le mapping des catégories sources

**Priorité : Haute**

**Dépendance : CAT-003**

### Objectif

Permettre aux données importées d'être converties vers le référentiel LocalLife.

### Critères d'acceptation

* mapping explicite ;
* catégorie source conservée lorsque nécessaire ;
* catégorie LocalLife attribuée correctement ;
* type LocalLife attribué lorsque possible ;
* absence de mapping → `PENDING` ;
* aucune classification arbitraire ;
* tests couvrant les principales catégories sources.

---

# CAT-005 — Appliquer les politiques DEFAULT / OPT_IN / PENDING

**Priorité : Haute**

**Dépendance : CAT-004**

### Objectif

Faire respecter les règles de visibilité définies par le référentiel.

### Critères d'acceptation

* `DEFAULT` peut alimenter le flux général ;
* `OPT_IN` n'est pas affiché par défaut ;
* `OPT_IN` reste publiable et consultable ;
* `PENDING` n'est pas considéré comme publiable normalement ;
* aucun nouveau type n'est automatiquement `DEFAULT` ;
* les règles sont couvertes par des tests.

---

# CAT-006 — Adapter le pipeline d'import

**Priorité : Haute**

**Dépendance : CAT-005**

### Objectif

Intégrer le nouveau référentiel dans le processus d'import existant.

### Flux attendu

```text
Source externe
      ↓
Normalisation
      ↓
Classification LocalLife
      ↓
DEFAULT / OPT_IN / PENDING
      ↓
Publication / traitement approprié
```

### Critères d'acceptation

* les événements importés utilisent le référentiel LocalLife ;
* les événements inconnus peuvent rester `PENDING` ;
* aucun événement n'est affiché uniquement parce qu'il a été importé ;
* aucun doublon de système de catégories n'est créé ;
* tests d'intégration présents.

---

# CAT-007 — Préparer l'exploitation par les préférences utilisateur

**Priorité : Moyenne**

**Dépendance : CAT-005**

### Objectif

Garantir que le référentiel peut être utilisé ultérieurement par le système de préférences utilisateur.

### À définir

* quelles catégories peuvent être sélectionnées ;
* quels types peuvent être sélectionnés ;
* comment un utilisateur active un contenu `OPT_IN` ;
* comment les préférences pourront être appliquées au flux.

### Critères d'acceptation

* modèle documenté ;
* aucune implémentation prématurée de l'interface utilisateur ;
* aucune dépendance forte empêchant le futur sprint de préférences.

---

# CAT-008 — Tests fonctionnels et non-régression

**Priorité : Haute**

**Dépendance : CAT-006**

### Scénarios minimum

#### Catégorie DEFAULT

```text
Événement → catégorie connue → DEFAULT
→ visible dans le flux général
```

#### Type OPT_IN

```text
Foodtruck → Gastronomie → OPT_IN
→ non visible par défaut
→ reste publiable
→ devient visible dans une recherche/préférence appropriée
```

#### Catégorie inconnue

```text
Événement → aucune classification fiable
→ PENDING
→ non affiché normalement
```

#### Nouveau type

```text
Ajout du type "Restaurant"
→ aucune visibilité implicite
→ politique explicite obligatoire
```

#### Source externe

```text
Catégorie source
→ mapping LocalLife
→ catégorie + type + visibilité corrects
```

### Critères d'acceptation

Tous les scénarios passent.

Aucune régression sur :

* carte ;
* liste ;
* recherche ;
* import ;
* publication ;
* modération.

---

# CAT-009 — Documentation finale

**Priorité : Haute**

**Dépendance : CAT-008**

### Documentation à mettre à jour

Selon les besoins réels du projet :

* `CATEGORIES_FUNCTIONAL_MODEL.md`
* `PROJECT_STATUS.md`
* `ROADMAP.md`
* documentation architecture ;
* documentation API ;
* documentation import ;
* changelog.

### Critères d'acceptation

La documentation permet à une nouvelle IA ou à un développeur de comprendre :

* pourquoi les catégories sont limitées ;
* la différence entre catégorie et type ;
* la différence entre `DEFAULT`, `OPT_IN` et `PENDING` ;
* comment ajouter un nouveau type ;
* comment mapper une source externe ;
* pourquoi un Foodtruck n'est pas un modèle spécifique ;
* comment le système préparera les préférences utilisateur.

---

# 20. Règles d'évolution du référentiel

Toute demande future du type :

> « Ajoutons une nouvelle catégorie »

doit obligatoirement répondre à ces questions :

1. Cette notion est-elle réellement une catégorie principale ?
2. Ne s'agit-il pas plutôt d'un type/sous-type ?
3. Existe-t-il déjà une catégorie capable de l'accueillir ?
4. Combien de contenus sont concernés ?
5. Quel est le besoin utilisateur ?
6. Cette catégorie doit-elle être `DEFAULT` ou `OPT_IN` ?
7. Quel est l'impact sur les préférences utilisateur ?
8. Quel est l'impact sur les sources externes ?
9. Quel est le comportement si la catégorie n'est pas reconnue ?
10. Cette évolution augmente-t-elle inutilement la complexité de l'interface ?

Aucune nouvelle catégorie principale ne doit être ajoutée uniquement parce qu'une source externe possède cette catégorie.

---

# 21. Cas limites obligatoires

Le système doit être capable de gérer au minimum :

### Foodtruck

```text
Gastronomie / Foodtruck / OPT_IN
```

### Restaurant

```text
Gastronomie / Restaurant / OPT_IN ou DEFAULT
```

La décision finale appartient au référentiel validé.

### Bar

```text
Gastronomie ou catégorie appropriée / Bar / politique explicite
```

### Festival

Peut être classé selon sa nature sans créer automatiquement une catégorie Festival principale.

### Association

L'association est un type d'acteur, pas une catégorie d'événement.

### Collectivité

La collectivité est un type d'acteur, pas une catégorie d'événement.

### Événement sans catégorie fiable

```text
PENDING
```

### Événement avec catégorie valide mais type OPT_IN

```text
PUBLISHED + OPT_IN
```

Ces deux notions ne doivent jamais être confondues.

---

# 22. Ce qui est explicitement hors périmètre

Ce sprint ne doit pas implémenter :

* système complet des comptes professionnels ;
* système complet des acteurs locaux ;
* gestion des organisations ;
* gestion multi-utilisateurs ;
* revendication d'une fiche ;
* abonnement professionnel ;
* paiement ;
* marketplace ;
* statistiques professionnelles ;
* publicité ;
* recommandations algorithmiques ;
* moteur de personnalisation complet ;
* interface complète des préférences utilisateur ;
* système spécifique Foodtruck ;
* système spécifique Restaurant ;
* système spécifique Bar.

Ces sujets pourront être traités dans des sprints dédiés.

---

# 23. Définition of Done

Le Sprint CATEG est terminé lorsque :

* [ ] `CAT-000` a analysé les données réelles ;
* [ ] les catégories principales LocalLife sont définies ;
* [ ] les types/sous-types sont définis ;
* [ ] chaque type possède une politique de visibilité ;
* [ ] `DEFAULT`, `OPT_IN` et `PENDING` sont clairement séparés ;
* [ ] le mapping des sources externes est défini ;
* [ ] le référentiel est implémenté ;
* [ ] les imports utilisent ce référentiel ;
* [ ] un contenu inconnu peut rester `PENDING` ;
* [ ] un contenu `OPT_IN` peut être publié sans être visible par défaut ;
* [ ] aucun nouveau type n'est automatiquement affiché ;
* [ ] Foodtruck, Restaurant et Bar peuvent être représentés sans architecture spécifique ;
* [ ] le futur système de préférences utilisateur est pris en compte ;
* [ ] les tests de non-régression passent ;
* [ ] la documentation est à jour ;
* [ ] aucune décision d'architecture concernant les acteurs locaux n'a été prise prématurément.

---

# 24. Principe directeur du sprint

> **LocalLife ne cherche pas à afficher tout ce qu'il sait identifier.**
>
> **LocalLife cherche à classifier suffisamment précisément les contenus pour décider intelligemment de ce qui doit être affiché par défaut et de ce qui doit être proposé uniquement aux utilisateurs intéressés.**
