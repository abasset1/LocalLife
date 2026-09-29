# CATEGORIES_REFERENCE — Référentiel des catégories LocalLife (CAT-001)

> Sprint CATEG (réécrit), ticket CAT-001. Document de définition métier uniquement : **aucun code
> applicatif, aucune migration, aucun modèle (`Activity`, base, imports, frontend, `FoodTruck`) n'est
> modifié.** Basé sur `docs/CATEGORIES_AUDIT.md` (CAT-000, données réelles : 47 338 activités, 88,1 %
> sans catégorie, 959 valeurs distinctes fragmentées — voir `docs/CATEGORIES_TOP200.md`).

## 0. Décision d'Alex — périmètre simplifié de ce document

Alex a tranché un périmètre volontairement simple pour CAT-001, qui prévaut sur toute lecture plus
étendue du sprint :

1. **Une seule liste de 10 à 12 catégories principales**, plates — pas de hiérarchie type/sous-type.
2. **Classification automatique à l'entrée** : chaque activité entrante (import ou saisie manuelle)
   doit se voir déduire une catégorie parmi cette liste.
3. **`PENDING` si aucune catégorie ne correspond** — pour **toutes** les activités entrantes (import
   comme saisie manuelle), pas seulement l'import.
4. **Saisie manuelle : liste déroulante fermée** sur ces mêmes catégories (plus de champ texte libre).

**Ce que ce périmètre laisse volontairement de côté** (repoussé à un ticket ultérieur si besoin, non
traité ici) : la couche *type/sous-type* et la politique `DEFAULT`/`OPT_IN` détaillées dans
`CATEGORIES_FUNCTIONAL_MODEL.md` §2 et §16. Le présent document ne les intègre pas ; il ne conserve
du modèle fonctionnel que la distinction **classification réussie vs `PENDING`**, qui correspond
exactement à la demande d'Alex. Si le besoin de distinguer `DEFAULT`/`OPT_IN` (ex. remonter certaines
catégories dans un flux « sur demande ») se confirme plus tard, il s'ajoutera par-dessus cette liste
sans la remettre en cause.

## 1. La liste des 10 à 12 catégories

Construite à partir des volumes réels de CAT-000 (`CATEGORIES_TOP200.md`) : chaque catégorie
correspond soit à un pôle de volume identifié (musique, culture/patrimoine, emploi/formation...),
soit à une famille attendue mais peu représentée dans l'échantillon actuel (nature, sport) qu'il est
préférable de garder ouverte dès le départ plutôt que de la faire naître plus tard dans `OTHER`.

