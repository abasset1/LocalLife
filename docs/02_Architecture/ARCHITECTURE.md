# Architecture

Monolithe modulaire, API REST (Spring Boot 4.1, Java 21), PostgreSQL/PostGIS, frontend React.

## Organisation des modules (backend)

Un package par domaine métier sous `com.locallife.backend` :

* `activity` — cœur du produit : entité `Activity`, recherche géographique
  (rayon, zone cartographique), filtres, contribution manuelle, DTO
  résumé/détail (`ActivitySummaryResponse`/`ActivityDetailResponse`, voir
  « Résumé vs détail » plus bas).
* `place`, `schedule`, `media`, `link`, `accessibility`, `tag`,
  `metadata` — modèle événementiel riche (Sprint 11) : lieux,
  programmation/récurrence/occurrences, médias, liens/contacts,
  accessibilité, tags et informations complémentaires d'une activité.
  Voir « Modèle événementiel » ci-dessous.
* `premium` — point d'extension unique décidant si un appelant reçoit la
  fiche détaillée ou le résumé d'une activité (`PremiumAccessPolicy`),
  sans qu'aucun autre module ne connaisse la notion de forfait. Voir
  « Résumé vs détail » et `docs/ADR-0008-preparation-premium.md`.
* `source` — origine d'une activité (import ou saisie manuelle). Voir
  `SOURCE_CONTRACT.md`.
* `foodtruck` — module indépendant (Sprint 6), point d'intérêt
  affiché sur la même carte que `Activity` sans en dépendre (pas de
  champs de date, pas de lien avec `source`) — voir
  `FOOD_TRUCK_CONTRACT.md` pour le raisonnement complet, le patron à
  suivre pour un futur type de point supplémentaire, et la distinction
  avec une activité à plusieurs lieux (Sprint 11, voir plus bas).
* `collector` — collecte, normalisation, déduplication et import de
  données externes vers `Activity` et le modèle événementiel riche. Voir
  `COLLECTOR_CONTRACT.md`, `COLLECTOR_OPERATIONS.md` et
  `docs/ADR-0005-import-pipeline-enrichment.md`.
* `category`, `user`, `auth`, `like`, `geocoding`, `contribution`,
  `admin` — modules plus ciblés, un par fonctionnalité.
* `common`, `config` — code transverse (gestion d'erreurs générique,
  configuration Spring).

Chaque module suit, quand pertinent, la même sous-structure en couches :

* `domain` — entités/records métier, sans dépendance vers Spring Data
  ni vers un autre module (sauf référence par id, ex. `Activity.sourceId`).
* `application` — services (`@Service`), orchestration, règles métier.
* `infrastructure` — accès aux données (`Repository` Spring Data JDBC)
  et intégrations externes (ex. `OpenAgendaCollector`, `RestClient`).
* `api` — contrôleurs REST (`@RestController`), DTOs d'entrée/sortie.

Convention constante depuis le Sprint 0 : `Repository` (pas
`CrudRepository`) pour n'exposer que les méthodes réellement utilisées ;
records Java pour les entités, sans enum dédié pour les champs
`status`/`type` (chaînes libres, extensibles sans migration de code —
voir `SOURCE_CONTRACT.md`).

## Persistance

PostgreSQL + PostGIS, migrations Flyway séquentielles
(`backend/src/main/resources/db/migration/`). PostGIS utilisé pour la
recherche géographique (`&&`, `ST_MakeEnvelope`, `ST_DWithin`, voir
`GEO_SEARCH_CONTRACT.md`/`BOUNDING_BOX_SEARCH_CONTRACT.md`), sur la
colonne `activity.location` comme sur `location.geo_location` (Sprint 11,
voir plus bas) — les deux sont maintenues automatiquement par un trigger
depuis les colonnes `latitude`/`longitude`.

## Modèle événementiel (Sprint 11)

Depuis le Sprint 11, une activité peut porter une programmation détaillée
plutôt que la seule date/lieu plats hérités des premiers sprints :

```text
Activity
    ↓
Schedule
    ↓
Recurrence
    ↓
Occurrence
    ↓
Location
```

* **`Activity`** reste l'entité centrale, cherchable et affichable comme
  avant (colonnes plates `latitude`/`longitude`/`city`/`start_date`/
  `end_date` toujours présentes et alimentées, pour la compatibilité —
  voir « Contributions manuelles » ci-dessous).
* **`Schedule`** — une programmation récurrente ou ponctuelle
  rattachée à une activité, avec un lieu par défaut (`Location`). Une
  activité peut avoir plusieurs `Schedule` (ex. tournée d'un food truck
  dans plusieurs villes selon le jour de la semaine, voir
  `docs/ADR-0006-recherche-occurrences.md`).
* **Recurrence** — portée par `Schedule.recurrenceRule`, une règle RFC
  5545 (RRULE, ex. `FREQ=WEEKLY;BYDAY=TU`), validée par
  `RecurrenceRuleService`. Un `Schedule` sans règle est un événement
  ponctuel.
* **`Occurrence`** — une date/heure concrète où l'activité a réellement
  lieu, rattachée à un `Schedule`. Matérialisée explicitement (pas
  recalculée à la volée depuis la RRULE) pour porter les **exceptions** :
  une occurrence peut être annulée (`status = CANCELLED`, exclue des
  recherches — voir `ActivityRepository`/ADR-0006 — mais visible dans la
  fiche détaillée, voir `docs/ADR-0010-cas-reels-programmation.md`) ou
  déplacée vers un lieu différent de celui du `Schedule`
  (`Occurrence.locationId`, résolu en priorité sur celui du `Schedule`).
