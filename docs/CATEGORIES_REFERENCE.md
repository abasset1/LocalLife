# CATEGORIES_REFERENCE — Référentiel métier officiel des catégories (CAT-001)

> Sprint CATEG, ticket CAT-001. Document de définition métier uniquement : **aucun code applicatif,
> aucune migration, aucun modèle (`Activity`, `FoodTruck`, base, imports, frontend) n'est modifié.**
> Basé sur `CATEGORIES_AUDIT.md` (CAT-000).

## Statut : PROVISOIRE — à figer après extraction des données réelles

Le référentiel ne doit pas être déduit des seules valeurs du dépôt. Or **aucune base (dev/beta/prod)
n'est accessible depuis l'environnement où ce document a été rédigé** : les requêtes de
`CATEGORIES_AUDIT.md` §6.2 n'ont donc **pas pu être exécutées**.

En conséquence :

* les 17 catégories de la proposition initiale sont **conservées telles quelles à titre provisoire** ;
  aucune fusion/suppression/ajout n'est décidé sans données réelles (la seule évidence disponible
  est celle du dépôt : valeurs de tests/seed, cf. audit §6.1, non représentatives) ;
* la section 6 (« Confrontation aux données réelles ») est **à compléter** ;
* le statut passera à « FIGÉ » une fois la section 6 remplie et les décisions ci-dessous validées.

## 1. Règles générales

1. **Code stable** : `[A-Z][A-Z0-9_]*`, 32 caractères max, immuable une fois publié. Un code n'est
   jamais renommé ni réutilisé pour un autre sens ; une catégorie abandonnée est désactivée
   (`active = false`, ticket CAT-003), jamais supprimée ni recyclée.
2. **Code ≠ libellé** : le libellé (affiché, français) peut changer sans effet ; toute logique
   (filtres, API, classification) s'appuie sur le code uniquement.
