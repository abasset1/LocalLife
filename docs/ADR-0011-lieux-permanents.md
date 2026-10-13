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
| A — précision géographique | priorité haute ; dossier de validation au § 7.1 |
| B — lieux permanents | principe accepté ; impacts présentés au § 7.2 |
| C — marchés et horaires | piste acceptée ; étude N-Triples terminée, **jours de marché non trouvés** dans la part lue (§ 2.8) : pas d'implémentation recommandée en l'état |
| D — liens de doublons | règle implémentée et testée mais **non branchée, donc non opérationnelle** ; branchement présenté au § 7.4 |

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
| DATAtourisme lieux | Vaucluse : 3 619 lieux, tous géolocalisés, 204 sans adresse. Dominés par la restauration (986), les commerces (731), l'hébergement (445). Pertinents a priori : sites culturels (345), sports et loisirs (708), patrimoine (59), lieux religieux (68), galeries (74). | Liste blanche des types à importer. |
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

**Recommandation (phase C) :** ne pas développer de connecteur N-Triples
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

**Priorité haute (décision d'Alex du 13/10/2026). Statut : à valider.**
Rien n'est implémenté de cette phase, en dehors de la règle du collecteur
DATAtourisme qui met déjà le texte d'une adresse générique en nom de lieu.

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

**Projet de migration (non créée) :**

```sql
ALTER TABLE location ADD COLUMN coordinate_precision VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    CHECK (coordinate_precision IN ('EXACT', 'APPROXIMATE', 'UNKNOWN'));
ALTER TABLE activity ADD COLUMN coordinate_precision VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    CHECK (coordinate_precision IN ('EXACT', 'APPROXIMATE', 'UNKNOWN'));
-- A5, seulement si retenu : rattrapage ciblé (requête exacte à figer avec la règle du collecteur).
```

**Fichiers concernés** : migration ; `Location`, `Activity`, `CollectedLocation` ;
`DataTourismeCollector` (pose `APPROXIMATE`), `OpenAgendaCollector`
(`UNKNOWN`), `NormalizationService`, `LocationService#findOrCreate` (A6),
`GeocodingService` (plus tard, A3/A4) ; réponses d'API d'activité ;
`ActivityAlertService` (mention) ; frontend : `ExplorerMap`, `ActivityList`,
`ActivityDetailModal`, `directions.ts` ; mobile : types et carte.

**Tests à écrire** : migration (lignes existantes `UNKNOWN`, valeur hors
liste refusée) ; collecteur (générique → `APPROXIMATE`, adresse précise →
`UNKNOWN`) ; normalisation (copie sur l'activité) ; `findOrCreate` (pas de
réutilisation croisée) ; API (champ présent sur les cinq réponses) ; alertes
(mention) ; frontend (marqueur, mention, itinéraire vers la commune) ;
non-régression des recherches géographiques.

**Décisions demandées pour la phase A** : A1 (copie sur l'activité), A5
(rattrapage ciblé ou non), A8 (alertes incluses avec mention), et le
calendrier de la mise à jour de l'application mobile (A9).

### 7.2 Phase B — lieux permanents et provenance : présentation pour décision

**Principe accepté (réutiliser `Location`). Statut : à valider.**

**Impacts sur l'architecture et les fonctionnalités existantes :**

| Zone | Impact | Compromis |
|---|---|---|
| Module `place` | passe d'un simple modèle à un module complet : API publique (`PlaceController`), service de lieux, modération | un module de plus à maintenir, mais pas de doublon d'entité |
| Recherches d'activités, alertes | inchangées : elles ignorent `place_type`/`status` | — |
| Lieux partagés existants | inchangés ; un lieu d'événement peut être promu en lieu permanent, sans changer d'`id` | une promotion modifie la fiche d'un lieu déjà utilisé : règle « ne jamais déplacer un lieu qui porte des programmations » (§ 2.3) |
| Suppression d'activités (administration) | inchangée : les lieux restent (déjà le cas) | les lieux permanents ne sont jamais supprimés avec une activité |
| Suppression d'une source | provenance supprimée en cascade, lieux conservés | un lieu peut rester sans provenance : à archiver par une règle explicite |
| Import | nouveau `PlaceImportService` séparé de l'import d'événements ; mêmes garde-fous (fichier suspect, disparition massive, essai à blanc) | duplication partielle de l'orchestration, à factoriser si un troisième import apparaît |
| Carte web et mobile | nouvelle couche « Lieux », activable ; l'application mobile ne la verra qu'après mise à jour | volume : 3 619 lieux DATAtourisme dans le seul Vaucluse avant liste blanche |
| Administration | modération des lieux `PENDING` (fusions ambiguës) | charge de relecture à mesurer sur un essai à blanc |

**Décisions demandées** : liste blanche des types de lieux DATAtourisme
(§ 2.5 ; proposition : sites culturels, patrimoine, sports et loisirs,
galeries) ; publication directe (`PUBLISHED`) ou modération (`PENDING`) des
lieux importés ; ordre des sources (DATAtourisme, puis Data ES, puis
Basilic).

```sql
ALTER TABLE location
    ADD COLUMN place_type VARCHAR(50),
    ADD COLUMN status VARCHAR(20),
    ADD COLUMN description TEXT,
    ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE location ADD CONSTRAINT chk_location_permanent_place CHECK (
    (place_type IS NULL AND status IS NULL)
    OR (place_type IS NOT NULL AND status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED')));
CREATE INDEX idx_location_published_place ON location (place_type) WHERE status = 'PUBLISHED';

CREATE TABLE location_source (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL REFERENCES source (id) ON DELETE CASCADE,
    external_id VARCHAR(255) NOT NULL,
    content_hash VARCHAR(64),
    last_seen_at TIMESTAMPTZ,
    UNIQUE (source_id, external_id)
);
```

`ON DELETE CASCADE` sur la source : la suppression d'une source
(`SourceService#deleteSource`) retire sa provenance, pas les lieux.

### 7.3 Phase C — marchés et horaires d'ouverture (§ 2.7)

```sql
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

Un marché sans ligne d'horaires s'affiche « Jours de marché à confirmer ».
Phase conditionnée au résultat de l'étude N-Triples (§ 2.8).

### 7.4 Phase D — liens de doublons : présentation pour décision

**Conception poursuivie. Statut : la règle existe et est testée, mais elle
n'est pas branchée : elle n'est pas opérationnelle.**

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

Non décidés, hors de cet ADR : couverture OpenAgenda des Journées
européennes du patrimoine et de la Fête de la science (vérification
préparée) ; les food trucks restent gérés manuellement jusqu'à nouvelle
décision.
