# Architecture

Monolithe modulaire, API REST (Spring Boot 4.1, Java 21), PostgreSQL/PostGIS, frontend React.

## Organisation des modules (backend)

Un package par domaine métier sous `com.locallife.backend` :

* `activity` — cœur du produit : entité `Activity`, recherche géographique
  (rayon, zone cartographique), filtres, contribution manuelle.
* `source` — origine d'une activité (import ou saisie manuelle). Voir
  `SOURCE_CONTRACT.md`.
* `collector` — collecte, normalisation, déduplication et import de
  données externes vers `Activity`. Voir `COLLECTOR_CONTRACT.md` et
  `COLLECTOR_OPERATIONS.md`.
* `category`, `user`, `auth`, `geocoding`, `place`, `contribution`,
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
recherche géographique (`&&`, `ST_MakeEnvelope`, voir
`GEO_SEARCH_CONTRACT.md`/`BOUNDING_BOX_SEARCH_CONTRACT.md`).

## Pipeline d'import (Sprint 5)

Alimente `Activity` depuis des sources externes, en plus de la
contribution manuelle. Vue d'ensemble :

```text
Collector.collect()          (collecteur/infrastructure)
        ↓
CollectedActivity            (donnée brute, un item par occurrence)
        ↓
DeduplicationService          → clé déterministe (externalId, ou repli composite)
        ↓
NormalizationService          → Optional<Activity> (rejet si invalide)
        ↓
ImportService                 → SourceService (résolution/création Source)
                                → ActivityRepository (création/mise à jour/archivage)
        ↓
ImportResult                  (journalisé, voir COLLECTOR_OPERATIONS.md)
```

Détail complet (configuration, déclenchement, stratégie de suppression
douce) : `COLLECTOR_OPERATIONS.md`.

## Frontend

React + Vite, carte interactive (Leaflet), consomme l'API REST du
backend. Aucune fonctionnalité frontend spécifique au pipeline d'import
(Sprint 5) : les activités importées sont de simples `Activity`,
indiscernables des activités manuelles côté carte/recherche/filtres
(vérifié en LL-5011).