3. **Une activité = une seule catégorie principale.** Les autres nuances restent dans les tags.
4. **Catégorie la plus spécifique** qui décrit l'activité ; en cas d'égalité, voir les cas ambigus de
   chaque catégorie (la priorité détaillée est l'objet de CAT-002).
5. **Les valeurs source ne sont jamais des catégories** : mots-clés OpenAgenda, saisies libres
   historiques et valeurs d'origine restent des tags (cf. CAT-002/006).
6. **`OTHER` est une catégorie valide, pas une absence de valeur** (voir §4).

## 2. Périmètre : `FoodTruck.category` (analyse demandée)

### 2.1 Ce qui a été analysé

| Axe | Constat (code de `main`) |
|---|---|
| Modèle | `FoodTruck(id, name, description, latitude, longitude, category, contact, status)`, table `food_truck`, `category VARCHAR(255) NOT NULL` (V12). Pas de dates, pas de source, pas de lien avec `activity` ni avec la table `category`. |
| Définition métier | `FOOD_TRUCK_CONTRACT.md` : « **Type de cuisine** (ex. `burger`, `tacos`, `street food asiatique`) », chaîne libre. Module séparé d'`Activity` par décision d'architecture (LL-6008) : un food truck **n'est pas un événement daté**. |
| Producteurs | Uniquement `POST /api/v1/foodtrucks` (utilisateur authentifié, `FoodTruckService#createFoodTruck`, non vide). Aucun import, aucun collecteur, aucun seed (V12 n'insère rien). |
| Consommateurs | Affichage seul : popup de marqueur (`ExplorerMap.tsx`), `PreviewCard.tsx` (mobile, préfixe « Food truck · »). **Aucun filtre, aucune recherche, aucun regroupement** ne s'appuie sur `FoodTruck.category` (le paramètre `category` de `/nearby` et `/within-bounds` ne porte que sur `activity`). |
| Valeurs | Exemples de contrat : `burger`, `tacos`, `street food asiatique`. **Valeurs réelles : à extraire** (`SELECT category, COUNT(*) FROM food_truck GROUP BY 1`, audit §6.2). |
| Usage fonctionnel | Décrire ce que vend le camion (étiquette informative sur la carte). |

### 2.2 Conclusion

`FoodTruck.category` est un **attribut descriptif du commerçant (type de cuisine)**, et non la
catégorie d'un événement LocalLife :

* les catégories du référentiel classent des **activités/événements** (« qu'est-ce que je peux faire ? »),
  alors que la valeur d'un food truck répond à « que vend-il ? » ;
* les deux ensembles n'ont pas la même granularité : `burger`/`tacos` n'ont aucune correspondance
  utile dans MUSIC, SPORT, HERITAGE… ; les fusionner créerait une catégorie fourre-tout (FOOD) sans
  valeur de filtrage ;
* aucun consommateur ne filtre dessus : un référentiel dédié n'a aujourd'hui **aucune justification
  métier** (pas de filtre, pas de recherche, pas de normalisation requise pour un affichage).

**Décision proposée (parmi les trois options demandées) : 3ᵉ option — donnée qui ne doit pas être
traitée comme une catégorie LocalLife.**

* `FoodTruck.category` **ne rejoint pas** le référentiel des catégories d'activités ;
* **aucun référentiel séparé** n'est créé (pas de justification métier aujourd'hui) ;
* la valeur reste un texte descriptif de type de cuisine, gérée dans le module `foodtruck`.

Conséquences pour le sprint :

* le Sprint CATEG ne modifie pas `FoodTruck` (conforme à la consigne CAT-001) ;
* le référentiel ne contient **pas** de code `FOOD_TRUCK`. Un **événement** de type rassemblement de
  food trucks (ex. le seed « Food Truck Festival ») est une *activité* : il se classe FOOD ou FESTIVAL
  selon les règles ci-dessous ;
* le frontend/mobile ne doivent pas mélanger les valeurs de `FoodTruck.category` aux filtres de
  catégories d'activités (à respecter en CAT-007/CAT-008) ;
* **Point ouvert** (à valider, hors sprint) : le nom de champ `category` est trompeur (il désigne un
  type de cuisine). Un éventuel renommage (`cuisine_type`) relèverait d'un ticket dédié ; et si
  les valeurs réelles (à extraire) montrent une forte hétérogénéité **et** qu'un besoin de filtre
  apparaît, un référentiel « type de cuisine » pourra être justifié à ce moment-là.

## 3. Le référentiel

Ordre = `display_order` proposé. Libellés provisoires.

| # | Code | Libellé |
|---|---|---|
| 1 | `MUSIC` | Musique |
| 2 | `SHOW` | Spectacle |
| 3 | `CULTURE` | Culture & arts |
| 4 | `HERITAGE` | Patrimoine & visites |
| 5 | `FESTIVAL` | Fête & festival |
| 6 | `MARKET` | Marché & brocante |
| 7 | `FOOD` | Gastronomie |
| 8 | `SPORT` | Sport |
| 9 | `FAMILY` | Famille & enfants |
| 10 | `WORKSHOP` | Atelier & formation |
| 11 | `NATURE` | Nature & plein air |
| 12 | `LOCAL_LIFE` | Vie locale & associations |
| 13 | `PROFESSIONAL` | Professionnel |
| 14 | `CINEMA` | Cinéma & audiovisuel |
| 15 | `COMMERCE` | Commerce & animations commerciales |
| 16 | `TRADITION` | Traditions & spiritualité |
| 17 | `YOUTH` | Jeunesse |
| 18 | `OTHER` | Autre (toujours en dernier) |

> Note : la proposition initiale comporte 17 codes métier (MUSIC … YOUTH) + `OTHER`, soit 18 lignes.

Chaque fiche : **description**, **règles d'utilisation**, **exemples**, **contre-exemples**,
**cas ambigus**.

### MUSIC — Musique
* **Description** : événements dont l'objet principal est l'écoute ou la pratique de la musique.
* **Règles** : concert, récital, jam, DJ set, chorale, orchestre, fanfare en concert, bal musical.
* **Exemples** : concert de jazz, récital d'orgue, concert de la chorale municipale.
* **Contre-exemples** : festival pluridisciplinaire (→ FESTIVAL), comédie musicale (→ SHOW), atelier
  de guitare (→ WORKSHOP), projection d'un film musical (→ CINEMA).
* **Ambigus** : « festival de jazz » → MUSIC si la programmation est exclusivement musicale et
  qu'un concert précis est décrit ; FESTIVAL si l'événement est présenté comme une fête plus large.
  Bal populaire → FESTIVAL si l'ambiance festive/repas prime, MUSIC si l'orchestre est l'attraction.

### SHOW — Spectacle
* **Description** : spectacles vivants à programmation scénique hors concert.
* **Règles** : théâtre, danse, cirque, humour, marionnettes, magie, contes, comédie musicale, opéra.
* **Exemples** : pièce de théâtre, one-man-show, spectacle de danse, conte pour enfants en salle.
* **Contre-exemples** : concert (→ MUSIC), exposition (→ CULTURE), animation de rue commerciale
  (→ COMMERCE).
* **Ambigus** : spectacle jeune public → SHOW (le public reste dans les tags) sauf si la nature
  d'activité est surtout ludique/atelier (→ FAMILY ou WORKSHOP). Opéra → SHOW ; concert lyrique
  sans mise en scène → MUSIC.

### CULTURE — Culture & arts
* **Description** : arts visuels, littérature, savoirs et médiation culturelle hors spectacle vivant.
* **Règles** : exposition, vernissage, galerie, lecture, conférence culturelle, salon du livre,
  dédicace, débat d'idées, musée (hors visite patrimoniale).
* **Exemples** : exposition de peinture, rencontre avec un auteur, conférence d'histoire de l'art.
* **Contre-exemples** : visite de monument (→ HERITAGE), projection (→ CINEMA), stage de peinture
  (→ WORKSHOP).
* **Ambigus** : exposition au sein d'un lieu classé → CULTURE si l'attrait est l'œuvre exposée,
  HERITAGE si c'est le lieu. Conférence professionnelle → PROFESSIONAL.

### HERITAGE — Patrimoine & visites
* **Description** : découverte du patrimoine bâti, historique, naturel guidé ou muséographique.
* **Règles** : visite guidée, journées du patrimoine, monuments, châteaux, archéologie, circuits de
  découverte urbaine.
* **Exemples** : visite guidée de la vieille ville, ouverture d'un château, journées du patrimoine.
* **Contre-exemples** : randonnée sans visée patrimoniale (→ NATURE), exposition (→ CULTURE).
* **Ambigus** : visite d'un site naturel remarquable → NATURE si l'intérêt est la nature,
  HERITAGE si c'est l'histoire du lieu. Visite d'entreprise → PROFESSIONAL ou COMMERCE selon la
  finalité.

### FESTIVAL — Fête & festival
* **Description** : rassemblements festifs, fêtes locales et festivals **multi-attractions** ou dont
  la nature festive prime sur un domaine unique.
* **Règles** : fête de village, fête foraine, festival pluridisciplinaire, carnaval, feux d'artifice,
  soirée festive, fête de quartier.
* **Exemples** : fête du village, carnaval, festival pluridisciplinaire d'été, fête foraine.
* **Contre-exemples** : concert isolé (→ MUSIC), marché de Noël (→ MARKET), fête religieuse à
  caractère cultuel (→ TRADITION).
* **Ambigus** : le mot « festival » **ne suffit pas** à classer : festival de musique →
  MUSIC ou FESTIVAL selon programmation ; festival de cinéma → CINEMA ; festival de rue
  gastronomique → FOOD ; festival de food trucks → FOOD (rassemblement à vocation culinaire).
  Règle par défaut : si un domaine unique dominant est identifiable, il l'emporte ; sinon FESTIVAL.

### MARKET — Marché & brocante
* **Description** : événements dont l'objet est la vente/achat/échange de biens dans un cadre de
  marché.
* **Règles** : marché, marché de producteurs, marché de Noël, brocante, vide-grenier, puces, bourse,
  salon de vente aux particuliers, marché artisanal.
* **Exemples** : marché de Noël, vide-grenier, marché des créateurs, bourse aux jouets.
* **Contre-exemples** : dégustation seule (→ FOOD), animation commerciale d'un magasin (→ COMMERCE).
* **Ambigus** : marché de Noël avec animations → MARKET (dominante marché). Marché gourmand →
  FOOD si l'accent est la dégustation, MARKET si la vente. Salon professionnel → PROFESSIONAL.

### FOOD — Gastronomie
* **Description** : événements centrés sur la cuisine, les produits du terroir et la dégustation.
* **Règles** : dégustation, repas/banquet, cours de cuisine grand public (atelier culinaire → voir
  ambigus), fête gastronomique, œnologie, food trucks (rassemblement), fêtes de produits.
* **Exemples** : dégustation de vins, fête du fromage, rassemblement de food trucks, repas de quartier
  à visée gastronomique.
* **Contre-exemples** : marché de producteurs (→ MARKET), festival pluridisciplinaire (→ FESTIVAL).
* **Ambigus** : atelier de cuisine → WORKSHOP (l'apprentissage prime) sauf mention de dégustation
  principale. Repas d'association → LOCAL_LIFE.

### SPORT — Sport
* **Description** : pratique ou spectacle sportif, compétitions et activités physiques encadrées.
* **Règles** : match, tournoi, course, randonnée sportive, trail, cyclisme, yoga/fitness en séance,
  inscription à un club (séance).
* **Exemples** : course à pied, tournoi de football, initiation à l'escalade.
* **Contre-exemples** : balade contemplative en nature (→ NATURE), stage de formation non sportif
  (→ WORKSHOP).
* **Ambigus** : randonnée → SPORT si effort/compétition mis en avant, NATURE si observation ou
  balade. Sport pour enfants → SPORT (public en tag) ou FAMILY si l'esprit est ludique/familial.

### FAMILY — Famille & enfants
* **Description** : activités conçues pour être vécues en famille ou destinées aux enfants
  (< 12 ans environ) quand aucune autre catégorie ne décrit mieux l'activité.
* **Règles** : chasse aux œufs, animations enfants, ludothèque, spectacles/ateliers explicitement
  « en famille », parcs et jeux.
* **Exemples** : chasse au trésor en famille, après-midi jeux pour enfants, ferme pédagogique.
* **Contre-exemples** : spectacle de marionnettes (→ SHOW), atelier de poterie enfants (→ WORKSHOP),
  activités ados/jeunes (→ YOUTH).
* **Ambigus** : catégorie de repli pour le jeune public : la nature de l'activité (SHOW, SPORT,
  WORKSHOP…) l'emporte quand elle est claire ; FAMILY quand elle ne l'est pas.

### WORKSHOP — Atelier & formation
* **Description** : événements dont l'objectif est d'apprendre ou de pratiquer avec un animateur.
* **Règles** : atelier, stage, cours, initiation, formation, masterclass, café-conférence
  pédagogique, repair café.
* **Exemples** : atelier poterie, stage de dessin, initiation à l'informatique, repair café.
* **Contre-exemples** : conférence sans pratique (→ CULTURE), séance sportive (→ SPORT).
* **Ambigus** : la thématique (cuisine, musique, sport) reste en tag ; WORKSHOP l'emporte dès que
  « atelier/stage/cours/initiation » est explicite. Formation d'entreprise → PROFESSIONAL.

### NATURE — Nature & plein air
* **Description** : découverte de la nature, de la faune/flore, de l'environnement et loisirs de
  plein air non compétitifs.
* **Règles** : balade nature, sortie botanique, observation des oiseaux, nettoyage de nature,
  cueillette, jardins, sentiers, sortie astronomie.
* **Exemples** : sortie ornithologique, nettoyage de plage, balade guidée en forêt.
* **Contre-exemples** : course/trail (→ SPORT), visite de château (→ HERITAGE).
* **Ambigus** : « plein air » seul ne suffit pas (un concert en plein air est MUSIC, un cinéma en
  plein air est CINEMA) : c'est l'objet de l'activité qui compte, pas le lieu.

### LOCAL_LIFE — Vie locale & associations
* **Description** : vie de quartier/commune, associations, institutions et démocratie locale.
* **Règles** : réunion publique, assemblée générale, portes ouvertes d'association, conseil de
  quartier, forum des associations, don du sang, cérémonies civiques, entraide, repas de quartier.
* **Exemples** : forum des associations, réunion de quartier, collecte solidaire.
* **Contre-exemples** : fête de village (→ FESTIVAL), cérémonie religieuse (→ TRADITION).
* **Ambigus** : sert aussi de catégorie de repli « civique » avant OTHER quand l'organisateur est
  une association/collectivité mais que la nature exacte est inconnue.

### PROFESSIONAL — Professionnel
* **Description** : événements à finalité professionnelle : réseau, emploi, business, formation
  métier.
* **Règles** : salon professionnel, forum emploi, afterwork réseau, conférence métier,
  séminaire, rencontre d'entrepreneurs.
* **Exemples** : forum de l'emploi, rencontre d'entrepreneurs locaux, salon B2B.
* **Contre-exemples** : atelier grand public (→ WORKSHOP), marché (→ MARKET).
* **Ambigus** : conférence grand public sur un métier → CULTURE ou PROFESSIONAL selon le public
  visé (indicateur « professionnels » explicite → PROFESSIONAL).

### CINEMA — Cinéma & audiovisuel
* **Description** : projections et événements consacrés à l'image animée.
* **Règles** : projection, ciné-club, avant-première, cinéma en plein air, festival de cinéma,
  courts-métrages, rencontre avec un réalisateur.
* **Exemples** : cinéma en plein air, ciné-débat, festival du court-métrage.
* **Contre-exemples** : concert de musique de film (→ MUSIC), exposition photo (→ CULTURE).
* **Ambigus** : ciné-concert → MUSIC ou CINEMA selon l'accent (défaut : CINEMA si projection
  principale).

### COMMERCE — Commerce & animations commerciales
* **Description** : animations initiées par des commerces, centres commerciaux ou unions
  commerciales à finalité promotionnelle.
* **Règles** : soldes, braderie commerçante, nocturne, opération commerciale, ouverture de magasin,
  animations de centre-ville portées par les commerçants.
* **Exemples** : braderie des commerçants, nocturne commerçante, inauguration de boutique.
* **Contre-exemples** : marché (→ MARKET), vide-grenier (→ MARKET), salon professionnel
  (→ PROFESSIONAL).
* **Ambigus** : la frontière MARKET/COMMERCE dépend de l'organisateur (particuliers/producteurs →
  MARKET ; commerçants établis → COMMERCE). Braderie : à confirmer avec les données réelles.

