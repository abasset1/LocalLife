# ADR-0013 — Food trucks : séparer le profil, les emplacements et les présences (ARCHI-01)

**Statut : décisions D1 à D5 prises par Alex le 18/10/2026 (toutes telles
que proposées, § 4) ; implémenté et testé (§ 6).** Le SQL du § 3 est
documentaire ; la migration réelle est `V65__food_truck_presences.sql`.

Ticket : [#143 ARCHI-01](https://github.com/abasset1/LocalLife/issues/143).
Tickets liés : #141 FOODTRUCK-01 (gestion des lieux de présence par le
professionnel), #144 PRO-01 lot C (création d'un food truck depuis
l'espace pro, ADR-0012), ADR-0011 (lieux permanents : `location` est le
référentiel unique des lieux).

## 1. Audit de l'existant (18/10/2026)

| Sujet | Constat |
|---|---|
| Table `food_truck` (V12, V55) | `name`, `description`, `latitude`/`longitude` **obligatoires**, `category` (texte libre), `contact`, `status` (`PENDING`/`PUBLISHED`/`REJECTED`), `published_at` (trigger V55). Aucun propriétaire, aucune date, aucune notion d'emplacement : **le food truck est aujourd'hui un point fixe**, ce que ARCHI-01 interdit. |
| API | `GET /api/v1/foodtrucks` (public, tous les `PUBLISHED`, entité renvoyée telle quelle), `POST /api/v1/foodtrucks` (tout connecté, publié directement). Pas de consultation par id, pas de modification. |
| Alertes (V55) | `ActivityAlertRepository#NEW_MATCHING_FOOD_TRUCKS` : rayon calculé sur `food_truck.latitude/longitude`, envoi unique par `activity_alert_food_truck_sent`. |
| Web | `App.tsx` charge `/api/v1/foodtrucks` une fois ; `ExplorerMap` affiche un marqueur par food truck à `latitude/longitude`. Pas de filtre dédié, pas de liste. |
| Mobile | `listFoodTrucks`, marqueurs de carte (`useMapData`, `markers.tsx`), fiche `foodtruck/[id]` (nom, catégorie, description, contact ; ne lit pas la position), alertes, accueil. |
| Lieux | `location` (V17) : référentiel unique ; lieux permanents typés (`place_type`, ADR-0011) ; ADR-0011 § 2.1 : **pas de seconde entité « lieu »**. |
| Activités | Type d'activité `FOODTRUCK` (V33) : un *événement* de food trucks reste une activité, sans lien avec `food_truck`. Hors périmètre. |

## 2. Principes proposés

1. **Le food truck est un profil, jamais un lieu.** `food_truck` garde son
   identité (nom, description, cuisine, contact, statut, propriétaire à
   venir avec PRO-01 lot C) et perd, à terme, sa position.
2. **Un emplacement est une `location`** (ADR-0011, pas d'entité
   concurrente). S'il correspond à un lieu LocalLife existant (marché
   couvert, parking d'un lieu permanent…), la présence pointe sur ce lieu ;
   sinon une `location` non typée est créée, comme une adresse d'événement.
   Un emplacement n'est donc jamais un lieu permanent par défaut, et un
   food truck n'est jamais converti en lieu.
3. **Une présence relie un food truck, un emplacement et un créneau.**
   Plusieurs food trucks peuvent partager un emplacement, à des créneaux
   distincts ; un food truck peut fréquenter plusieurs emplacements.
4. **Compatibilité stricte** : identifiants des food trucks inchangés,
   alertes déjà envoyées conservées, `GET /api/v1/foodtrucks` garde ses
   champs (`latitude`/`longitude` deviennent la position de la présence
   affichée), affichage public inchangé pour les food trucks existants.
5. **Consultation gratuite** : carte et liste publiques, sans compte.

## 3. Modèle proposé (documentaire)

