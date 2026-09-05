# Documentation du collecteur

Documentation opérationnelle du pipeline d'import (Sprint 5) : comment
il fonctionne concrètement, comment le configurer, comment le
déclencher, et comment ajouter un futur collecteur. Complète
`COLLECTOR_CONTRACT.md` (le contrat de l'interface, LL-5003) et
`SOURCE_CONTRACT.md` (le modèle `Source`, LL-5001) sans les remplacer.

---

## Collecteur actif : OpenAgenda

Une seule classe de collecteur existe à ce jour : `OpenAgendaCollector`
(`com.locallife.backend.collector.infrastructure`), qui interroge
l'API officielle [OpenAgenda](https://developers.openagenda.com/).

**Mise à jour LL-EF-005 :** les agendas ne sont plus une liste fixe
construite au démarrage de l'application à partir de variables
d'environnement (`OpenAgendaSourcesConfig`, supprimée) — ils sont
gérés dynamiquement depuis l'interface d'administration (`/admin`,
section « Agendas »), via `SourceController`
(`POST`/`PUT`/`DELETE /api/v1/sources`, réservés au rôle `ADMIN`). Une
instance d'`OpenAgendaCollector` est construite par
`OpenAgendaCollectorFactory` (`com.locallife.backend.collector.infrastructure`)
pour chaque `Source` collectible trouvée en base **à chaque exécution**
de `ImportService#importAll()` — ajouter, modifier ou désactiver
(`status = INACTIVE`) ou supprimer un agenda prend donc effet dès le
prochain import, sans redémarrage de l'application.

Une source est collectible via OpenAgenda si, et seulement si :
`type = "API"`, `status = "ACTIVE"`, et `agendaUid` non vide (voir
`ImportService#isCollectible`).

### Configuration requise

Une seule variable d'environnement reste nécessaire (secret partagé
entre tous les agendas, jamais stocké en base — voir `SOURCE_CONTRACT.md`) :

| Variable            | Obligatoire | Description                                                        |
| -------------------- | ------------ | -------------------------------------------------------------------- |
| `OPENAGENDA_API_KEY` | oui          | Clé publique OpenAgenda (compte gratuit, voir leur documentation), commune à tous les agendas. |

Tout le reste (identifiant d'agenda, nom de la source, filtre région)
se configure désormais **par agenda**, depuis l'interface
d'administration (`SourceController`, champs `agendaUid`/
`regionFilter` du modèle `Source` — voir `SOURCE_CONTRACT.md`) :

| Champ (formulaire admin) | Correspond à                     | Obligatoire |
| -------------------------- | ----------------------------------- | ------------ |
| Nom                        | `Source.name`                       | oui          |
| Identifiant d'agenda        | `Source.agendaUid`                  | oui, pour qu'un agenda de type `API` soit réellement collecté |
| Filtre région (optionnel)  | `Source.regionFilter`               | non          |

Le filtre région ne conserve que les événements dont `location.region`
correspond exactement (insensible casse/espaces) à la valeur fournie —
filtrage côté client après récupération (ajoute `detailed=1` à la
requête OpenAgenda quand actif). ⚠️ Non vérifié contre l'API réelle en
sandbox — à confirmer avec une clé réelle que le champ `region` est
bien présent dans la réponse. Désormais réglable indépendamment par
agenda (remplace l'ancien filtre global unique `OPENAGENDA_REGION_FILTER`).

Tant que `OPENAGENDA_API_KEY` n'est pas définie, ou que l'`agendaUid`
d'un agenda est vide, `OpenAgendaCollector.collect()` lève une
`CollectorException` explicite pour cet agenda — comportement attendu,
pas un bug (voir LL-5006) ; les autres agendas configurés ne sont pas
affectés par cet échec isolé (voir « Résultat d'un import » ci-dessous).

### Agendas migrés lors du passage à la configuration dynamique

La migration `V14__add_agenda_fields_to_source.sql` (LL-EF-005) a
recréé en base les deux agendas jusqu'ici actifs dans
`application.properties`, pour éviter une régression silencieuse au
déploiement (plus aucun agenda ne serait collecté tant qu'un
administrateur ne les aurait pas ressaisis manuellement) :

| Agenda | UID | Statut |
| --- | --- | --- |
| OpenAgenda Ministère culture | `86244142` | actif |
| OpenAgenda Ville d'Avignon | `79839448` | actif |

Les trois agendas Avignon non identifiés (Spectacles/Patrimoine/
Loisirs, LL-8004) n'ont pas été migrés (jamais configurés avec un uid
réel) — à créer depuis l'interface d'administration le jour où ils
sont identifiés, plutôt que par une nouvelle migration.

## Comment est déclenché un import

**Automatique**, depuis LL-8005 : `ImportScheduler`
(`com.locallife.backend.collector.application`, `@Scheduled`)
déclenche `ImportService.importAll()` toutes les heures, sans action
manuelle. Décision initiale du Sprint 7 (« aucun scheduler complexe »)
levée par LL-8005 — un scheduler simple (cron horaire fixe, pas de
configuration dynamique) reste jugé suffisant pour une bêta.

**Manuel**, en complément, via `POST /api/v1/admin/import` (LL-7002,
Sprint 7), réservé au rôle `ADMIN` — voir `AdminImportController`
(`com.locallife.backend.collector.api`) et la section « Déclenchement
d'un import » du `README.md` racine pour un exemple complet.

`ImportService.importAll()` (`com.locallife.backend.collector.application`)
reste la seule logique d'orchestration — ni le contrôleur, ni le
scheduler ne dupliquent de logique d'import, tous deux se contentent de
l'invoquer. Elle reste aussi appelable directement en test (voir
`ImportServiceIntegrationTest`, LL-5010/LL-8009) ou depuis du code Java.