### TRADITION — Traditions & spiritualité
* **Description** : fêtes traditionnelles, événements religieux ou spirituels et folklore.
* **Règles** : fête patronale, procession, messe/concert sacré à visée cultuelle, pèlerinage,
  fêtes calendaires traditionnelles, folklore provençal, cérémonies spirituelles, méditation.
* **Exemples** : fête de la Saint-Jean, procession, cérémonie traditionnelle, bénédiction.
* **Contre-exemples** : fête de village sans dimension traditionnelle (→ FESTIVAL), concert d'orgue
  (→ MUSIC).
* **Ambigus** : le caractère traditionnel/religieux doit être explicite ; sinon FESTIVAL/MUSIC.

### YOUTH — Jeunesse
* **Description** : activités destinées principalement aux adolescents et jeunes adultes
  (env. 12–25 ans).
* **Règles** : événements de maisons des jeunes, services jeunesse, animations ados, informations
  jeunesse, soirées étudiantes.
* **Exemples** : soirée étudiante, animation maison des jeunes, forum jeunesse.
* **Contre-exemples** : activités enfants (→ FAMILY), concert tous publics (→ MUSIC).
* **Ambigus** : YOUTH est une catégorie de public plus que de nature d'activité ; sa **pertinence
  comme catégorie** (vs tag « jeunesse ») est à confirmer par les données réelles. Candidate à
  fusion avec FAMILY si le volume est faible.