```sql
CREATE TABLE food_truck_presence (
    id BIGSERIAL PRIMARY KEY,
    food_truck_id BIGINT NOT NULL REFERENCES food_truck (id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES location (id),
    spot_kind VARCHAR(20) NOT NULL CHECK (spot_kind IN ('MARKET', 'PRIVATE', 'EVENT', 'OTHER')),
    days_of_week VARCHAR(30),      -- 'MO,WE,SA' ; NULL = tous les jours
    start_time TIME,               -- NULL = horaires non précisés
    end_time TIME,
    valid_from DATE,               -- NULL = sans début
    valid_until DATE,              -- NULL = sans fin
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUSPENDED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ftp_truck ON food_truck_presence (food_truck_id) WHERE status = 'PUBLISHED';
CREATE INDEX idx_ftp_location ON food_truck_presence (location_id);
```

- **Présence active** : `PUBLISHED`, food truck `PUBLISHED`, aujourd'hui
  dans `[valid_from, valid_until]` (fuseau `Europe/Paris`), jour courant
  dans `days_of_week`. Une présence expirée ou suspendue n'est jamais
  présentée comme actuelle (exigence de FOODTRUCK-01).
- **Lien avec un lieu existant** : `location_id` vers un lieu permanent
  publié, ou vers une `location` créée pour l'occasion. Rapprochement à la
  saisie (FOODTRUCK-01) avec la même recherche que PRO-01 (100 m ou même
  nom dans la commune).
- **Migration des food trucks existants** : pour chacun, une `location`
  non typée à ses coordonnées (`coordinate_precision = 'UNKNOWN'`, sans
  adresse) et une présence `PUBLISHED`, `spot_kind = 'OTHER'`, sans jours
  ni dates (« présence permanente »). Résultat visible identique.
  `food_truck.latitude/longitude` deviennent nullables et ne sont plus
  écrits ; leur suppression viendra dans une migration ultérieure, une
  fois web, mobile et alertes passés aux présences.

### API publique (compatible)