* **`Location`** — un lieu structuré (adresse, ville, coordonnées,
  fuseau horaire), potentiellement partagé par plusieurs `Schedule`/
  `Occurrence` (`LocationService#findOrCreate`, voir ADR-0005).

À cela s'ajoutent, rattachés directement à `Activity` : `Media` (galerie
d'images), `ActivityLink` (liens/contacts), `Accessibility` (cinq
catégories, voir `Accessibility`), `ActivityTag` et `ActivityMetadata`
(informations complémentaires, stockage `jsonb`, voir
`docs/ADR-0004-metadata-jsonb.md`).

Toutes ces entités sont peuplées par le pipeline d'import (voir
ci-dessous) — une **contribution manuelle** (formulaire, `POST
/api/v1/activities`) ne crée à ce jour que l'`Activity` elle-même, sans
`Location`/`Schedule`/`Occurrence` : la recherche (`ActivityRepository`)
et la fiche détaillée savent gérer ce cas (repli sur les colonnes plates
d'`Activity`, voir ADR-0006/ADR-0007).

### Résumé vs détail

`GET /api/v1/activities` retourne `ActivitySummaryResponse` (champs
légers : titre, catégorie, date, ville, adresse, coordonnées, image
principale) ; `GET /api/v1/activities/{id}` retourne
`ActivityDetailResponse` (arborescence complète ci-dessus) — voir
`docs/ADR-0007-dto-resume-detail-activite.md`. Lequel des deux
`GET /api/v1/activities/{id}` retourne effectivement dépend de
`PremiumAccessPolicy#hasPremiumAccess` (module `premium`, voir
ci-dessus) : **future distinction Basic/Premium**, préparée mais pas
implémentée (`StubPremiumAccessPolicy` accorde l'accès complet à tout le
monde aujourd'hui — paiement/abonnement hors périmètre du Sprint 11, voir
`docs/ADR-0008-preparation-premium.md`).

### Sources externes et conservation du payload brut

`ExternalActivity` (module `collector`) conserve, pour chaque élément
collecté d'une source externe, sa charge JSON brute (`jsonb`,
`raw_payload`) et son empreinte (`payload_hash`), en plus de
l'`Activity` normalisée qu'il produit — trace exploitable pour du
débogage ou une future ré-normalisation, indépendante de
`NormalizationService`. Voir `COLLECTOR_CONTRACT.md`.

## Pipeline d'import

Alimente `Activity` **et** le modèle événementiel riche ci-dessus depuis
des sources externes, en plus de la contribution manuelle. Vue
d'ensemble :

```text
Collector.collect()          (collecteur/infrastructure)
        ↓
CollectedActivity            (donnée brute normalisable, structurée en
                                sous-modèles : CollectedLocation,
                                CollectedTiming[], CollectedMedia[],
                                CollectedLink[], CollectedAccessibility)
        ↓
DeduplicationService          → clé déterministe (externalId, ou repli composite)
        ↓
NormalizationService          → Optional<Activity> (rejet si invalide)
                                → Location/Schedule/Occurrence/Media/
                                  ActivityLink/Accessibility/ActivityTag/
                                  ActivityMetadata (conversions séparées,
                                  jamais rejetées avec l'Activity)
        ↓
ImportService                 → SourceService (résolution/création Source)
                                → ActivityRepository (création/mise à jour/archivage)
                                → services du modèle riche (Location/Schedule/
                                  Occurrence/Media/Link/Accessibility/Tag/
                                  Metadata) — remplacées à chaque réimport
                                  plutôt que fusionnées, voir ADR-0005
        ↓
ImportResult                  (journalisé, voir COLLECTOR_OPERATIONS.md)
```

Détail complet (configuration, déclenchement, stratégie de suppression
douce) : `COLLECTOR_OPERATIONS.md`. Détail de l'enrichissement du modèle
riche : `docs/ADR-0005-import-pipeline-enrichment.md`.

## Documentation API (OpenAPI)

Générée automatiquement par springdoc-openapi depuis les annotations
`@Operation`/`@ApiResponse`/`@Parameter`/`@Schema` déjà présentes sur les
contrôleurs (`ActivityController`, `SourceController`, ...) — pas de
fichier `openapi.yaml` statique à maintenir dans ce projet, l'UI Swagger
reflète directement le code.

## Frontend

React + Vite, carte interactive (Leaflet), consomme l'API REST du
backend. Depuis le Sprint 6, un second type de marqueur (food truck) est
affiché sur la même carte, avec une icône dédiée pour la distinction
visuelle — voir `FOOD_TRUCK_CONTRACT.md` pour le patron à suivre si un
troisième type de point est ajouté plus tard. Depuis le Sprint 11
(`docs/ADR-0009-frontend-modele-riche.md`), la fiche détaillée d'une
activité (carte et liste) exploite le modèle événementiel riche
(galerie, description longue, horaires avec récurrence traduite en
français, lieux, liens/contacts, accessibilité, tags, informations
complémentaires), chargée à la demande via `GET /api/v1/activities/{id}`.