### OTHER — Autre
* **Définition** : catégorie **valide et officielle** de dernier recours, pour tout événement
  **existant mais dont la classification est impossible** (aucune règle de CAT-002 ne s'applique
  avec une confiance suffisante).
* **Règles** :
  * **Cas A — événement non classifiable : `Category = OTHER`, aucune erreur HTTP.** Un événement
    n'est jamais rejeté, ni laissé sans catégorie (`null`), à cause d'une classification impossible ;
  * `OTHER` n'est **pas** un repli pour les données invalides (titre vide, coordonnées absentes…) :
    celles-ci restent traitées par les règles de validation existantes ;
  * `OTHER` ne doit pas devenir un fourre-tout : le taux d'activités `OTHER` est un **indicateur
    qualité** suivi dans le rapport de migration (CAT-005) ; un volume élevé pour un même motif doit
    déclencher l'ajout d'une règle CAT-002 ou d'une nouvelle catégorie.
* **Exemples** : « Événement XYZ » sans mot-clé ni description ; titre non descriptif (« Rencontre »).
* **Contre-exemples** : tout événement pouvant se rattacher à une autre catégorie même de façon
  moyennement fiable (à arbitrer par CAT-002).
* **Ambigus** : un événement à double thématique forte n'est **pas** OTHER : la règle de priorité
  (CAT-002) désigne la catégorie.

