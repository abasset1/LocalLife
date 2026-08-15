# Documentation du collecteur

Documentation opérationnelle du pipeline d'import (Sprint 5) : comment
il fonctionne concrètement, comment le configurer, comment le
déclencher, et comment ajouter un futur collecteur. Complète
`COLLECTOR_CONTRACT.md` (le contrat de l'interface, LL-5003) et
`SOURCE_CONTRACT.md` (le modèle `Source`, LL-5001) sans les remplacer.

---

## Collecteur actif : OpenAgenda

Un seul collecteur existe à ce jour : `OpenAgendaCollector`
(`com.locallife.backend.collector.infrastructure`), qui interroge
l'API officielle [OpenAgenda](https://developers.openagenda.com/) pour
un agenda choisi par Alex.

### Configuration requise

Variables d'environnement (aucune valeur par défaut sensible, aucun
secret committé) :

| Variable                | Obligatoire | Description                                                        |
| ------------------------ | ------------ | -------------------------------------------------------------------- |
| `OPENAGENDA_API_KEY`     | oui          | Clé publique OpenAgenda (compte gratuit, voir leur documentation).   |
| `OPENAGENDA_AGENDA_UID`  | oui          | Identifiant numérique de l'agenda ciblé.                             |
| `OPENAGENDA_SOURCE_NAME` | non          | Nom affiché comme `Source.name`. Par défaut `"OpenAgenda"`.          |

Tant que `OPENAGENDA_API_KEY`/`OPENAGENDA_AGENDA_UID` ne sont pas
définies, `OpenAgendaCollector.collect()` lève une `CollectorException`
explicite — comportement attendu, pas un bug (voir LL-5006).

## Comment est déclenché un import

**Aucun déclencheur automatique n'existe à ce jour.**
`ImportService.importAll()` (`com.locallife.backend.collector.application`)
exécute l'import pour tous les `Collector` enregistrés, mais rien
n'appelle cette méthode dans l'application en cours d'exécution — ni
tâche planifiée (`@Scheduled`), ni endpoint dédié. Elle est appelable
directement en test (voir `ImportServiceIntegrationTest`, LL-5010) ou
depuis du code Java, mais pas encore depuis une requête HTTP ou un cron.

C'est une limitation connue, pas un oubli silencieux — voir
`DETTE_TECHNIQUE.md`. Aucun ticket du Sprint 5 ne demandait
explicitement d'ajouter un déclencheur.

## Résultat d'un import

`ImportService.importAll()` retourne une liste d'`ImportResult` (un par
`Collector`), et journalise (SLF4J) une ligne `INFO` récapitulative par
source. Compteurs disponibles : `fetched`, `created`, `updated`,
`ignored` (donnée invalide, rejetée par `NormalizationService`),
`errors` (exception inattendue sur un élément, ou échec total du
collecteur), `archived` (voir stratégie de suppression ci-dessous).

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

1. Implémenter l'interface `Collector`
   (`com.locallife.backend.collector.domain.Collector`) :
   `getSourceName()` (nom de la `Source`) et `collect()` (renvoie une
   `List<CollectedActivity>`, sans écriture en base — interdit par les
   règles du sprint).
2. Annoter l'implémentation `@Component` (ou `@Service`) : `Spring`
   l'ajoute automatiquement à la `List<Collector>` injectée dans
   `ImportService`, sans registre ni configuration supplémentaire.
3. Si le nouveau collecteur nécessite des identifiants, suivre le
   même principe que `OpenAgendaCollector` : configuration via
   variables d'environnement (`application.properties`,
   `${VARIABLE:valeur_par_défaut_non_sensible}`), aucun secret committé.
4. Attention au piège rencontré en LL-5006 : si la classe a plus d'un
   constructeur, annoter `@Autowired` celui destiné à Spring — sans
   quoi Spring tente un constructeur sans argument et le démarrage de
   l'application échoue entièrement (voir le correctif du 15/08/2026
   dans `PROJECT_STATUS.md`).
5. Attention au format des dates : les API externes ne garantissent pas
   toujours la forme exacte du décalage horaire ISO 8601 (`+0100` vs
   `+01:00`) — voir le correctif équivalent dans `PROJECT_STATUS.md`
   pour `OpenAgendaCollector`, à reproduire si besoin.

Rien d'autre à modifier : ni `ImportService`, ni `NormalizationService`,
ni `DeduplicationService` n'ont besoin de connaître le nouveau
collecteur (règle du sprint : « ne pas créer de framework générique de
collecte » — respectée : `List<Collector>` est le seul mécanisme
d'extension, standard Spring, pas un registre maison).
