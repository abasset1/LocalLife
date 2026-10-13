# ADR-0011 — Lieux permanents : faire évoluer `Location` en référentiel consultable

**Statut : proposé, en attente de validation par Alex.** Aucune entité,
migration ni endpoint n'est créé tant que cette proposition n'est pas
validée.

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
   jours n'apparaissent pas** sur la carte par défaut. À l'inverse, avec une
   date choisie, un marché du jeudi apparaît tous les jours.

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
2. **Défaut 1.3.2 (doublons OpenAgenda / DATAtourisme)** : la source
   DATAtourisme de la PR #104 est créée active. Proposition : la créer
   **inactive** tant qu'une règle de rapprochement entre sources (titre
   normalisé + même jour + moins de 300 m) n'est pas en place, ou accepter
   des doublons temporaires. À trancher.
3. **Défaut 1.3.3 (fenêtre de 7 jours)** : corriger la règle (occurrence
   qui chevauche la fenêtre, et non qui y commence ou finit). Changement
   de comportement des recherches publiques existantes : à valider.
4. **Mesure du recouvrement réel OpenAgenda / DATAtourisme, et de la
   couverture JEP / Fête de la science** : impossible ici. L'environnement
   n'a pas de clé OpenAgenda et bloque `openagenda.com`. Il faut soit une
   clé de lecture et l'accès réseau à `openagenda.com`, soit un export des
   événements importés en base de production. La liste des agendas
   réellement configurés en production est aussi inconnue d'ici (seuls les
   deux de la migration V14 sont versionnés).
5. **Types de lieux DATAtourisme à importer** (liste blanche, § 2.5).