## 4. Catégorie inconnue ≠ événement non classifiable

| Cas | Situation | Comportement |
|---|---|---|
| **A** | Un événement existe mais aucune règle ne permet de déterminer sa catégorie | `Category = OTHER`, **pas d'erreur HTTP** (moteur : CAT-004 ; imports : CAT-006 ; migration : CAT-005) |
| **B** | Un client demande explicitement une catégorie absente du référentiel (ex. `GET /activities?category=UNKNOWN`) | **`400 Bad Request`** (lecture : filtres `/nearby`, `/within-bounds`… ; écriture : création/modification) — implémenté en CAT-007 |

Ceci **remplace la décision LL-4004** (catégorie inconnue → liste vide) pour les endpoints de
lecture ; le remplacement ne se fait qu'en CAT-007 (aucune modification de code ici).
Le cas B s'applique aux **codes** du référentiel (`MUSIC`, `OTHER`…) : `OTHER` est donc un code valide
pour le filtre ; une catégorie **désactivée** (`active = false`) reste une valeur connue (pas de 400,
comportement à préciser en CAT-003).

## 5. Décisions et points à valider (Alex)

1. Valider le principe « `FoodTruck.category` n'est pas une catégorie LocalLife » (§2.2), à
   confirmer après extraction de `SELECT category, COUNT(*) FROM food_truck GROUP BY 1`.
