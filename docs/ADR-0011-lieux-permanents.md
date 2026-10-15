# ADR-0011 — Lieux permanents : faire évoluer `Location` en référentiel consultable

**Statut : principes acceptés par Alex le 13/10/2026 ; proposition
détaillée (§ 7) en attente de validation.** Principes acceptés :
réutiliser `Location` sans entité concurrente, précision géographique
`EXACT`/`APPROXIMATE`/`UNKNOWN`, marchés comme lieux permanents aux jours
éventuellement à confirmer, stratégie de doublons sans fusion destructive.
Validation **phase par phase** (décision du 13/10/2026) : aucune migration
de modèle n'est écrite avant la validation de sa phase ; le SQL du § 7 est
documentaire.

| Phase | Statut |
|---|---|
| A — précision géographique | **validée le 14/10/2026** (§ 7.1.7) ; **implémentée et testée** (§ 7.1.8, migration V61) ; déployée après validation de la PR |
| B — lieux permanents | **décisions du 14/10/2026** (§ 7.2.6) ; **implémentée et testée** : backend (§ 7.2.7, migration V62) et interface (§ 7.2.8) ; rapprochement avec les lieux d'événement reporté |
| C — marchés et horaires | ontologie reconstituée à partir des données (§ 7.3) : jours de marché **présents dans le N-Triples** (dates d'une séance ou `appliesOnDay`), perdus dans le CSV ; **compte diffuseur décidé le 14/10/2026** (§ 7.3.6), mesure à faire avant tout import |
| D — liens de doublons | mesure sans écriture outillée et étalonnée (0 faux positif sur 1 probable, 3 sur 14 ambiguës) ; mesure sur les données de l'application à faire ; règle **non branchée** |

Révision du 13/10/2026 (retour d'audit de la PR #104) : précision
géographique (§ 2.6), marchés et lieux permanents (§ 2.7), données
N-Triples (§ 2.8), stratégie de doublons OpenAgenda / DATAtourisme
(§ 2.9). Le défaut 1.3.3 est corrigé dans la PR #104.

Données chiffrées : export DATAtourisme PACA publié sur data.gouv.fr le
13/10/2026 (`datatourisme-reg-pac.csv`), date de référence 13/10/2026.

## 1. Audit de l'existant

### 1.1 Modèle

| Élément | Constat |
|---|---|
| `place/domain/Location` (V17) | Lieu autonome : `name`, `address`, `postal_code`, `city`, `department`, `region`, `country_code`, `insee`, `latitude`, `longitude`, `timezone`, `website`, `email`, `phone`. Tous nullables. `geo_location` (PostGIS) calculée par trigger, index GIST. |
| Relations | Aucune relation vers `Activity`. Le lien passe par la programmation : `schedule.location_id` (lieu par défaut) et `occurrence.location_id` (lieu exceptionnel). Lieu effectif d'une occurrence = `COALESCE(occurrence.location_id, schedule.location_id)` (ADR-0006). |
| Partage | Un même lieu peut servir à plusieurs activités ; supprimer une programmation ne supprime jamais le lieu (`RecurringContributionService#clearSchedules`). |
| Absent du modèle | Statut de publication, description, type de lieu, provenance (source, identifiant externe), médias, horaires d'ouverture. |

### 1.2 Code

| Couche | Constat |
|---|---|
| `LocationRepository` | `findAll`, `findById`, `save`, `findFirstByAddressAndCityAndPostalCode…`, `findFirstByLatitudeAndLongitude…`. Aucune recherche géographique. |
| `LocationService` | `create` (refuse un lieu sans nom, adresse ni coordonnées), `findOrCreate` (rapprochement, voir 1.3). Aucune mise à jour : un lieu n'est jamais modifié après création. |
| API | **Aucun endpoint `place`.** Un lieu n'est visible qu'à travers la fiche d'une activité (`ActivityDetailResponse.LocationSummary`). |
| Écritures | 1) import (`ImportService` → `NormalizationService#normalizeLocation` → `findOrCreate`) ; 2) contribution récurrente (`RecurringContributionService#attachSchedule`). Les deux ne renseignent ni `website`, ni `email`, ni `phone`, ni `department`, ni `insee`. |
| Lectures | Recherches publiques (`ActivityRepository#findWithinRadius`/`findWithinBounds`, `ActivitySearchRepository`, liste par ville) : jointure sur le lieu effectif pour la position (`geo_location`), la ville et le fuseau. |
| Carte | Les marqueurs d'activité utilisent les colonnes plates `activity.latitude`/`longitude`, mais le **filtre** rayon/zone utilise `location.geo_location`. Les food trucks sont une couche distincte (`/api/v1/foodtrucks`, coordonnées propres) : c'est le précédent d'un point de carte qui n'est pas une activité. |

### 1.3 Défauts constatés (existant + PR #104)