| Endpoint | Changement |
|---|---|
| `GET /api/v1/foodtrucks` | Mêmes champs. Un élément **par présence active** (un food truck présent à deux endroits aujourd'hui apparaît deux fois), `latitude`/`longitude` = celles de l'emplacement ; nouveaux champs `presenceId`, `spotName`, `spotKind`, `startTime`, `endTime`, `placeId` (lieu LocalLife lié, ou `null`). Paramètre facultatif `date` (défaut : aujourd'hui). |
| `GET /api/v1/foodtrucks/{id}` (nouveau) | Fiche du food truck et ses présences publiées à venir (planning). |
| `GET /api/v1/places/{id}/foodtrucks` (nouveau) | Food trucks présents à un lieu LocalLife (fiche lieu). |
| `POST /api/v1/foodtrucks` | Inchangé jusqu'à PRO-01 lot C ; crée en plus la présence permanente à la position reçue. |

### Alertes

`NEW_MATCHING_FOOD_TRUCKS` calcule le rayon sur les emplacements des
présences publiées du food truck (un food truck nouveau est signalé s'il a
au moins un emplacement dans le rayon). Envois passés conservés.

### Interface

Web et mobile : couche « Food trucks » distincte, avec sa propre bascule
(comme « Lieux ») et un pictogramme dédié ; dans la liste, un onglet
« Food trucks » séparé des activités et des lieux, avec l'emplacement et
les horaires du jour. La fiche d'un lieu affiche « Food trucks présents ».

## 4. Décisions attendues

| # | Question | Proposition |
|---|---|---|
| **D1** ✅ | Où vit un emplacement ? | Dans `location` (ADR-0011), lié au lieu existant quand il y en a un. Alternative : table `food_truck_spot` dédiée avec un lien facultatif vers `location` (plus simple à isoler, mais recrée une entité « lieu » concurrente). |
| **D2** ✅ | Que montre la carte un jour donné ? | Les présences actives ce jour-là (un marqueur par présence). Alternative : un seul marqueur par food truck, à sa prochaine présence. |
| **D3** ✅ | Food trucks existants | Migration en présence permanente à leur position actuelle (affichage inchangé). Alternative : les masquer tant que le professionnel n'a pas saisi d'emplacement. |
| **D4** ✅ | Périmètre d'ARCHI-01 | Modèle, migration, API publique compatible, alertes, couches et filtres séparés (web et mobile). La saisie et la gestion des présences par le professionnel restent dans FOODTRUCK-01, la création du food truck par un pro dans PRO-01 lot C. |
| **D5** ✅ | Type d'emplacement | Porté par la présence (`spot_kind`), car un même parking peut servir de marché un jour et d'événement un autre. Alternative : porté par l'emplacement. |

## 5. Découpage proposé (après validation)

1. Migration `V65` (table, migration des food trucks existants, colonnes
   nullables) et tests de non-régression de l'affichage et des alertes.
2. Domaine et API publique (présences actives, fiche, food trucks d'un
   lieu) ; tests des règles de présence (jours, horaires, période,
   suspension, expiration).
3. Alertes sur les emplacements.
4. Web : couche et onglet « Food trucks », bloc sur la fiche d'un lieu.
5. Mobile : mêmes changements de lecture.
6. Documentation : `FOOD_TRUCK_CONTRACT.md`, README, CHANGELOG.

## 6. Implémentation (18/10/2026)

| Élément | Où |
|---|---|
| Migration | `V65__food_truck_presences.sql` : table `food_truck_presence` (contraintes sur les jours, la période et les horaires), reprise des food trucks existants en présence permanente sur un emplacement non typé à leur position (vérifiée sur données d'essai : même position, emplacement sans `place_type`, food truck non publié toujours masqué), `food_truck.latitude/longitude` nullables et plus écrites. |
| Domaine | `FoodTruckPresence` (vue), `SpotKind`, `PresenceStatus` ; `FoodTruck.latitude/longitude` en lecture seule. |
| Lecture | `FoodTruckPresenceRepository` : présences actives un jour donné (statut, période, jour de la semaine), planning d'un food truck, présences à un lieu (le lieu lui-même ou un emplacement relié par `place_id`) ; `placeId` = lieu LocalLife publié de l'emplacement. |
| API | `GET /api/v1/foodtrucks?date=` (un élément par présence, champs historiques conservés), `GET /api/v1/foodtrucks/{id}` (profil + planning), `GET /api/v1/places/{id}/foodtrucks`. `POST /api/v1/foodtrucks` crée la présence permanente à la position reçue ; sa réponse ne change pas. |
| Alertes | `ActivityAlertRepository` : rayon calculé sur les emplacements des présences publiées non terminées. |
| Web | Bascule « 🚚 Food trucks » (affichée par défaut, choix mémorisé), un marqueur par présence avec l'emplacement et les horaires, onglet « Food trucks » de la liste (zone affichée), bloc « Food trucks présents aujourd'hui » sur la fiche d'un lieu. |
| Mobile | Bascule de la couche, un marqueur par présence, emplacement et horaires dans l'aperçu, fiche lue par `GET /foodtrucks/{id}` avec « Où le trouver » (planning). |
| Tests | `FoodTruckPresenceIntegrationTest` (jours, période, suspension, food truck non publié, deux emplacements, emplacement partagé, lien avec un lieu, fiche et planning), tests existants adaptés (`FoodTruckControllerTest`, `FoodTruckServiceTest`, `FoodTruckControllerIntegrationTest`, `ActivityAlertRepositoryIntegrationTest`), `FoodTruckList.test.tsx`, `presence.test.ts`. |

Limites connues : la présence du jour est jugée à la journée (une présence
dont l'horaire est passé reste listée ce jour-là, avec ses horaires) ;
l'application mobile n'a pas d'onglet liste « Food trucks » ni de bloc sur
la fiche d'un lieu ; la saisie des présences par le professionnel relève de
FOODTRUCK-01, la création du food truck par un pro de PRO-01 lot C.
