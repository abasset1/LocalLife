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
Depuis LL-8009, plusieurs **instances** de cette classe peuvent être
enregistrées — une par agenda réellement configuré — via
`OpenAgendaSourcesConfig` (`@Configuration`, même package) : ce n'est
plus un simple `@Component` auto-détecté par Spring, précisément parce
qu'un unique `@Component` ne permettait d'enregistrer qu'un seul agenda
à la fois (écart trouvé pendant LL-8009 — voir sa Javadoc et
`docs/PROJECT_STATUS.md`, section LL-8009, pour le détail).

### Configuration requise

Variables d'environnement (aucune valeur par défaut sensible, aucun
secret committé) :

| Variable                | Obligatoire | Description                                                        |
| ------------------------ | ------------ | -------------------------------------------------------------------- |
| `OPENAGENDA_API_KEY`     | oui          | Clé publique OpenAgenda (compte gratuit, voir leur documentation), commune à tous les agendas. |
| `OPENAGENDA_AGENDA_UID`  | oui          | Identifiant numérique de l'agenda de démonstration (rétrocompatibilité LL-5006). |
| `OPENAGENDA_SOURCE_NAME` | non          | Nom affiché comme `Source.name` pour cet agenda. Par défaut `"OpenAgenda"`. |
| `OPENAGENDA_REGION_FILTER` | non, **temporaire** | Ne conserve que les événements dont `location.region` correspond exactement (insensible casse/espaces). Filtrage côté client, après récupération (ajoute `detailed=1` à la requête quand actif). ⚠️ Non vérifié contre l'API réelle en sandbox — à confirmer avec une clé réelle que le champ `region` est bien présent dans la réponse. À retirer quand le besoin temporaire n'existe plus (pas de ticket associé). Appliqué à tous les agendas configurés, pas seulement celui par défaut. |

**Agendas Avignon (LL-8004/LL-8009)**, définis dans
`application.properties` (`openagenda.avignon-*-uid`/`-name`) plutôt
qu'en variables d'environnement dédiées (à l'exception de
`OPENAGENDA_AVIGNON_SPECTACLES_UID` et consorts, réservées aux trois
agendas pas encore identifiés) :

| Agenda | UID | Statut |
| --- | --- | --- |
| Avignon — Culture | `79839448` | actif |
| Avignon — Spectacles | — | à identifier (placeholder vide) |
| Avignon — Patrimoine | — | à identifier (placeholder vide) |
| Avignon — Loisirs | — | à identifier (placeholder vide) |

Un agenda dont l'UID est vide n'est pas enregistré comme collecteur
(voir la Javadoc de `OpenAgendaSourcesConfig`, `addIfConfigured`) —
aucun risque d'erreur à chaque import tant que ces trois agendas ne
sont pas identifiés.

Tant que `OPENAGENDA_API_KEY`/l'uid d'un agenda ne sont pas définis,
`OpenAgendaCollector.collect()` lève une `CollectorException`
explicite pour cet agenda — comportement attendu, pas un bug (voir
LL-5006) ; les autres agendas configurés ne sont pas affectés par cet
échec isolé (voir « Résultat d'un import » ci-dessous).

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
`Collector` **enregistré**, donc un par agenda réellement configuré
depuis LL-8009 — voir ci-dessus), et journalise (SLF4J) une ligne
`INFO` récapitulative par source, plus une ligne de synthèse globale
(`ImportScheduler`, LL-8005) quand l'import est déclenché
automatiquement. Compteurs disponibles : `fetched`, `created`,
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

1. Implémenter l'interface `Collector`
   (`com.locallife.backend.collector.domain.Collector`) :
   `getSourceName()` (nom de la `Source`) et `collect()` (renvoie une
   `List<CollectedActivity>`, sans écriture en base — interdit par les
   règles du sprint).
2. Annoter l'implémentation `@Component` (ou `@Service`) si une seule
   instance suffit : `Spring` l'ajoute alors automatiquement à la
   `List<Collector>` injectée dans `ImportService`, sans registre ni
   configuration supplémentaire. Si plusieurs instances sont nécessaires
   (plusieurs sources pour la même API, comme `OpenAgendaCollector`
   depuis LL-8009), suivre plutôt le modèle de `OpenAgendaSourcesConfig`
   (`@Configuration` + un seul `@Bean` construisant directement le
   `List<Collector>`) — `@Component` ne permet d'enregistrer qu'une
   seule instance par classe.
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