2. Valider les règles Cas A/Cas B (§4) — reprises telles que fournies par Alex.
3. Confirmer les libellés (§3) et l'ordre d'affichage.
4. Points de périmètre à trancher **avec les données réelles** : pertinence de `YOUTH` (vs `FAMILY`),
   frontière `MARKET`/`COMMERCE` (braderie), rôle exact de `LOCAL_LIFE` comme catégorie de repli
   civique, placement de `FESTIVAL` (multi-domaines) et distinction `SHOW`/`CULTURE`.

## 6. Confrontation aux données réelles — À COMPLÉTER

Ces sections doivent être remplies avec la sortie des requêtes de `CATEGORIES_AUDIT.md` §6.2, exécutées
par environnement accessible. Tant qu'elles sont vides, le référentiel reste **PROVISOIRE**.

| Environnement | Date d'extraction | Nb activités | Nb sans catégorie | Nb valeurs distinctes | Extraction jointe ? |
|---|---|---|---|---|---|
| dev | *à compléter* | | | | ☐ |
| beta | *à compléter* | | | | ☐ |
| prod | *à compléter* | | | | ☐ |

### 6.1 Top des valeurs `activity.category` (par source)
*À compléter.*

### 6.2 Variantes regroupables (casse/accents/pluriels)
*À compléter.*

### 6.3 Top des tags OpenAgenda (`activity_tag.tag`)
*À compléter.*

### 6.4 Valeurs `food_truck.category`
*À compléter.*

### 6.5 Écarts référentiel ↔ données réelles et décisions prises
*À compléter : pour chaque catégorie conservée / fusionnée / supprimée / ajoutée, justification
chiffrée. Tout changement du §3 doit être motivé ici.*

## 7. Critères d'acceptation CAT-001

* [x] Chaque catégorie possède un code stable (règle §1.1, tableau §3)
* [x] Les codes sont indépendants du libellé (règle §1.2)
* [x] Chaque catégorie possède une définition
* [x] Chaque catégorie possède des exemples et contre-exemples
* [x] Les cas ambigus sont documentés
* [x] `OTHER` est défini (Cas A / Cas B, §3 et §4)
* [~] Le document constitue le référentiel métier officiel : **provisoire** tant que §6 n'est pas complété
* [x] Aucun code applicatif modifié (ni `Activity`, ni base, ni imports, ni frontend, ni `FoodTruck`)