## Résultat d'un import

`ImportService.importAll()` retourne une liste d'`ImportResult` (un par
source collectible trouvée en base au moment de l'appel — voir
« Collecteur actif : OpenAgenda » ci-dessus, LL-EF-005), et journalise
(SLF4J) une ligne `INFO` récapitulative par source, plus une ligne de
synthèse globale (`ImportScheduler`, LL-8005) quand l'import est
déclenché automatiquement. Compteurs disponibles : `fetched`, `created`,
`updated`, `ignored` (donnée invalide, rejetée par
`NormalizationService`), `errors` (exception inattendue sur un élément,
ou échec total du collecteur), `archived` (voir stratégie de
suppression ci-dessous).

Pas de tableau de bord d'administration — exclu explicitement par
`SPRINT_5.md`. Consultation uniquement via les logs applicatifs.


## Déduplication

`DeduplicationService.computeDeduplicationKey(CollectedActivity)` :
priorité à l'identifiant externe fourni par la source
(`source` + `externalId`) ; à défaut, clé composite SHA-256
(`source`/`title`/`startDate`/latitude/longitude). Cette clé est stockée
dans `Activity.importKey`, unique par `Activity.sourceId` (index
partiel, voir la migration `V9__link_activity_to_source.sql`).

## Stratégie de suppression : suppression douce

Une activité déjà importée pour une source, mais absente de la
dernière collecte, est **archivée** (`status = "ARCHIVED"`), jamais
supprimée physiquement. Décision LL-5008 : plus prudent pour un MVP
(une panne réseau partielle du collecteur ne doit pas effacer des
activités réelles), et conserve un historique exploitable.

⚠️ **Point à surveiller** : ni la recherche géographique ni la carte ne
filtrent `status` par défaut (voir `ActivityService`) — une activité
`ARCHIVED` continue donc d'apparaître tant qu'un filtre explicite n'est
pas ajouté côté requête/frontend. Voir `DETTE_TECHNIQUE.md`.

## Activités manuelles vs importées

Toute `Activity` a désormais un `sourceId` obligatoire (LL-5008). Les
activités créées manuellement (formulaire de contribution) sont
rattachées à la source réservée `MANUAL` (une seule ligne, créée par la
migration `V8__create_source_table.sql`) plutôt qu'à un `sourceId` nul —
décision LL-5001, qui évite tout cas particulier « pas de source » dans
le code métier. Leur `importKey` reste `null` : rien à déduplicer pour
une contribution manuelle.

Le pipeline d'import ne touche jamais les activités manuelles : le
balayage d'archivage (`ImportService`) est scopé au `sourceId` de la
source en cours d'import, catégoriquement différent de celui de
`MANUAL` (vérifié en LL-5008/LL-5010).

## Ajouter un futur collecteur

**Mise à jour LL-EF-005 :** l'ancien mécanisme d'extension
(`List<Collector>` injecté automatiquement par Spring, sans code à
modifier dans `ImportService` pour ajouter un type de collecteur) a été
**retiré** avec le passage à une configuration dynamique par source.
`ImportService#isCollectible`/`#importFrom` sont désormais écrits
spécifiquement pour OpenAgenda (`type = "API"` + `agendaUid`, voir
ci-dessus) : ajouter un type de collecteur réellement différent (ex.
RSS) nécessite donc, contrairement à avant ce ticket, de modifier
`ImportService` lui-même pour reconnaître ce nouveau cas (par exemple
`type = "RSS"` avec un champ dédié) et choisir la bonne factory selon
le type de la source. Compromis assumé : la simplicité d'une
configuration 100 % dynamique par source, au prix d'une extensibilité
un peu moindre pour un futur type de collecteur — jugé acceptable tant
qu'un seul type de collecteur existe réellement.

1. Implémenter l'interface `Collector`
   (`com.locallife.backend.collector.domain.Collector`) :
   `getSourceName()` (nom de la `Source`) et `collect()` (renvoie une
   `List<CollectedActivity>`, sans écriture en base — interdit par les
   règles du sprint).
2. Créer un factory dédié suivant le modèle d'`OpenAgendaCollectorFactory`
   (`com.locallife.backend.collector.infrastructure`) : une méthode
   `create(Source)` qui construit une instance du nouveau collecteur à
   partir des champs pertinents de la `Source` — ajouter au modèle
   `Source` (migration Flyway) les champs spécifiques à ce nouveau type
   si nécessaire, comme `agendaUid`/`regionFilter` pour OpenAgenda (voir
   `SOURCE_CONTRACT.md`).
3. Étendre `ImportService#isCollectible` pour reconnaître ce nouveau
   type de source comme collectible (voir le compromis assumé
   ci-dessus), et `#importFrom`/`#importAll` pour appeler le bon
   factory selon `source.type()`.
4. Si le nouveau collecteur nécessite un secret partagé entre toutes
   les sources de ce type (comme la clé API OpenAgenda), suivre le même
   principe : variable d'environnement (`application.properties`,
   `${VARIABLE:valeur_par_défaut_non_sensible}`), jamais stockée en
   base ni exposée via `SourceController`.
5. Attention au piège rencontré en LL-5006 : si la classe a plus d'un
   constructeur, annoter `@Autowired` celui destiné à Spring — sans
   quoi Spring tente un constructeur sans argument et le démarrage de
   l'application échoue entièrement (voir le correctif du 15/08/2026
   dans `PROJECT_STATUS.md`).
6. Attention au format des dates : les API externes ne garantissent pas
   toujours la forme exacte du décalage horaire ISO 8601 (`+0100` vs
   `+01:00`) — voir le correctif équivalent dans `PROJECT_STATUS.md`
   pour `OpenAgendaCollector`, à reproduire si besoin.