| # | Code | Libellé (liste déroulante) | Volume indicatif CAT-000 |
|---|---|---|---|
| 1 | `MUSIQUE` | Musique | ~67 (concert) + part de « festival », « jazz »... |
| 2 | `SPECTACLE_VIVANT` | Spectacle vivant | ~70 (spectacle) + théâtre (258) + danse, cirque, opéra, marionnettes, conte, magie |
| 3 | `CULTURE_PATRIMOINE` | Culture & patrimoine | exposition (55) + patrimoine (28) + visite (49) + architecture (46) + archéologie, conférence, lecture |
| 4 | `CINEMA` | Cinéma | faible en volume direct (7) mais catégorie attendue/stable |
| 5 | `FESTIVAL_FETE` | Festival & fête | festival (9) + événements « fête de... » (peu représentés en l'état, cf. §3) |
| 6 | `MARCHE_BROCANTE` | Marché & brocante | faible en direct (« marché » : 9) — largement dans le bruit `null`/longue traîne, cf. §3 |
| 7 | `GASTRONOMIE` | Gastronomie | pas de valeur directe significative dans le top 200 ; catégorie de rattachement pour foodtruck (§5) |
| 8 | `SPORT` | Sport | ~38 (acronymes de clubs, ex. MSB, MHB, inclus) |
| 9 | `FAMILLE_ENFANTS` | Famille & enfants | ~193 (jeune public, très jeune public, famille, jeux) |
| 10 | `NATURE_PLEINAIR` | Nature & plein air | peu représenté dans le top 200 ; catégorie à garder ouverte |
| 11 | `EMPLOI_FORMATION` | Emploi & formation | ~1 240 — 2ᵉ pôle de volume après culture/spectacle, découvert par CAT-000 |
| 12 | `VIE_LOCALE_ASSOCIATIVE` | Vie locale & associations | catégorie de repli civique/institutionnel (forums, réunions publiques...), peu représentée en direct dans le top 200 mais nécessaire pour ne pas pousser ce type de contenu vers `PENDING` par défaut |

12 catégories, dans la cible « 8 à 12 » du sprint. Les libellés sont ceux proposés pour la liste
déroulante de saisie manuelle ; ils restent modifiables sans impact technique (le code, lui, ne
change jamais — règle §2).

## 2. Règles générales (reprises du référentiel précédent, toujours valables)

1. **Code stable** : `[A-Z][A-Z0-9_]*`, immuable une fois publié. Une catégorie abandonnée est
   désactivée, jamais supprimée ni recyclée pour un autre sens.
2. **Code ≠ libellé** : toute logique (classification, filtres, API) s'appuie sur le code ; le
   libellé peut changer sans effet.
3. **Une activité = une seule catégorie.** Les mots-clés d'origine restent des tags (`activity_tag`),
   jamais une deuxième catégorie.
4. **`PENDING` n'est pas une catégorie** : c'est l'état d'une activité pour laquelle aucune des 12
   catégories ne peut être déduite avec une confiance suffisante. Il n'y a pas de catégorie
   `OTHER`/« autre » dans cette liste — conformément à la demande d'Alex, tout ce qui ne rentre dans
   aucune des 12 catégories est `PENDING`, pas casé de force.

## 3. Classification à l'entrée — principe (pour toutes les activités entrantes)

> **Import comme saisie manuelle** : Alex a explicitement demandé que la règle « pas de
> correspondance → `PENDING` » s'applique aux deux, pas seulement à l'import. Pour la saisie
> manuelle, la liste déroulante (§4) rend ce cas rare mais pas impossible : voir §4.2.

### 3.1 Source du signal de classification

D'après CAT-000, `activity.category` (premier mot-clé OpenAgenda) est un signal insuffisant à lui
seul : 88,1 % des activités n'en ont pas, et le champ mélange catégories réelles et bruit (noms de
lieux, d'artistes, acronymes). La classification doit donc s'appuyer, par ordre de priorité :

1. l'ensemble des mots-clés conservés dans `activity_tag` (pas seulement le premier) ;
2. à défaut de mot-clé exploitable, le titre et la description de l'activité.

Ce point de méthode est acté ici comme principe ; le mécanisme technique précis (règles, table de
correspondance, seuil de confiance) est du ressort de CAT-004 (mapping des catégories sources), pas
de ce document.

### 3.2 Dictionnaire indicatif par catégorie

Base de départ pour CAT-004, construite à partir des valeurs réelles les plus fréquentes de
`CATEGORIES_TOP200.md` :

| Catégorie | Mots-clés indicatifs observés dans les données réelles |
|---|---|
| `MUSIQUE` | concert, musique, jazz, chant, chorale |
| `SPECTACLE_VIVANT` | théâtre, spectacle, danse, cirque, opéra, marionnettes, conte, clown, magie, déambulation |
| `CULTURE_PATRIMOINE` | exposition, patrimoine, visite (guidée/commentée), architecture, archéologie, conférence, lecture, musée, journées du patrimoine |
| `CINEMA` | cinéma, projection, film |
| `FESTIVAL_FETE` | festival, fête |
| `MARCHE_BROCANTE` | marché, brocante, vide-grenier |
| `GASTRONOMIE` | dégustation, food truck *(événement, à distinguer de `FoodTruck.category`, §5)* |
| `SPORT` | sport, et noms/acronymes de clubs identifiés localement (ex. MSB, MHB) |
| `FAMILLE_ENFANTS` | jeune public, très jeune public, famille, jeux, jeux de société |
| `NATURE_PLEINAIR` | balade, nature, jardins, plein air *(sauf si le plein air n'est qu'un lieu — ex. cinéma en plein air reste `CINEMA`)* |
| `EMPLOI_FORMATION` | recrutement, emploi, formation, insertion, apprentissage, alternance, métiers (de..., du...), interim |
| `VIE_LOCALE_ASSOCIATIVE` | association, réunion, forum, institutionnel *(peu de correspondance directe dans l'échantillon — à surveiller une fois la classification en place : si le volume réel est nul, la pertinence de cette catégorie sera à réévaluer)* |

**Avertissement issu de CAT-000** : une grande partie de la longue traîne (noms de lieux comme
« Cameo Commanderie », d'artistes comme « Isabelle Lafon », codes internes comme « NDMBX26 »)
**ne doit être classée dans aucune de ces catégories** — ce sont des identifiants, pas des indices de
nature d'activité ; ils resteront en tags. Une activité dont le seul mot-clé est de cette nature doit
tomber en `PENDING` si le titre/la description ne permettent pas non plus de la classer.

### 3.3 Conséquence attendue sur le volume `PENDING`

Sans avoir exécuté de classification réelle, on peut déjà anticiper, à partir des volumes de
CAT-000 :

* les 88,1 % actuellement sans `activity.category` ne basculeront en dehors de `PENDING` que si le
  titre/la description permettent une classification (le premier mot-clé n'existe pas pour ces
  activités — donc `activity_tag` non plus, sauf si d'autres mots-clés existaient au-delà du premier
  et ont simplement été ignorés par `NormalizationService` actuel, point à vérifier en CAT-004) ;
* une partie significative des 11,9 % restantes tombera aussi en `PENDING` (noms de lieux/artistes
  sans indice de nature) ;
* le volume `PENDING` attendu au démarrage est donc **élevé**, potentiellement majoritaire. Ce n'est
  pas un échec de la classification : c'est la conséquence directe de la nature des données sources
  telles qu'observées en CAT-000. Il ne doit pas être traité comme une anomalie ni forcé dans une
  catégorie pour le faire baisser artificiellement.

## 4. Saisie manuelle : liste déroulante

### 4.1 Principe

Le champ texte libre actuel (`ContributionModal.tsx`, `POST /api/v1/activities {category}`) est
remplacé, à l'implémentation (CAT-003), par une liste déroulante fermée sur les 12 codes du §1. Le
contributeur choisit une catégorie parmi les 12 ; il n'y a plus de saisie libre de catégorie pour les
contributions manuelles.

### 4.2 Cas où aucune catégorie ne convient au contributeur

Deux options sont envisageables pour CAT-002/CAT-003, à trancher à l'implémentation :

* **a)** la liste déroulante inclut une option explicite « Je ne sais pas / aucune ne convient » qui
  positionne directement l'activité en `PENDING` (cohérent avec la demande d'Alex : le cas
  s'applique aussi à la saisie manuelle) ;
* **b)** le champ catégorie devient obligatoire parmi les 12 (aucune échappatoire), ce qui reporte le
  choix sur le contributeur plutôt que sur le système.

Ce point n'est pas tranché ici — **à valider par Alex avant CAT-003**, mention explicite requise
sur cette PR.

## 5. Cas `FoodTruck.category` (analyse CAT-000 confirmée)

Rappel de l'audit (`CATEGORIES_AUDIT.md` §7) : `FoodTruck.category` est un **type de cuisine**
(burger, tacos...), pas une catégorie d'événement — il reste hors de cette liste de 12 catégories, et
aucun référentiel séparé n'est créé. Un **événement** de type rassemblement de food trucks (ex. seed
« Food Truck Festival ») est une activité classée `GASTRONOMIE` (ou `FESTIVAL_FETE` selon les cas,
comme documenté en §3.2), sans lien avec le champ `FoodTruck.category` du commerçant.

## 6. Ce que ce document ne fait pas (rappel du périmètre CAT-001)

Conformément au sprint : pas de modification d'`Activity`, de la base, des imports, du frontend, ni
de `FoodTruck`. Le modèle de données (table, relations, migration) revient à CAT-002 ; la mise en
œuvre de la classification (règles, seuils, table de correspondance) revient à CAT-004 ; la liste
déroulante et le blocage de la saisie libre reviennent à CAT-003.

## 7. Points à valider par Alex avant CAT-002

1. La liste des 12 catégories et leurs libellés (§1) — en particulier `NATURE_PLEINAIR` et
   `VIE_LOCALE_ASSOCIATIVE`, peu représentées dans l'échantillon actuel : les garder dès le départ ou
   attendre un volume réel avant de les créer ?
2. Le traitement du cas « aucune catégorie ne convient » en saisie manuelle (§4.2, options a/b).
3. Le volume `PENDING` élevé attendu au démarrage (§3.3) est-il acceptable tel quel, ou faut-il un
   second passage de classification (titre/description) avant la mise en service de CAT-004 ?