1. **Rapprochement de lieux sur l'adresse seule.** `findOrCreate` réutilise
   le premier lieu de même `address`/`city`/`postal_code`, sans comparer
   les coordonnées. Sur PACA, 37 des 3 171 événements à venir (1,2 %)
   partagent une adresse avec des points distants de plus de 200 m (jusqu'à
   5,6 km, ex. `CD 10 Route de St Chamas Château Virant`, Lançon). Dans le
   Vaucluse : 2 événements (Bonnieux, 2 km). Effet : ces événements sont
   filtrés en recherche rayon/zone sur la position d'un autre. Les autres
   adresses partagées (22 groupes, 67 événements dans le Vaucluse)
   désignent bien le même bâtiment (0 m d'écart) : le partage est correct.
2. **Déduplication entre sources inopérante entre OpenAgenda et
   DATAtourisme** (PR #104). `ImportFingerprintService#existsLiveDuplicateInAnotherSource`
   exige le même `external_id` dans les deux sources. Or OpenAgenda fournit
   un slug et DATAtourisme une URI : un même événement présent dans les
   deux ne sera **jamais** reconnu. La règle compare aussi la date de début
   exacte : DATAtourisme n'a pas d'heure (00:00), OpenAgenda en a une. Ce
   n'est donc pas vérifiable « sur données réelles » : il n'y a rien à
   observer, le cas ne peut pas se déclencher. Mesure du recouvrement réel
   impossible depuis l'environnement de développement (voir § 6).
3. **Événements longs masqués par la fenêtre de 7 jours** (existant,
   aggravé par la PR #104). Sans date choisie, `findWithinRadius`/
   `findWithinBounds` ne retiennent une occurrence que si son **début ou
   sa fin** tombe dans les 7 prochains jours. Une occurrence qui couvre
   toute la fenêtre est exclue. DATAtourisme encode souvent les marchés
   hebdomadaires sur une seule période annuelle (`2026-01-01<->2026-12-31`) :
   dans le Vaucluse, **103 des 159 événements actifs dans les 7 prochains
   jours n'apparaissaient pas** sur la carte par défaut. Depuis que le
   collecteur écarte les périodes de validité (marchés publiés pour toute
   l'année), il en reste 9 sur 64 : 8 expositions de 16 à 214 jours et
   « Le Festival TOUTIFESTI » (typé spectacle, 18 jours). Tous sont
   importés (la règle des 31 jours ne les concerne pas : type continu, ou
   période de 31 jours au plus) ;
   seule la fenêtre les masque, chacun ayant commencé avant aujourd'hui et
   finissant après la fenêtre.
   **Corrigé (PR #104)** : la fenêtre retient désormais une occurrence qui
   la chevauche (début au plus tard dans 7 jours, fin aujourd'hui ou plus
   tard), dans `findWithinRadius` et `findWithinBounds`, pour les deux
   branches (occurrences et colonnes plates). La nouvelle condition
   contient l'ancienne : rien de ce qui était visible ne disparaît.
   `DefaultDateWindowIntegrationTest` échoue sur l'ancienne requête et
   passe sur la nouvelle. Le seuil de 31 jours n'est pas modifié.

## 2. Décision proposée

### 2.1 Principe

Un lieu permanent **est** une `Location`, enrichie et publiée. Pas de
seconde entité « lieu » : les adresses d'événements et les lieux
permanents partagent la même table, ce qui permet de rattacher les
événements d'un théâtre à la fiche du théâtre.

Une `Location` est un **lieu permanent** si et seulement si `place_type`
est renseigné. Les lignes existantes (`place_type` nul) restent de simples
lieux d'événements : aucun changement de comportement pour elles.

### 2.2 Évolution du modèle (migration unique, colonnes nullables)

`location`, colonnes ajoutées :

| Colonne | Rôle |
|---|---|
| `place_type VARCHAR(50)` | Type de lieu (code interne : `MUSEUM`, `THEATER`, `SPORTS_FACILITY`, `HERITAGE`, …). Nul = lieu d'événement seulement. |
| `status VARCHAR(20)` | `PENDING`/`PUBLISHED`/`REJECTED`/`ARCHIVED`, mêmes valeurs qu'`Activity`. Nul tant que `place_type` est nul (contrainte `CHECK`). |
| `description TEXT` | Texte de présentation. |
| `published_at TIMESTAMPTZ` | Comme `activity`/`food_truck` (trigger). |

Nouvelle table de **provenance** `location_source` (une ligne par source
qui décrit le lieu, plusieurs sources par lieu) :

| Colonne | Rôle |
|---|---|
| `location_id` → `location(id)` | Lieu fusionné. |
| `source_id` → `source(id)` | Source (DATAtourisme, Data ES, Basilic…). |
| `external_id` | Identifiant chez la source (URI DATAtourisme, n° d'équipement Data ES, identifiant Basilic). Unique par `(source_id, external_id)`. |
| `content_hash`, `last_seen_at` | Ré-import incrémental et archivage des lieux disparus, comme `import_fingerprint`. |

C'est cette table, et non une colonne de `location`, qui porte la
déduplication multi-sources : un même lieu peut être décrit par trois
sources.

Hors périmètre de cette étape : horaires d'ouverture, médias de lieux,
catégories partagées avec les activités. À traiter par un ADR ultérieur
si le besoin est confirmé.

### 2.3 Déduplication des lieux importés

Ordre de rapprochement d'un lieu collecté :

1. même source + même `external_id` → même lieu (ré-import) ;
2. autre source : lieu permanent existant à moins de **100 m**
   (`ST_DWithin` sur `geo_location`), même code postal, et nom normalisé
   identique ou inclus (`ll_search_normalize`, V58) → fusion, une ligne de
   provenance ajoutée ;
3. lieu d'événement existant (`place_type` nul) répondant aux mêmes
   critères → **promu** en lieu permanent (les événements déjà rattachés
   apparaissent alors sur sa fiche) ;
4. sinon → nouveau lieu. Un cas ambigu (plusieurs candidats) n'est jamais
   fusionné automatiquement : nouveau lieu en `PENDING`.

Règle de mise à jour : une source ne remplace que les champs vides ou les
champs qu'elle a elle-même fournis ; elle ne déplace jamais un lieu de
plus de 100 m s'il porte des programmations (sinon les événements
bougeraient avec lui).

Correctif lié (défaut 1.3.1, indépendant du reste) : `findOrCreate` ne
réutilise un lieu de même adresse que s'il est à moins de 100 m.

### 2.4 Lecture

Module `place`, nouveau `place/api/PlaceController` :

- `GET /api/v1/places/within-bounds?swLatitude=…` (carte) et
  `GET /api/v1/places/nearby?…` : lieux `PUBLISHED` uniquement, même
  contrat de zone que les activités ;
- `GET /api/v1/places/{id}` : fiche du lieu + activités à venir qui s'y
  déroulent (jointure lieu effectif, même règle qu'ADR-0006).

Frontend : couche de carte distincte (comme les food trucks), activable,
et onglet « Lieux » dans la liste. Un lieu n'a ni date ni occurrence : il
n'est jamais converti en activité.

### 2.5 Import

Collecte séparée des événements : interface `PlaceCollector` qui renvoie
des `CollectedPlace`, service `PlaceImportService` (module `collector`,
même patron qu'`ImportService`). Le collecteur DATAtourisme d'événements
n'est pas modifié ; le collecteur de lieux réutilise son lecteur CSV et
le même fichier régional (le fichier PACA contient déjà les lieux).

Ordre des sources, selon les décisions d'Alex :

| Source | Données (constat) | Point à trancher |
|---|---|---|
| DATAtourisme lieux | Vaucluse : 3 619 lignes `PlaceOfInterest` (3 483 lieux une fois retirés ceux qui sont aussi événement, produit ou circuit, § 7.2.3), tous géolocalisés, 204 sans adresse. Dominés par la restauration (986), les commerces (731), l'hébergement (445). Pertinents a priori : sites culturels (345), sports et loisirs (708), patrimoine (59), lieux religieux (68), galeries (74). | Liste blanche des types à importer. |
| Data ES (équipements sportifs) | Non encore analysé (étape 3). Données quotidiennes, nationales, filtrables côté serveur. | Quels équipements : tous, ou seulement ceux ouverts au public ? |
| Basilic (lieux culturels) | Non encore analysé (étape 3). Publication ponctuelle. | Fréquence de mise à jour acceptable. |

### 2.6 Précision géographique des coordonnées

**Constat.** Une `Location` a toujours des coordonnées traitées comme
exactes : marqueur, rayon de recherche, bouton d'itinéraire. Or certaines
ne désignent pas un lieu précis : adresses génériques DATAtourisme
(« Divers lieux », « Centre ville » : 4 événements retenus dans le
Vaucluse le 13/10/2026), géocodage d'une contribution manuelle qui
n'aboutit qu'à la rue ou à la commune.

**Proposition.** Colonne `location.coordinate_precision VARCHAR(16) NOT
NULL DEFAULT 'UNKNOWN'`, contrainte `CHECK` sur trois valeurs :

| Valeur | Sens | Qui la pose |
|---|---|---|
| `EXACT` | Position du lieu lui-même (bâtiment, entrée). | Source qui le garantit, ou validation manuelle. |
| `APPROXIMATE` | Position estimée : centre de commune, zone, lieu multiple. | Collecteur qui le détecte (adresse générique DATAtourisme), géocodage de niveau rue ou commune. |
| `UNKNOWN` | Précision non établie. | Valeur par défaut, et toutes les lignes existantes. |

Règle d'affichage : `EXACT` et `UNKNOWN` s'affichent comme aujourd'hui
(rien ne change pour les données existantes) ; seul `APPROXIMATE` change.

| Zone | Impact |
|---|---|
| Modèle `Location` | Un champ. `CollectedLocation` (domaine collecteur) porte la précision jusqu'à `NormalizationService`. |
| DATAtourisme | Adresse générique → `APPROXIMATE` (règle déjà en place : nom du lieu, sans adresse). Sinon `UNKNOWN` : le CSV ne dit pas si le point est géocodé à l'adresse ou au centre de la commune. Une vérification contre le centre de commune (référentiel géographique) pourrait affiner, non retenue à ce stade. |
| OpenAgenda | `UNKNOWN` : l'API ne qualifie pas la précision. Ne pas présumer `EXACT`. |
| Contributions | Géocodage Nominatim : le type de résultat (maison, rue, commune) permettrait `EXACT`/`APPROXIMATE`. Second temps. |
| Données existantes | Toutes `UNKNOWN` par la valeur par défaut. Pas de rattrapage déduit : aucune ligne n'a de provenance fiable (§ 1.1). |
| Mise à jour | `findOrCreate` ne modifie jamais un lieu existant : un lieu déjà créé garderait `UNKNOWN`. Il faut une règle « une source peut abaisser `EXACT`/`UNKNOWN` vers `APPROXIMATE` pour un lieu qu'elle a créé », ou reprendre les lieux génériques déjà importés. |
| Rapprochement | Un lieu `APPROXIMATE` n'est jamais fusionné par coordonnées avec un autre, et n'est jamais promu en lieu permanent (§ 2.3). |
| Recherche | Inchangée : un lieu approximatif reste dans le rayon calculé sur son point. |
| Carte | Marqueur distinct (contour ou halo) et mention « Emplacement approximatif » au survol. |
| Liste et fiche | Mention « Emplacement approximatif » ; le bouton d'itinéraire vise la commune, pas le point. |
| Colonnes plates d'`Activity` | `activity.latitude`/`longitude` restent obligatoires et inchangées ; la précision est lue sur le lieu effectif. L'API d'activité expose un champ `coordinatePrecision` (ajout, non cassant). |

Migration : une colonne, valeur par défaut, `CHECK` ; aucun rattrapage de
données. Tests : migration (lignes existantes en `UNKNOWN`, valeur hors
liste refusée), collecteur DATAtourisme (générique → `APPROXIMATE`,
adresse précise → `UNKNOWN`), `findOrCreate` (un lieu approximatif n'est
pas réutilisé par coordonnées), API (champ exposé), frontend (marqueur et
mention, itinéraire vers la commune), non-régression des recherches.

### 2.7 Marchés : lieux permanents, jours de présence à confirmer

**Constat.** La règle des 31 jours (validée, non modifiable) écarte de
l'import d'événements les marchés publiés pour toute une saison : 100
événements sur 239 dans le Vaucluse le 13/10/2026, dont 88 marchés
(« Marché de Châteauneuf-du-Pape », 2026-01-01 → 2026-12-31), 3 visites
et 9 autres.
Le CSV simplifié ne donne pas les jours.

**Proposition**, dans le cadre du § 2.2, sans nouvelle entité :

- un marché est une `Location` de `place_type = 'MARKET'` ;
- ses jours de présence sont des **horaires d'ouverture** du lieu, pas des
  occurrences d'activité : table `location_opening_hours` (`location_id`,
  `day_of_week`, `opens`, `closes`, `valid_from`, `valid_until`, `source_id`),
  calquée sur `schema:OpeningHoursSpecification` qu'utilise DATAtourisme ;
- sans horaires connus, le lieu s'affiche avec « Jours de marché à
  confirmer » et la saison de la source (« publié du 1er janvier au 31
  décembre ») ; aucune date ni récurrence n'est déduite ;
- jamais d'occurrence générée à partir d'horaires d'ouverture : un marché
  reste un lieu, consultable sur la carte et dans la liste « Lieux ».

Alternative écartée : un marché comme activité récurrente (`Schedule`
avec une règle `RRULE` hebdomadaire). Elle n'est possible que si les jours
sont connus, crée une activité par saison, et mélange un lieu permanent
avec un événement daté. Elle reste envisageable plus tard pour un marché
exceptionnel (marché de Noël).

### 2.8 Données DATAtourisme complètes (N-Triples) : étude de faisabilité

Étude ponctuelle et limitée (13/10/2026), sans traitement complet ni
connecteur : lecture en flux du **premier quart de l'archive** (480 Mo
compressés sur 1,93 Go, 26 %), par requêtes HTTP partielles, décompression
à la volée, rien n'est écrit sur disque.

**Méthode d'extraction.** L'archive `datatourisme.nt.zip` ne contient
qu'un fichier, `dataset.nt` (N-Triples). Le serveur accepte les requêtes
partielles (206), ce qui permet de lire un préfixe sans tout télécharger.
Mais la compression (deflate) impose de lire depuis le début : on ne peut
pas sauter au milieu du fichier. Dans le flux, on garde les marchés
(`core#Market`) et leurs liens `core#takesPlaceAt`, les nœuds datés, et
les nœuds `schema:OpeningHoursSpecification`. On tente ensuite la jointure
marché → période → horaires.

**Résultats mesurés :**

| Mesure | Valeur |
|---|---|
| Triplets lus | 18,9 millions |
| Marchés (`core#Market`) | 3 168 (France entière, part lue) |
| Marchés dont la période est retrouvée dans la part lue | 255 (8 %) |
| Marchés reliés à des horaires (jours, ouverture, fermeture) | **0** |
| Nœuds d'horaires (`schema:OpeningHoursSpecification`, avec `schema:dayOfWeek`, `opens`, `closes`) | 2 769 |
| Liens trouvés d'un nœud daté vers un nœud d'horaires | **0** |

**Organisation du fichier**, mesurée sur un extrait de 60 Mo : les
triplets d'un même sujet sont contigus, mais les nœuds liés d'un point
d'intérêt (description, localisation, période) sont rangés **ailleurs**
dans le fichier : 0 retrouvé à proximité sur 1 241 liens de période. Les
nœuds d'horaires existent, avec les bons champs (exemple : jeudi
14:00-18:00), mais aucun nœud de l'extrait n'y renvoie. La propriété qui
relie une période ou un lieu à ses horaires **n'a pas pu être
identifiée**. La documentation de l'ontologie (framagit) n'est pas
joignable depuis l'environnement de développement.

**Coût :** 55 minutes pour 26 % du fichier ici, limitées par le réseau
(0,15 Mo/s à travers le proxy de l'environnement de développement).
Estimations pour le fichier entier : environ 15 Go décompressés et
77 millions de triplets. Sur un serveur au débit normal, le téléchargement
prend quelques minutes, mais l'analyse en Python d'un fichier dont les
nœuds liés sont dispersés demande soit de garder en mémoire tous les nœuds
utiles, soit deux passes complètes.

**Qualité :** non démontrée. Rien, dans la part lue, ne montre qu'un
marché ait des jours de présence exploitables.

**Limites de l'étude :** préfixe de 26 % seulement ; le lien vers les
horaires est peut-être situé dans les 74 % non lus, ou passer par une
propriété non suivie ici (par exemple sur le lieu plutôt que sur la
période).

**Mise à jour du 13/10/2026 (après-midi)** : la propriété manquante est
identifiée et les jours de marché sont bien présents dans le N-Triples :
voir § 7.3, qui remplace la recommandation ci-dessous.

**Recommandation initiale (phase C) :** ne pas développer de connecteur N-Triples
pour les jours de marché à ce stade. Avant tout traitement complet :

1. lire la définition de l'ontologie (dépôt framagit ou GitLab ADN
   Tourisme), pour identifier la propriété qui porte les jours d'un
   marché ;
2. ou ouvrir un compte gratuit sur la plateforme diffuseur DATAtourisme,
   créer un flux limité aux marchés du Vaucluse, et examiner à la main une
   dizaine de fiches complètes (JSON). C'est le moyen le moins coûteux de
   savoir si les jours sont renseignés ;
3. seulement si l'une de ces deux étapes montre des jours exploitables,
   décider d'un traitement complet ponctuel, sur un serveur au débit
   normal, pour mesurer la proportion de marchés concernés.

En attendant, la phase C reste au niveau du principe : marché comme lieu
permanent, « Jours de marché à confirmer ».

### 2.9 Doublons OpenAgenda / DATAtourisme : stratégie proposée

**Constat.** La règle actuelle (même `external_id`) ne peut pas rapprocher
ces deux sources (§ 1.3.2). Mesure sur DATAtourisme PACA seul, faute
d'accès à OpenAgenda : parmi les événements à venir, les paires de **même
titre normalisé et même jour** se répartissent ainsi :

| Distance | Paires | Exemples |
|---|---|---|
| ≤ 300 m | 26 | « Cendrillon », même salle (0 m) ; « Cinéma », Saintes-Maries (2 m) |
| 300 m – 5 km | 3 | « Jouer la mouette », Marseille 3e et 2e (1,9 km) |
| > 5 km | 5 | « Fête de la musique » à Port-de-Bouc, Marignane et Salon (18 à 27 km) ; « Octobre Rose », Lambesc et Barbentane (50 km) |

Conséquences : le titre et la date seuls donnent des faux positifs ; la
distance est indispensable. Même à moins de 300 m, un même titre peut
désigner deux séances distinctes, et DATAtourisme n'a pas d'heure.

**Critères**, du plus fiable au moins fiable :

1. même jour pour au moins une occurrence de chaque côté (comparaison au
   jour : DATAtourisme n'a pas d'heure) ;
2. distance ≤ 300 m entre lieux effectifs, si aucun n'est `APPROXIMATE`
   (§ 2.6) ; sinon même commune ;
3. titre : normalisé identique (`ll_search_normalize`), ou un titre
   contenant au moins 80 % des mots de l'autre ;
4. titres génériques (« Marché », « Cinéma », « Visite guidée »,
   « Concert »…) : jamais suffisants, même avec 1 et 2.

**Décisions graduées, sans fusion destructive :**

| Niveau | Condition | Effet |
|---|---|---|
| Probable | 1 + 2 (300 m) + titre identique, non générique | L'événement DATAtourisme est **masqué** au profit de celui d'OpenAgenda (source prioritaire) : lien enregistré, réversible, visible par un administrateur. Rien n'est supprimé ni fusionné. |
| Possible | 1 + 2, titre proche ou générique ; ou lieu approximatif dans la même commune | Les deux restent publiés ; paire proposée à la relecture d'un administrateur. |
| Aucun | Autres cas | Rien. |

Mise en œuvre en deux temps :

- **Mesure** : calcul des paires, sans aucune écriture, sur les données
  réelles importées ; revue des faux positifs avant tout masquage
  automatique.
- **Application** : une table de liens (`activity_duplicate_link` :
  activité, doublon de, règle, niveau, statut auto/confirmé/rejeté). C'est
  un changement de modèle, soumis à validation.

Le mécanisme actuel (même `external_id`, archivage du doublon le plus
récent) reste en place pour les doublons d'une même plateforme.

## 7. Proposition détaillée (à valider avant toute migration)

Le SQL ci-dessous est un **projet** : aucun fichier de migration n'existe.
Livraison en quatre phases indépendantes, chacune avec sa migration, ses
tests et sa validation.

### 7.1 Phase A — précision géographique : dossier de validation

**Priorité haute. Statut : conception finalisée ; règles implémentées et
testées sans changement de schéma, non branchées ; migration non créée.**

**Audit des usages des coordonnées** (13/10/2026) :

| Usage | Coordonnées lues | Effet d'une position approximative aujourd'hui |
|---|---|---|
| Carte web et mobile (`/nearby`, `/within-bounds`) | marqueur : `activity.latitude/longitude` ; filtre : `location.geo_location` | marqueur présenté comme exact |
| Bouton d'itinéraire (`frontend/src/utils/directions.ts`) | coordonnées de l'activité | guidage vers un point qui n'est pas le lieu |
| Recherche globale (`ActivitySearchRepository`) | `location.geo_location` | distance affichée et tri présentés comme exacts |
| Alertes (`ActivityAlertRepository`) | `location.geo_location`, repli `activity.location` | alerte déclenchée (ou non) sur un point approximatif |
| Rapprochement de lieux (`LocationService#findOrCreate`) | coordonnées exactes en repli de l'adresse | deux événements « Divers lieux » d'une même commune peuvent partager un lieu |
| Application mobile (`mobile/src/api/types.ts`) | `latitude`/`longitude` des réponses | idem carte web |

**Choix proposés, avec leurs compromis :**

| # | Question | Proposition | Alternative | Compromis |
|---|---|---|---|---|
| A1 | Où stocker la précision | `location.coordinate_precision` (le lieu porte les coordonnées) **et** copie sur `activity` (`activity.coordinate_precision`), comme `latitude`/`longitude` y sont déjà copiées | calcul à la lecture par jointure sur le lieu effectif | la copie évite une jointure par activité dans les listes et la carte, au prix d'une colonne dupliquée à tenir cohérente à l'import |
| A2 | Valeurs | `EXACT`, `APPROXIMATE`, `UNKNOWN` ; défaut `UNKNOWN` | deux valeurs (exact / approximatif) | `UNKNOWN` évite d'affirmer une précision qu'aucune source ne garantit |
| A3 | Qui pose `EXACT` | personne au départ ; plus tard : géocodage de niveau « adresse » d'une contribution, ou validation par un administrateur | OpenAgenda en `EXACT` | aucune source actuelle ne garantit la précision ; `EXACT` resterait vide au début, sans effet visible |
| A4 | Qui pose `APPROXIMATE` | collecteur DATAtourisme (adresse générique, règle déjà en place) ; géocodage limité à la rue ou à la commune | détection du « centre de commune » par comparaison à un référentiel | la comparaison au centre de commune demande un référentiel externe ; écartée pour l'instant |
| A5 | Données existantes | toutes `UNKNOWN` par défaut ; **rattrapage ciblé optionnel** : les lieux sans adresse dont le nom est générique (même règle que le collecteur) passent en `APPROXIMATE` | aucun rattrapage | le rattrapage est heuristique ; il ne vise que des cas reconnus par la règle déjà testée |
| A6 | Rapprochement | la précision fait partie du rapprochement : un lieu `APPROXIMATE` n'est jamais réutilisé par coordonnées pour un autre événement, ni l'inverse | aucune règle | quelques lieux supplémentaires en base pour les événements multi-lieux |
| A7 | Affichage | `APPROXIMATE` : marqueur distinct, mention « Emplacement approximatif » (carte, liste, fiche), itinéraire vers la commune ; `EXACT` et `UNKNOWN` : inchangés | marqueur masqué | un point reste nécessaire pour situer l'événement sur la carte |
| A8 | Alertes | une activité `APPROXIMATE` déclenche l'alerte si son point est dans le rayon, avec la mention « emplacement approximatif » dans le message | exclure les positions approximatives | ne rien envoyer ferait manquer des événements réels de la commune |
| A9 | API | champ `coordinatePrecision` ajouté aux réponses d'activité (ajout, non cassant) | — | les anciennes versions de l'application mobile ignorent le champ et continuent d'afficher un point exact jusqu'à leur mise à jour |

#### 7.1.1 Décisions d'Alex (13/10/2026)

Validés : les trois niveaux `EXACT`/`APPROXIMATE`/`UNKNOWN` ; une stratégie
explicite pour les données existantes, **sans attribuer arbitrairement une
précision exacte** ; l'étude des impacts sur `Location`, les imports, la
carte et les listes ; SQL et migration préparés ici, **non appliqués** avant
validation de l'implémentation détaillée.

#### 7.1.2 Conception finalisée

| Règle | Décision de conception | Où (implémenté, non branché) |
|---|---|---|
| Valeurs | `EXACT`, `APPROXIMATE`, `UNKNOWN` ; défaut `UNKNOWN` | `place.domain.CoordinatePrecision` |
| Import | jamais `EXACT` ; `APPROXIMATE` si le texte du lieu est générique, sinon `UNKNOWN` | `CoordinatePrecisionPolicy.inferFromText` |
| Texte examiné | l'adresse, ou le nom à défaut (le collecteur DATAtourisme range une adresse générique dans le nom et vide l'adresse) ; une adresse précise l'emporte sur un nom générique | idem |
| Texte générique | règle unique, partagée avec le collecteur DATAtourisme (déplacée, comportement inchangé) | `CoordinatePrecisionPolicy.isGenericPlaceText` |
| Données existantes (A5) | **même règle**, appliquée en SQL : `APPROXIMATE` pour les lieux reconnus, `UNKNOWN` pour tous les autres ; jamais `EXACT` | § 7.1.3, parité Java / SQL testée |
| Réutilisation (A6, **précisée**) | entre lieux non approximatifs : règle actuelle ; dès qu'un lieu est approximatif : réutilisé **seulement** par un lieu approximatif de même nom et même commune (normalisés) | `CoordinatePrecisionPolicy.mayReuse` |
| Lieu réutilisé | précision inchangée : un import n'abaisse pas une validation humaine et n'élève rien | `CoordinatePrecisionPolicy.onReuse` |
| Copie sur l'activité (A1) | celle du lieu dont l'activité affiche le point (mêmes coordonnées) | rattrapage § 7.1.3 ; à l'import : `NormalizationService` (à brancher) |
| Affichage (A7) | seul `APPROXIMATE` change : mention « Emplacement approximatif », marqueur distinct, itinéraire vers l'adresse ou la commune | `CoordinatePrecisionPolicy.isShownAsApproximate` ; frontend : `buildDirectionsUrl` (champ optionnel) |

**Pourquoi A6 a été précisée.** L'import rattache le lieu par
`LocationService#findOrCreate` à **chaque** réimport (`ImportService#enrichActivity`
supprime puis recrée programmations et occurrences). La règle initiale (« un
lieu approximatif n'est jamais réutilisé par coordonnées ») créerait donc un
nouveau lieu à chaque import horaire pour chaque événement approximatif. La
règle retenue réutilise le lieu approximatif de même nom et même commune,
et sépare toujours deux zones distinctes (« Divers lieux » et « Centre
ville » d'une même commune, géocodés au même centre).

#### 7.1.3 Migration et rattrapage (créés le 14/10/2026 : V61, § 7.1.8)

```sql
ALTER TABLE location ADD COLUMN coordinate_precision VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    CONSTRAINT chk_location_coordinate_precision
    CHECK (coordinate_precision IN ('EXACT', 'APPROXIMATE', 'UNKNOWN'));
ALTER TABLE activity ADD COLUMN coordinate_precision VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    CONSTRAINT chk_activity_coordinate_precision
    CHECK (coordinate_precision IN ('EXACT', 'APPROXIMATE', 'UNKNOWN'));

-- Rattrapage ciblé (A5). <bloc de classification> : le bloc « classification » de
-- infra/verification/phase_a_precision_mesure.sql, repris à l'identique (testé par
-- GenericPlaceSqlParityIntegrationTest).
WITH <bloc de classification>
UPDATE location l SET coordinate_precision = 'APPROXIMATE'
FROM precision_candidate p
WHERE p.id = l.id AND p.approximate AND l.coordinate_precision = 'UNKNOWN';

-- Copie sur l'activité (A1) : celle dont le point affiché est celui d'un lieu approximatif rattaché.
UPDATE activity a SET coordinate_precision = 'APPROXIMATE'
WHERE a.coordinate_precision = 'UNKNOWN'
  AND EXISTS (
      SELECT 1
      FROM schedule s
      LEFT JOIN occurrence o ON o.schedule_id = s.id
      JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
      WHERE s.activity_id = a.id
        AND l.coordinate_precision = 'APPROXIMATE'
        AND l.latitude = a.latitude AND l.longitude = a.longitude);
```

Le bloc de classification normalise en SQL comme en Java : minuscules,
accents retirés (`translate` sur les lettres accentuées françaises, en
minuscules et en majuscules, indépendant de la collation), ponctuation en
espaces, nom de commune retiré.

**Mesure préalable, en lecture seule** : `infra/verification/phase_a_precision_mesure.sql`
compte, avant toute migration, les lieux qui passeraient `APPROXIMATE`,
les activités rattachées et celles qui seraient marquées. Il liste aussi
les 100 premiers lieux concernés, pour relire les faux positifs. À exécuter
en bêta et en production avant de valider le rattrapage.

#### 7.1.4 Tests exécutés (13/10/2026)

| Test | Résultat |
|---|---|
| `CoordinatePrecisionPolicyTest` (9 cas : textes génériques et précis, adresse avant nom, jamais `EXACT`, réutilisation, précision inchangée, affichage) | réussi |
| `GenericPlaceSqlParityIntegrationTest` (PostgreSQL réel) : 21 lieux classés à l'identique par le SQL et par Java ; copies du bloc identiques ; script sans écriture | réussi ; **contrôle par mutation** : en retirant `le village` du SQL, le test échoue sur « Le Village / Gordes » |
| `DataTourismeCollectorTest` après délégation de la règle générique | 19 cas réussis, comportement inchangé |
| `directions.test.ts` : coordonnées sans précision, `UNKNOWN` ou `EXACT` ; commune pour `APPROXIMATE` ; point conservé sans commune | 4 cas réussis |
| Migration et rattrapage sur **base jetable** (copie de la base de test, jamais un environnement réel) | valeur hors liste refusée ; « Centre ville / Apt » et « Dans les rues du village / Gordes » → `APPROXIMATE` ; « Divers lieux » avec adresse « Place de l'Horloge » → `UNKNOWN` ; autres lieux `UNKNOWN` ; activité au point du lieu → `APPROXIMATE` ; activité au point différent → `UNKNOWN` ; second passage : 0 ligne modifiée |

#### 7.1.5 Impacts du branchement (après validation)

| Zone | Changement |
|---|---|
| `Location`, `Activity`, `CollectedLocation` | un champ `coordinatePrecision` |
| `DataTourismeCollector`, `OpenAgendaCollector` | posent `inferFromText` (OpenAgenda : `UNKNOWN` sauf texte générique) |
| `LocationRepository` / `LocationService#findOrCreate` | recherche par coordonnées filtrée par `mayReuse` (requête : même précision, et pour `APPROXIMATE` même nom et même commune) |
| `NormalizationService` | copie la précision du lieu sur l'activité |
| `ActivityResponse` et réponses de carte, de liste et de recherche | champ `coordinatePrecision` (ajout non cassant) |
| `ActivityAlertService` | mention « emplacement approximatif » (A8) |
| Frontend | `ExplorerMap` (marqueur), `ActivityList` et `ActivityDetailModal` (mention), `buildDirectionsUrl` (prêt) |
| Mobile | types et carte ; les anciennes versions ignorent le champ |
| Recherche géographique | inchangée (même point, même rayon) |

#### 7.1.6 Hypothèses restantes, à trancher avant toute modification du schéma

1. **Ampleur réelle du rattrapage** : inconnue tant que le script de mesure
   n'a pas tourné en bêta et en production. Seul chiffre réel : 4 événements
   DATAtourisme retenus sur 118 dans le Vaucluse ont une adresse générique.
   Les lieux OpenAgenda et les contributions n'ont jamais été mesurés.
2. **Faux positifs et négatifs de la règle** : elle ne reconnaît que des
   formulations relevées dans DATAtourisme. « Cœur de ville » ou « Dans la
   ville d'Avignon » restent `UNKNOWN` (choix prudent : une omission laisse
   l'affichage actuel ; un faux positif dégraderait un lieu précis). Faut-il
   élargir la liste après relecture de la mesure ?
3. **Activité dont le point affiché diffère de son lieu approximatif** (cas
   testé : activité à plusieurs lieux) : elle reste `UNKNOWN`. Acceptable,
   ou faut-il la marquer `APPROXIMATE` dès qu'un de ses lieux l'est ?
4. **Lieux en double existants** : avant la phase A, des événements
   génériques d'une même commune ont pu partager un même lieu par
   coordonnées. Le rattrapage les marque `APPROXIMATE` sans les séparer.
   Ensuite, `mayReuse` en créera un par nom au réimport, et l'ancien lieu
   restera orphelin (aucune suppression prévue).
5. **Alertes (A8)** : inclure les activités approximatives avec une mention
   reste la proposition. À confirmer.
6. **Application mobile (A9)** : calendrier de mise à jour. Les versions
   publiées afficheront un point exact jusqu'à leur mise à jour.
7. **Géocodage des contributions (A3/A4)** : hors de cette phase. Les
   contributions restent `UNKNOWN`.

#### 7.1.7 Décisions d'Alex (14/10/2026)

Recommandations du § 7.1.6 validées :

1. Liste des formulations génériques conservée telle quelle ; élargie
   seulement après relecture de la mesure en bêta
   (`phase_a_precision_mesure.sql`).
2. Activité à plusieurs lieux dont le point affiché n'est pas celui du lieu
   approximatif : reste `UNKNOWN`.
3. Lieux partagés existants : pas de séparation forcée ; l'ancien lieu peut
   rester orphelin.
4. Alertes : envoyées pour une activité approximative, avec la mention
   « emplacement approximatif ».
5. Application mobile : mise à jour plus tard ; le champ ajouté à l'API est
   ignoré par les versions publiées.
6. Géocodage des contributions : hors de cette phase.

Implémentation autorisée (migration, import, API, affichage web). La
migration n'est déployée qu'après validation finale de la PR.

#### 7.1.8 Implémentation (14/10/2026)

| Élément | Où |
|---|---|
| Migration (colonnes, `CHECK`, rattrapage ciblé, copie sur l'activité) | `V61__add_coordinate_precision.sql` ; bloc de classification identique au script de mesure (test de concordance) |
| Précision d'un lieu | `Location.coordinatePrecision` (défaut `UNKNOWN`) |
| Précision d'une activité | colonne hors du modèle `Activity`, écrite par `ActivityCoordinatePrecisionRepository.update` après le rattachement du lieu : les nombreuses copies d'`Activity` ne peuvent pas la remettre à `UNKNOWN` |
| Import | `NormalizationService.normalizeLocation` (`inferFromText`), `LocationService.findOrCreate` (`mayReuse`), `ImportService.enrichActivity` (copie sur l'activité) |
| API | `coordinatePrecision` dans `ActivityResponse`, `ActivitySummaryResponse`, `ActivityDetailResponse` (et `LocationSummary`), `ActivitySearchResponse.Item` (lieu effectif) |
| Alertes | `AlertActivity.approximatePosition`, mention dans l'e-mail |
| Web | `ExplorerMap` (marqueur atténué, info-bulle), `ActivityList`, `formatActivityLocation` (fiche, aperçu), `buildDirectionsUrl` |

Tests : migration appliquée sur une base en V60 avec données (lieux génériques →
`APPROXIMATE`, adresse précise → `UNKNOWN`, activité au point du lieu →
`APPROXIMATE`, activité au point différent → `UNKNOWN`) ; persistance et
contrainte sur PostgreSQL ; précision conservée après une copie d'`Activity` ;
réutilisation des lieux ; import ; API ; alertes ; frontend.

Non fait, conformément aux décisions : application mobile, géocodage des
contributions, élargissement de la liste des formulations génériques.

### 7.2 Phase B — lieux permanents et provenance

**Statut : proposition pour décision (13/10/2026). Rien n'est implémenté.**

#### 7.2.1 Décisions d'Alex (13/10/2026)

- **Liste blanche explicite** : aucun type de lieu n'est publié
  implicitement.
- **Modération avant publication** de tout nouveau lieu importé.
- Avant implémentation : proposer le mécanisme qui distingue lieux en
  attente et lieux publiés, puis présenter la liste blanche et ses impacts.

#### 7.2.2 Mécanisme proposé : statut de lieu permanent, distinct du lieu d'événement

Une colonne `location.place_status` (et non `status`, pour ne pas la
confondre avec le statut d'une activité), toujours associée à `place_type` :

| `place_type` | `place_status` | Sens | Visible comme lieu (couche « Lieux », API `/places`) |
|---|---|---|---|
| nul | nul | lieu d'événement seulement (tous les lieux existants) | non — comme aujourd'hui |
| renseigné | `PENDING` | lieu importé, en attente de modération | non |
| renseigné | `PUBLISHED` | validé par un administrateur | **oui** |
| renseigné | `REJECTED` | refusé ; mémorisé pour ne pas être reproposé | non |
| renseigné | `ARCHIVED` | disparu de la source ou retiré de la liste blanche | non |

Règles :

1. **Import** : un lieu dont aucun type précis n'est dans la liste blanche
   n'est **pas importé du tout**, pas même en attente. Un lieu retenu est
   créé en `PENDING`, jamais en `PUBLISHED`.
2. **Indépendance des événements** : `place_status` ne masque jamais un
   événement. Un événement rattaché à un lieu `PENDING` ou `REJECTED` reste
   affiché comme aujourd'hui (le lieu d'un événement et sa fiche de lieu
   permanent sont deux usages distincts de la même ligne).
3. **Promotion** (§ 2.3, étape 3) : un lieu d'événement reconnu comme lieu
   permanent reçoit `place_type` et passe en `PENDING`, pas en
   `PUBLISHED`.
4. **Refus durable** : la provenance (`location_source`, clé `(source_id,
   external_id)`) garde le lien ; un réimport d'un lieu `REJECTED` met à
   jour sa provenance mais ne le repropose pas.
5. **Mise à jour d'un lieu publié** : nom, description, contacts mis à jour
   sans nouvelle modération ; **retour en `PENDING`** si sa position bouge
   de plus de 100 m, si son adresse disparaît, ou si ses types sortent de
   la liste blanche (au lieu de le dépublier sans regard humain).
6. **Disparition de la source** : `ARCHIVED`, avec le même garde-fou que
   les activités (au moins 10 lieux et plus de 50 % disparus d'un coup :
   archivage suspendu) et l'essai à blanc avant activation.
7. **Signalements à la modération** (calculés, non bloquants) : sans
   adresse, nom d'itinéraire (« Circuit… », « Parcours… »), doublon de nom
   dans la commune, précision `APPROXIMATE` (phase A), candidat de fusion
   ambigu (§ 2.3).
8. **Actions de modération** : publier ou refuser un lieu ; **actions
   groupées** sur une sélection filtrée (type, commune, sans signalement),
   indispensables au volume (§ 7.2.3). Chaque action est journalisée
   (administrateur, date).
9. **Publication automatique** par règle « source + type » : **non
   proposée maintenant** ; envisageable plus tard, sur la base du taux de
   refus mesuré par type.

```sql
-- PROJET, non créé : remplace le projet précédent (colonne status).
ALTER TABLE location
    ADD COLUMN place_type VARCHAR(50),
    ADD COLUMN place_status VARCHAR(20),
    ADD COLUMN description TEXT,
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN moderated_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN moderated_at TIMESTAMPTZ;
ALTER TABLE location ADD CONSTRAINT chk_location_permanent_place CHECK (
    (place_type IS NULL AND place_status IS NULL)
    OR (place_type IS NOT NULL AND place_status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED')));
CREATE INDEX idx_location_published_place ON location (place_type) WHERE place_status = 'PUBLISHED';
CREATE INDEX idx_location_pending_place ON location (id) WHERE place_status = 'PENDING';

CREATE TABLE location_source (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL REFERENCES source (id) ON DELETE CASCADE,
    external_id VARCHAR(255) NOT NULL,
    source_types TEXT NOT NULL,          -- types DATAtourisme reçus, pour rejouer la liste blanche
    content_hash VARCHAR(64),
    last_seen_at TIMESTAMPTZ,
    UNIQUE (source_id, external_id)
);
```

Données existantes : aucune ligne ne reçoit `place_type` ni
`place_status` ; aucun changement visible.

#### 7.2.3 Liste blanche proposée, mesurée sur l'export DATAtourisme PACA du 13/10/2026

Mesure reproductible : `collectors/verification/place_types_report.py`
(lecture seule, sans réseau) sur `datatourisme-reg-pac.csv`. Vaucluse :
**3 483 lieux**, dominés par la restauration (799 sans sous-type) et la
dégustation (653), puis les commerces et l'hébergement.

**Règle** : un lieu est retenu s'il a au moins un **type précis** de la
liste (type qui n'est parent d'aucun autre de ses types), et aucun type
`Accommodation`, `FoodEstablishment` ou `TastingProvider`. Un type parent
de la liste (`CulturalSite`) ne retient que les lieux dont il est le type
précis : aucun sous-type n'entre implicitement. La hiérarchie est déduite
du fichier (l'ontologie n'est pas joignable), ce qui donne des parentés de
fait parfois surprenantes (toutes les galeries portent aussi `Store`) :
c'est pourquoi l'exclusion ne porte pas sur `Store`.

**Groupe 1 — proposé (46 types, 418 lieux dans le Vaucluse)**

| Famille | Types (lieux dans le Vaucluse) |
|---|---|
| Culture | `CulturalSite` sans sous-type (53 : Théâtre antique d'Orange…), `Library` (48), `Museum` (44), `Theater` (35), `Cinema` (14), `InterpretationCentre` (1) |
| Patrimoine | `ParkAndGarden` (30), `Castle` (22), `Ruins` (18), `Mill` (5), `Tower` (4), `Dungeon` (4), `WashHouse` (3), `Bridge` (3), `RemembranceSite` (3), `Palace` (2), `Aqueduct` (2), `ArcheologicalSite`, `RomanPath`, `Fort`, `DefenceSite`, `TechnicalHeritage`, `IndustrialSite`, `Mine`, `CoveredMarket` (1 chacun) |
| Patrimoine religieux | `Church` (45), `Chapel` (9), `Abbey` (5), `Collegiate` (4), `Basilica`, `Cathedral`, `Convent`, `Commanderie`, `Synagogue`, `Temple` (2 chacun), `Monastery` (1) |
| Équipements sportifs et de plein air | `TennisComplex` (16), `SwimmingPool` (13), `BoulesPitch` (13), `TrackRollerOrSkateBoard` (9), `Stadium` (8), `Beach` (8), `FitnessPath` (5), `PlayArea` (3), `NaturalHeritage`, `PointOfView` (1 chacun) |

Qualité des 418 : 28 sans adresse, 8 noms d'itinéraires (« Circuit piéton
"à la découverte de Valréas" », classé à la fois église, chapelle, tour et
couvent), 14 doublons de nom dans la même commune. Autres départements :
04 : 322 ; 05 : 344 ; 06 : 678 ; 13 : 1 907 ; 83 : 1 072 (**4 741 en
PACA**).

**Groupe 2 — à trancher (17 types, 215 lieux)** : lieux de loisirs
souvent commerciaux ou services. `ArtGalleryOrExhibitionGallery` (73,
galeries marchandes et salles d'exposition mêlées), `EquestrianCenter`
(41), `LocalTouristOffice` (37), `ClimbingWall` (21), `ThemePark` (10),
`ZooAnimalPark` (6), `TouristTrain`, `GolfCourse`, `BowlingAlley`,
`TeachingFarm` (4 chacun), `AdventurePark`, `MiniGolf` (3), `SightseeingBoat`
(2), `ViaFerrata`, `RacingCircuit`, `Racetrack`, `LeisureComplex` (1).

**Exclus explicitement** : restauration, dégustation, hébergement,
commerces, prestataires d'activités, services, santé, transport ; et
**`CityHeritage` / `District`** (59 lieux : un village ou un quartier
entier, 28 sans adresse ; ce sont des zones, pas des lieux, au sens de la
phase A).

#### 7.2.4 Impacts

**Impacts sur l'architecture et les fonctionnalités existantes :**

| Zone | Impact | Compromis |
|---|---|---|
| Module `place` | passe d'un simple modèle à un module complet : API publique (`PlaceController`), service de lieux, modération | un module de plus à maintenir, mais pas de doublon d'entité |
| Recherches d'activités, alertes, affichage des événements | inchangés : ils ignorent `place_type`/`place_status` (règle 2) | — |
| Lieux partagés existants | inchangés ; un lieu d'événement peut être promu en lieu permanent, sans changer d'`id` | une promotion modifie la fiche d'un lieu déjà utilisé : règle « ne jamais déplacer un lieu qui porte des programmations » (§ 2.3) |
| Suppression d'activités (administration) | inchangée : les lieux restent (déjà le cas) | les lieux permanents ne sont jamais supprimés avec une activité |
| Suppression d'une source | provenance supprimée en cascade, lieux conservés | un lieu peut rester sans provenance : à archiver par une règle explicite |
| Import | nouveau `PlaceImportService` séparé de l'import d'événements ; mêmes garde-fous (fichier suspect, disparition massive, essai à blanc) | duplication partielle de l'orchestration, à factoriser si un troisième import apparaît |
| Carte web et mobile | nouvelle couche « Lieux », activable ; l'application mobile ne la verra qu'après mise à jour | volume : 418 lieux publiables dans le Vaucluse avec le groupe 1 (3 483 avant liste blanche) |
| Administration | écran de modération des lieux `PENDING` (tous les nouveaux lieux importés), avec signalements et actions groupées | charge de relecture : voir la ligne suivante |
| Modération | file d'attente initiale de **418 lieux** (Vaucluse) ou **4 741** (PACA) ; ensuite, quelques lieux par import. Actions groupées indispensables | sans actions groupées, la file initiale n'est pas traitable |
| Ordre de mise en service | Vaucluse seul d'abord (filtre de département de la source, comme les événements), puis extension | — |
| Phase A | les lieux sans adresse et les zones sont signalés ; `CityHeritage`/`District` exclus | dépend de la migration de la phase A pour le signalement `APPROXIMATE` |

#### 7.2.5 Décisions demandées

1. Valider le **groupe 1** tel quel, ou le réduire.
2. Trancher le **groupe 2**, type par type ou en bloc (proposition : ne
   pas l'inclure au départ ; galeries et offices de tourisme à revoir après
   un premier cycle de modération).
3. Valider la règle 5 (retour en attente sur changement important) et la
   règle 4 (refus durable).
4. Périmètre de départ : **Vaucluse seul** (proposition).
5. Qui modère, et à quel rythme : la file initiale est de 418 lieux.

#### 7.2.6 Décisions d'Alex (14/10/2026)

1. **Groupe 1 validé** (46 types).
2. **Groupe 2 exclu au départ** ; à revoir après un premier cycle de
   modération.
3. Règles 4 (refus durable) et 5 (retour en attente sur changement
   important) **conservées**.
4. Périmètre de départ : **toute la région PACA** (4 741 lieux à modérer au
   départ) : les actions groupées et les filtres de la file de modération
   (type, département, signalements) sont donc indispensables dès la
   première version.
5. Modération : **les administrateurs** (rôle `ADMIN`).

#### 7.2.7 Implémentation du backend (14/10/2026)

| Élément | Où |
|---|---|
| Migration : colonnes `place_type`, `place_status`, `description`, `published_at`, `moderated_by`, `moderated_at` ; contrainte `CHECK` ; table `location_source` ; source `DATATOURISME_LIEUX` créée **inactive** | `V62__add_permanent_places.sql` |
| Liste blanche (46 types, règle des types précis, exclusions) | `PlaceTypeWhitelist` |
| Règles de modération (refus durable, retour en attente, réapparition, disparition, garde-fou 10 / 50 %) | `PlaceModerationPolicy` |
| Collecte (même fichier et mêmes contrôles que les événements ; types schema.org ignorés) | `DataTourismePlaceCollector` |
| Import (création `PENDING`, mises à jour, archivage, essai à blanc, une transaction) ; import quotidien des sources actives à 4 h 30 | `PlaceImportService` |
| Modération (file filtrée par statut, type, département, signalements ; publier, refuser, actions groupées jusqu'à 500) ; essai à blanc, import, activation vérifiée d'une source | `AdminPlaceController` (`/api/v1/admin/places`, rôle `ADMIN`) |
| Lecture publique des lieux publiés dans une zone | `PlaceController` (`/api/v1/places/within-bounds`) |

Les colonnes des lieux permanents ne sont pas des champs de `Location` : l'import
d'événements ne peut ni les lire ni les modifier. Un lieu permanent peut être
réutilisé comme lieu d'un événement de mêmes coordonnées (règle existante de
`findOrCreate`) ; son statut ne masque jamais l'événement.

Mesure sur le fichier réel (collecteur Java + liste blanche) : 32 332 lieux
lus en PACA, **4 771 retenus** (Vaucluse : **418**), les 2 écarts avec la
mesure Python étant des URI en double.

Tests : règles (10), collecteur sur extrait réel (5), import et modération sur
PostgreSQL (6 : création, réimport, refus durable, retour en attente,
disparition massive et normale, réapparition, échec, essai à blanc,
signalements, lecture publique, activation vérifiée), API (4 : accès, validations,
409, 404), source de lieux (1), migration V62 sur une base V61 avec données.

Non fait (B2) : écran de modération et couche « Lieux » de la carte. Non fait,
reporté : rapprochement avec les lieux d'événement existants (§ 2.3, étapes 2 et
3) ; un même lieu peut donc exister en lieu d'événement et en lieu permanent.

#### 7.2.8 Interface (14/10/2026)

| Élément | Où |
|---|---|
| Onglet « Lieux » de l'administration : source des lieux (essai à blanc, activation vérifiée, import, bilan) ; file de modération par statut, type, département et signalements ; publication et refus unitaires ou de la page sélectionnée, après confirmation ; pagination de 50 | `AdminPlacesSection` |
| Type `DATATOURISME_LIEUX` dans le formulaire des agendas | `AdminPage` |
| Couche « Lieux » de la carte : bouton (choix mémorisé dans le navigateur), lieux publiés de la zone affichée chargés dès l'activation puis à chaque déplacement, marqueur distinct et fiche courte (type, adresse, mention d'emplacement approximatif, site) | `PlacesLayer`, `ExplorerMap`, `App` |
| Libellés français des 46 types | `placeTypes.ts` |

Vérifié dans un navigateur réel (Chromium, API simulée) : onglet, bilan d'essai,
signalements ; couche, marqueurs et fiche.

Application mobile (14/10/2026, après la PR du rapprochement) : champ
`coordinatePrecision` lu (facultatif, un backend antérieur reste compatible) ;
mention « emplacement approximatif » sur la fiche et l'aperçu, marqueur atténué ;
bouton « Itinéraire » (coordonnées, ou adresse ou commune si la position est
approximative, même règle que le site) ; couche « Lieux » activable (losange
violet, aperçu : type, adresse, itinéraire, site). Choix de la couche mémorisé sur
l'appareil (clé `locallife.showPlaces`, comme le site) avec
`@react-native-async-storage/async-storage` 2.2.0, version associée au SDK Expo 57
(décision d'Alex, 14/10/2026) ; un stockage indisponible laisse la couche masquée. Vérifié par typecheck,
lint et tests unitaires (helpers, appel API) ; **non vérifié sur appareil ni
émulateur** (pas d'émulateur dans l'environnement de développement).

#### 7.2.9 Rapprochement lieux d'événement ↔ lieux permanents : mesure (14/10/2026)

**Statut** : règle et rapport **implémentés et testés** ; mesure faite **en local** sur
des données réelles ; **aucune liaison écrite**, aucun lieu modifié ni fusionné ; rien
de déployé. Le mécanisme de liaison est à décider (ci-dessous).

**Règle** (`PlaceMatcher`, fonction pure) — jamais de rapprochement si une position est
`APPROXIMATE`, si les codes postaux diffèrent ou au-delà de 100 m. Sinon :

| Niveau | Condition |
|---|---|
| `CERTAIN` | ≤ 30 m et même nom normalisé, ou même adresse et nom proche ; ou lieu d'événement **sans nom** dont l'adresse contient le nom du lieu permanent (≥ 2 mots significatifs), ≤ 30 m |
| `PROBABLE` | nom proche (≥ 80 % des mots significatifs du plus court dans l'autre), ou nom contenu dans l'adresse, de 30 à 100 m |
| `AMBIGUOUS` | même adresse mais noms différents ; ou plusieurs lieux permanents `CERTAIN`/`PROBABLE` pour un même lieu d'événement (« plusieurs candidats ») |

Le cas « nom dans l'adresse » vient de la mesure : **64 des 67** lieux d'événement
DATAtourisme du Vaucluse n'ont pas de nom, celui-ci est noyé dans l'adresse
(« 31 rue Giraud Cinéma Le Luberon ») ; sans ce cas, la règle ne trouvait qu'une paire.
Un lieu permanent nommé comme une voie (« Place Saint-Pierre », « Le cours Mirabeau »)
en est exclu : les adresses le citent sans le désigner (2 faux positifs observés puis
corrigés).

**Rapport** : `GET /api/v1/admin/places/matches?department=84&level=CERTAIN&limit=200`
(administrateurs, lecture seule) : paires classées, distance, nombre d'activités
concernées, totaux par niveau. Paires candidates calculées en PostGIS (`ST_DWithin`
100 m) entre les lieux utilisés par au moins une programmation et les lieux permanents
`PENDING` ou `PUBLISHED`.

**Procédure de mesure** (reproductible, base locale) : sources pointées sur l'export
DATAtourisme PACA de data.gouv.fr du 13/10/2026 servi localement ; import des
événements (filtre 84, puis 13) ; essai à blanc puis import des lieux (4 771 lieux
`PENDING`) ; appel du rapport ; relecture manuelle.

**Résultats** (événements DATAtourisme seulement ; ni OpenAgenda ni la base bêta) :

| | Vaucluse (84) | Bouches-du-Rhône (13) |
|---|---|---|
| Lieux d'événement utilisés / activités | 67 / 102 | 643 / 1 664 |
| `CERTAIN` : lieux d'événement (activités) | 8 (13) | 130 (580) |
| `PROBABLE` | 3 (4) | 18 (26) |
| `AMBIGUOUS` | 2 (2) | 8 (34) |
| Lieux permanents distincts reliés (`CERTAIN`) | 4 | 121 |

**Relecture** (faite par l'assistant, à confirmer par un administrateur) : 84 —
11/11 paires `CERTAIN` ou `PROBABLE` justes ; 13 — échantillon aléatoire de 40
`CERTAIN` sur 130 : 40 justes (dont une discutable : « Presqu'île du cirque romain »
→ « Le cirque romain ») ; `PROBABLE` : 17/18 justes, 1 douteuse (centre culturel
arménien → cathédrale arménienne, 67 m). Les `AMBIGUOUS` sont de vrais cas à trancher :
musée dans un bâtiment lui-même lieu permanent (Hôtel de Porcelet / Musée Yves Brayer),
**doublons dans DATAtourisme** (« Cinéma Le Luberon » ×2, « Musée des Enfants » ×2).

Constats annexes : un même lieu réel a souvent plusieurs lignes de lieu d'événement
(Palais des Papes ×5, Château de Simiane ×2) ; la liaison doit donc être plusieurs-à-un.

**Mécanisme de liaison — options (décision demandée, aucune écrite)** :

| Option | Effet | Réversible | Risque |
|---|---|---|---|
| A. Promotion (§ 2.3, étape 3) | le lieu d'événement devient le lieu permanent | non : il faut fusionner deux lignes, le lieu importé existe déjà | fusion destructive |
| B. Réaffecter les programmations au lieu permanent | `schedule`/`occurrence.location_id` changent | seulement avec un journal | l'adresse et la position affichées de l'événement changent (jusqu'à 100 m) |
| **C. Lien réversible (recommandé)** | colonne `location.place_id` (lieu d'événement → lieu permanent), nulle par défaut | oui : remettre à nul | aucun sur l'affichage des événements ; la fiche du lieu liste les activités des lieux reliés |

Recommandation : **C**, migration `V63` (colonne nullable, clé étrangère, index ;
à valider explicitement avant création), et **liaison par un administrateur** :
`CERTAIN` proposés en lot, `PROBABLE` et `AMBIGUOUS` un par un ; aucune liaison
automatique tant que le taux d'erreur n'est pas mesuré par l'usage. Les doublons de
lieux permanents se traitent par refus en modération.

#### 7.2.10 Liaison : décision et implémentation (14/10/2026)

**Décision d'Alex (14/10/2026)** : option C (lien réversible), liaison par un administrateur.

| Élément | Où |
|---|---|
| Migration `V63` : `location.place_id` (clé étrangère vers `location`, `ON DELETE SET NULL`), `place_linked_by`, `place_linked_at` ; contrainte `chk_location_place_link` (seul un lieu d'événement se relie, jamais à lui-même) ; index partiel. Aucune donnée existante modifiée. | `V63__link_event_locations_to_places.sql` |
| Liaison groupée (≤ 500) : une paire n'est reliée que si elle est encore candidate (lieu d'événement utilisé, lieu permanent `PENDING`/`PUBLISHED` à ≤ 100 m) et que la règle ne la classe pas `NONE` ; sinon refusée avec sa raison. Seul `place_id` change. | `PlaceLinkService`, `POST /api/v1/admin/places/links` |
| Délier : `place_id` remis à vide | `DELETE /api/v1/admin/places/links/{eventLocationId}` |
| Rapport : lien existant (`linkedPlaceId`) | `GET /api/v1/admin/places/matches` |
| Onglet « Rapprochements » : paires par niveau et département, sélection, « Relier la sélection » et « Délier » après confirmation | `AdminPlaceMatchesSection` |

Testé : contrainte, liaison (paires refusées : inutilisée, lieu refusé, doublon, lieu permanent),
lieu d'événement inchangé après liaison, délier, accès réservé aux administrateurs ; interface (vitest).
**Non déployé** : la migration V63 s'applique au prochain déploiement du backend.

#### 7.2.11 Fiche des lieux (14/10/2026)

| Élément | Où |
|---|---|
| Fiche d'un lieu publié (404 sinon) | `GET /api/v1/places/{id}` |
| Activités à venir d'un lieu publié : celles du lieu et des lieux d'événement reliés (`place_id`), lieu effectif = programmation ou occurrence non annulée ; publiées et non terminées ; au plus 50, de la plus proche à la plus lointaine ; même format que la carte (404 si le lieu n'est pas publié) | `GET /api/v1/activities/at-place/{placeId}`, `ActivityAtPlaceRepository` |
| Site : la fiche courte du lieu (carte) charge ses activités à venir à l'ouverture ; 5 affichées, la suite résumée ; un clic ferme la fiche et ouvre l'aperçu de l'activité | `PlaceActivities`, `PlacesLayer` |
| Mobile : l'aperçu du lieu liste 3 activités à venir, un appui ouvre la fiche de l'activité | `usePlaceActivities`, `PreviewCard` |

Testé : backend (activités du lieu et d'un lieu relié, exclusion des activités passées, en attente
ou d'un lieu non relié, ordre ; 404 pour un lieu non publié), site (vitest, et rendu vérifié dans
Chromium avec une API simulée), mobile (typecheck, lint, appel API). **Non vérifié sur appareil.**
Aucune migration. Un lieu sans lien ni programmation affiche « Aucune activité à venir connue ici. »

#### 7.2.12 Liste « Lieux » (14/10/2026)

| Élément | Où |
|---|---|
| Site : quand la couche « Lieux » est affichée (hors recherche textuelle), le panneau de liste propose les onglets « Activités » et « Lieux (N) » ; les lieux sont ceux de la couche pour la zone affichée, filtrables par type, groupés par commune et triés par nom ; un lieu se déplie (adresse, description, site, activités à venir) | `PlaceList`, `App`, `PlacesLayer` (`onPlacesChange`) |
| Mobile : bouton « Liste (N) » quand la couche est affichée ; liste des lieux visibles par commune puis par nom ; un appui ouvre l'aperçu du lieu | `PlaceListPanel`, `placeList.ts`, `MapScreen` |

Aucun appel réseau supplémentaire : la liste réutilise les lieux déjà chargés par la couche.
Testé : site (vitest, rendu vérifié dans Chromium avec une API simulée), mobile (typecheck, lint,
tri). **Non vérifié sur appareil.** Non fait : `GET /api/v1/places/nearby` (§ 2.4), sans usage
tant que la liste suit la zone de la carte.

#### 7.2.13 Doublons de lieux d'événement : mesure (15/10/2026)

**Statut** : règle et rapport **implémentés et testés** ; mesure faite **en local** ; **aucune
écriture**, aucun lieu fusionné ; rien de déployé. Correction à décider (ci-dessous).

**Cause** : `LocationService#findOrCreate` ne réutilise un lieu d'événement que pour une adresse
**identique au caractère près** (même commune et code postal) : « 8 Place Aristide Briand » et
« 8 place Aristide Briand », ou les mêmes mots dans un autre ordre, créent deux lignes.

**Règle** (`EventLocationDuplicateMatcher`, fonction pure) : on compare les mots significatifs du nom
et de l'adresse (sans accents, casse, ponctuation ni mots vides, ordre ignoré, numéros gardés).
Jamais de doublon si une position est approximative, si les codes postaux diffèrent, au-delà de
100 m, ou avec moins de 2 mots.

| Niveau | Condition | Présentation |
|---|---|---|
| `CERTAIN` | mêmes mots, à 30 m au plus | regroupés en grappes (un lieu réel, plusieurs lignes) |
| `PROBABLE` | mêmes mots plus loin, ou tous les mots de l'un dans l'autre | paire par paire, **jamais par ricochet** : « Château de Simiane » relierait sinon le n° 5 et le n° 8 de la même place |

**Rapport** : `GET /api/v1/admin/places/event-location-duplicates?department=84&level=CERTAIN`
(administrateurs, lecture seule) : grappes et paires, lieux et activités concernés, lignes en trop.

**Mesure** (base locale, événements DATAtourisme de l'export PACA du 13/10/2026 ; ni OpenAgenda ni
la bêta) :

| | Vaucluse (84) | Bouches-du-Rhône (13) |
|---|---|---|
| Lieux d'événement utilisés | 64 | 646 |
| `CERTAIN` : grappes / lieux / lignes en trop / activités | 5 / 10 / 5 / 29 | 15 / 31 / 16 / 135 |
| `PROBABLE` : paires / activités | 9 / 24 | 51 / 276 |

**Relecture** (faite par l'assistant, à confirmer) : `CERTAIN` **20/20 justes** (variantes de casse,
de ponctuation ou d'ordre : « 90 Boulevard / boulevard Victor Hugo Théâtre de Pertuis », « Badaboum
Théâtre / Théâtre Badaboum ») ; `PROBABLE`, 84 : 8 à 9 sur 9 ; 13, échantillon aléatoire de 25 sur 51 :
16 vrais doublons, 6 **salles d'un même bâtiment** (« Espace culturel Saint-Exupéry » et son
« Auditorium Beethoven », « Opéra de Marseille » et son « Foyer »), 3 douteux ou faux (une place et
le musée qui s'y trouve). Les probables demandent donc une décision humaine.

**Options — décision demandée, rien n'est écrit** :

| Option | Effet | Risque |
|---|---|---|
| **A. Prévenir (recommandé)** : `findOrCreate` réutilise aussi un lieu que la règle classe `CERTAIN` | plus de nouveaux doublons de ce type aux imports ; aucune migration, aucune donnée existante modifiée | nul sur l'échantillon (20/20) ; le lieu réutilisé garde son texte d'origine |
| B. Résorber l'existant par un lien réversible : lieu d'événement → lieu d'événement de référence (même principe que V63) | grappes affichées comme un seul lieu, défaisable | migration ; à valider |
| C. Résorber l'existant en réaffectant les programmations à la ligne la plus ancienne d'une grappe `CERTAIN`, avec journal | plus de lignes en trop | l'adresse affichée change de casse ou d'ordre des mots ; écriture à valider |

Les `PROBABLE` ne seraient jamais traités automatiquement. Pour le rapprochement avec les lieux
permanents (§ 7.2.10), les doublons sont sans gravité : chaque ligne se relie au même lieu.

### 7.3 Phase C — marchés et horaires : ontologie et N-Triples (13/10/2026)

**Statut : étude terminée dans les limites fixées (aucun compte, aucun
traitement complet). Aucune intégration.**

#### 7.3.1 Méthode

L'ontologie publiée (framagit, GitLab ADN Tourisme, datatourisme.fr) n'est
**pas joignable** depuis l'environnement de développement (politique
réseau). Elle a donc été **reconstituée à partir des données** : la sonde
`collectors/verification/ntriples_ontology_probe.py` lit un préfixe de
l'archive N-Triples du 13/10/2026 (320 Mo compressés sur 1 930, **17 %**,
12,5 millions de triplets, 35 min), sans rien écrire, et mesure les
prédicats, les types et les liens réellement employés. Le fichier ne
contient aucun triplet d'ontologie (`rdfs:domain`, `subClassOf`…).

#### 7.3.2 Concepts, propriétés et relations identifiés

```
core:Market (événement)
 ├─ core:takesPlaceAt ──▶ core:Period            (5 181 nœuds lus)
 │                         ├─ core:startDate, core:endDate
 │                         ├─ core:startTime, core:endTime   (2 123 périodes avec heures)
 │                         └─ sous-type core:RecurrentPeriod (41)
 │                              └─ core:appliesOnDay          (jour de la semaine)
 └─ core:isLocatedAt ───▶ schema:Place
                           └─ schema:openingHoursSpecification ──▶ schema:OpeningHoursSpecification
                                                                     ├─ schema:dayOfWeek
                                                                     ├─ schema:opens, schema:closes
                                                                     └─ schema:validFrom, schema:validThrough
```

La propriété qui manquait à l'étude du § 2.8 est
**`schema:openingHoursSpecification`, portée par le lieu
(`schema:Place`, cible de `isLocatedAt`)**, et non par la période : 396
liens observés, tous de cette forme. Pour un événement comme un marché,
les jours et heures sont portés par les **périodes**.

#### 7.3.3 Les jours de marché sont-ils dans le N-Triples ?

**Oui, en partie.** Dans la part lue (2 625 marchés, France entière), 107
ont des périodes présentes dans le préfixe :

| Ce que portent les périodes du marché | Marchés (sur 107) | Jours exploitables |
|---|---|---|
| dates d'un jour seulement, toutes le même jour de semaine (ex. « Marché à Latronquière » : 52 vendredis) | 53 | **oui**, déduits des dates |
| dates d'un jour seulement, deux jours de semaine | 5 | oui |
| périodes longues seulement | 49 | non, sauf `appliesOnDay` |
| `core:appliesOnDay` sur une période récurrente | 37 (recoupement avec les lignes précédentes non mesuré) | **oui**, explicite |
| `core:startTime` / `endTime` | 73 | heures de marché |

Chemin par les horaires du lieu : 215 lieux de marché lus, **aucun**
avec des horaires (`openingHoursSpecification`). Pour les marchés, ce
chemin n'est pas celui qui porte les jours.

**Pourquoi le CSV ne les a pas** : la colonne `Periodes_regroupees` du
CSV regroupe les périodes. En PACA, 485 des 531 marchés n'y ont qu'une
période couvrant l'année (d'où les 88 marchés du Vaucluse écartés par la
règle des 31 jours), alors que le N-Triples porte, pour des marchés
comparables, une date par séance ou une période récurrente avec son jour.

#### 7.3.4 Informations manquantes

1. **Valeurs de `appliesOnDay`** : propriété observée, valeurs non relevées
   (probablement des individus `schema:Monday`…) ; à vérifier.
2. **Proportion réelle** : 107 marchés résolus sur 2 625 lus (périodes
   dispersées dans le fichier), France entière ; **la part du Vaucluse
   n'est pas mesurée**.
3. **Ontologie officielle** non lue : définitions, cardinalités, sens exact
   de `RecurrentPeriod` (exceptions, fermetures) non confirmés.
4. Fraîcheur : les dates d'un jour couvrent l'année en cours ; leur
   prolongation l'année suivante n'est pas observée.

#### 7.3.5 Intérêt d'un compte diffuseur DATAtourisme

Un compte gratuit sur la plateforme diffuseur permet de créer un **flux
filtré** (type `Market`, département 84) au format JSON, où chaque marché
est **complet** (périodes, `appliesOnDay`, heures, lieu) dans un petit
fichier : il répondrait aux points 1 et 2 pour le Vaucluse sans traiter
1,9 Go, et deviendrait la source naturelle d'un futur import. Inconvénients :
un compte et une clé de plus à gérer, conditions d'utilisation de la
plateforme à relire. Alternative sans compte : traitement complet ponctuel
du N-Triples sur un serveur au débit normal (deux passes, quelques
heures), non autorisé à ce stade.

#### 7.3.6 Décision d'Alex (14/10/2026)

Créer un compte diffuseur DATAtourisme au nom du projet, avec un flux
« Marchés, Vaucluse » en JSON, aux conditions du § 7.3.5 : conditions
d'utilisation relues, clé dans les paramètres de l'environnement, mesure
des jours de marché **avant** toute décision d'import.

#### 7.3.7 Conséquence pour la conception (non implémentée)

La table du projet (`location_opening_hours`) reste adaptée ; ses lignes
viendraient des périodes (jour de la semaine déduit ou `appliesOnDay`,
heures `startTime`/`endTime`), avec `valid_from`/`valid_until`. Un marché
sans jour exploitable reste « Jours de marché à confirmer ».

```sql
-- PROJET, non créé.
CREATE TABLE location_opening_hours (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    opens TIME,
    closes TIME,
    valid_from DATE,
    valid_until DATE,
    source_id BIGINT REFERENCES source (id) ON DELETE SET NULL
);
```

### 7.4 Phase D — liens de doublons : présentation pour décision

**Statut : étape 1 (mesure sans écriture, autorisée le 13/10/2026) outillée
et étalonnée sur données publiques ; mesure sur les données de
l'application à faire ; étape 2 non autorisée.** Procédure, critères et
résultats : `docs/02_Architecture/VERIFICATION_DOUBLONS.md`. La règle n'est
toujours pas branchée dans l'application.

État réel :

- implémenté et testé : `CrossSourceDuplicateMatcher` (fonction pure,
  9 tests, dont les faux positifs mesurés) ;
- non implémenté : calcul des paires candidates sur les données en base,
  enregistrement des liens, masquage, écran de relecture.

**Conception du branchement**, en deux étapes :

1. **Mesure, sans écriture** : un service d'application calcule les paires
   candidates entre activités publiées de sources différentes. Il ne
   compare que des activités qui ont un jour d'occurrence commun et le même
   département, pour éviter une comparaison de toutes les paires. Il
   produit un rapport (paires probables et possibles, avec titres, lieux et
   distance) consultable par un administrateur, et des tests d'intégration
   PostGIS sur des cas réels. Aucune table nouvelle n'est nécessaire.
2. **Liens**, après revue du rapport sur les données de production :
   table `activity_duplicate_link` (SQL ci-dessous), masquage des seuls
   liens `PROBABLE` non rejetés, écran de relecture des liens `POSSIBLE`,
   annulation possible.

| Choix | Proposition | Compromis |
|---|---|---|
| Quand calculer | après chaque import, pour les activités créées ou modifiées | un calcul complet périodique serait plus simple mais plus coûteux |
| Activité conservée | celle de la source prioritaire (OpenAgenda) | une fiche DATAtourisme plus riche peut être masquée ; la relecture permet d'inverser |
| Masquage | exclusion des recherches publiques, fiche toujours accessible par son lien | les requêtes de recherche gagnent une condition (`NOT EXISTS` sur les liens) |
| Faux positifs | jamais de masquage pour un titre générique, une position approximative ou une distance supérieure à 300 m | des doublons réels restent visibles en attendant leur relecture |

**Décisions demandées** : autoriser l'étape 1 (mesure sans écriture,
aucune migration) ; accès aux données de production pour la revue.

```sql
CREATE TABLE activity_duplicate_link (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activity (id) ON DELETE CASCADE,
    duplicate_of_id BIGINT NOT NULL REFERENCES activity (id) ON DELETE CASCADE,
    level VARCHAR(16) NOT NULL CHECK (level IN ('PROBABLE', 'POSSIBLE')),
    rule VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('AUTO', 'CONFIRMED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (activity_id, duplicate_of_id)
);
```

Masquage : une activité liée `PROBABLE` + `AUTO` ou `CONFIRMED` est exclue
des recherches publiques ; `REJECTED` annule le lien. Rien n'est supprimé.
Phase conditionnée à la mesure sur les données réelles de production.

### 7.5 Impacts

| Zone | Phase A | Phase B | Phase C | Phase D |
|---|---|---|---|---|
| Modèle | 1 colonne | 4 colonnes, 1 table | 1 table | 1 table |
| Import OpenAgenda | précision `UNKNOWN` | rattachement d'événements aux lieux permanents existants | — | liens calculés |
| Import DATAtourisme (événements) | générique → `APPROXIMATE` | idem | — | liens calculés |
| Import DATAtourisme (lieux) | — | nouveau collecteur, liste blanche de types | horaires si disponibles | — |
| Contributions manuelles | `UNKNOWN` (puis niveau Nominatim) | — | — | — |
| Carte et liste | marqueur et mention « approximatif » | couche « Lieux », onglet liste, fiche | « Jours à confirmer » / horaires | doublons masqués |
| Données existantes | toutes `UNKNOWN`, aucun rattrapage | lignes existantes inchangées (`place_type` nul) | — | aucune activité modifiée |
| Suppressions | — | cascade de provenance | cascade d'horaires | cascade de liens |
| Tests | § 2.6 | § 4 | migration, affichage sans horaires | `CrossSourceDuplicateMatcherTest` (déjà écrit), mesure à blanc, masquage réversible |

**Déjà implémenté sans changement de modèle** : la règle de doublons
(`CrossSourceDuplicateMatcher`, fonction pure, 9 tests dont les faux
positifs mesurés ; non branchée, donc sans effet sur l'application) ; la règle « adresse générique →
nom du lieu, sans adresse » du collecteur DATAtourisme.

## 3. Impacts

| Zone | Impact |
|---|---|
| Activités, horaires, occurrences | Aucun : colonnes nullables, lignes existantes inchangées, requêtes de recherche d'activités inchangées. |
| Lieux partagés | Préservés. La promotion d'un lieu d'événement en lieu permanent garde son `id` : les programmations restent attachées. |
| `LocationService#findOrCreate` | Ajout du contrôle de distance (correctif 1.3.1). Peut créer quelques lieux supplémentaires là où deux points distants partageaient une adresse : c'est l'effet recherché. |
| `Source` | Nouveaux types (`DATATOURISME_PLACE`, `DATA_ES`, `BASILIC`). Aucune colonne ajoutée. |
| Administration | Modération des lieux `PENDING` (même principe qu'`AdminActivityController`). |
| Performance | Recherche de lieux sur `geo_location` déjà indexée (GIST). Import : quelques milliers de lieux par département. |
| Documentation | `LOCATION_CONTRACT.md`, `SOURCE_CONTRACT.md`, `ARCHITECTURE.md`, contrat d'API des lieux. |

## 4. Tests nécessaires

- **Migration** : les lignes `location` existantes gardent `place_type`
  et `status` nuls ; la contrainte refuse un `status` sans `place_type`.
- **Non-régression** : tests existants de recherche
  (`GeographicEndpointsNonRegressionIntegrationTest`,
  `ActivitySearchIntegrationTest`, `RealSchedulingScenariosIntegrationTest`)
  inchangés et verts.
- **Rapprochement** (`LocationService`) : même adresse à 0 m → même lieu ;
  même adresse à 2 km → deux lieux (cas réel de Bonnieux en fixture).
- **Déduplication des lieux** (intégration PostGIS) : ré-import même
  source ; fusion entre deux sources à 30 m avec le même nom ; pas de
  fusion à 30 m avec des noms différents ; pas de fusion à 300 m ; cas
  ambigu → `PENDING` ; promotion d'un lieu d'événement sans perte de ses
  programmations ; un lieu porteur de programmations n'est pas déplacé.
- **API** : seuls les lieux `PUBLISHED` sont exposés ; fiche avec activités
  à venir ; un lieu d'événement simple n'apparaît jamais comme lieu.
- **Collecteurs** : extraits réels de chaque source en fixture, comme
  `DataTourismeCollectorTest`.
- **Frontend** : couche lieux distincte, fiche sans date, liste.

## 5. Alternatives écartées

- **Nouvelle entité `Place`** distincte de `Location` : doublonne adresse,
  coordonnées et contacts, et empêche de rattacher les événements à la
  fiche du lieu sans table de correspondance. Écartée (décision d'Alex).
- **Lieu permanent = activité sans date** : fausse le modèle (statut
  d'occurrence, filtres de date, classification d'activités). Écartée.
- **Provenance dans des colonnes de `location`** (`source_id`,
  `external_id`) : un seul fournisseur par lieu, donc pas de fusion
  multi-sources. Écartée.

## 6. Décisions et blocages pour Alex

1. **Valider ce modèle cible** (§ 2.2) avant toute migration.
2. **Défaut 1.3.2 (doublons OpenAgenda / DATAtourisme)** : stratégie
   acceptée (§ 2.9) ; règle implémentée et testée, non branchée. Reste à
   valider la phase D (§ 7.4) après mesure sur les données de production.
   En attendant, une source DATAtourisme ne s'active qu'après un essai à
   blanc réussi ; celle de la migration V60, fusionnée active, est à
   traiter selon `DATAGOUV_SOURCES.md` (« Migration V60 »).
3. **Défaut 1.3.3 (fenêtre de 7 jours)** : corrigé dans la PR #104.
4. **Précision géographique** (§ 2.6) : valider les trois valeurs, la
   règle d'affichage et l'absence de rattrapage des données existantes.
5. **Marchés** (§ 2.7) : valider le principe « lieu `MARKET` + horaires
   d'ouverture, jours à confirmer », et décider du traitement ponctuel du
   fichier N-Triples complet (§ 2.8).
6. **Mesure du recouvrement réel OpenAgenda / DATAtourisme, et de la
   couverture JEP / Fête de la science** : non réalisée faute d'accès ;
   accès nécessaires et vérifications préparées dans
   `docs/02_Architecture/VERIFICATION_JEP_FDS.md`.
7. **Types de lieux DATAtourisme à importer** (liste blanche, § 2.5).
8. **Liaison lieux d'événement ↔ lieux permanents** : décidée le 14/10/2026
   (lien réversible, `V63`, par un administrateur) et implémentée (§ 7.2.10).

Non décidés, hors de cet ADR : couverture OpenAgenda des Journées
européennes du patrimoine et de la Fête de la science (vérification
préparée) ; les food trucks restent gérés manuellement jusqu'à nouvelle
décision.
