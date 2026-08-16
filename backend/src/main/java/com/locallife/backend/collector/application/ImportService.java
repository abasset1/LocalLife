package com.locallife.backend.collector.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Persistance des imports (LL-5008) : orchestre {@code Collector} →
 * {@code DeduplicationService} → {@code NormalizationService} →
 * {@code ActivityRepository}, « via les services métier existants »
 * (objectif du ticket) — {@code ActivityRepository} et
 * {@code SourceService} plutôt qu'un accès direct aux tables, conformément
 * à la règle du sprint « un collecteur ne doit jamais écrire directement
 * en base » (le collecteur lui-même, {@code Collector}, ne fait toujours
 * que lire la source externe : c'est ce service, pas lui, qui écrit).
 *
 * Injecte {@code List<Collector>} (et non un seul) : pattern Spring
 * standard pour rester ouvert à plusieurs collecteurs sans construire de
 * registre ni de mécanisme de découverte — un seul {@code Collector} est
 * enregistré à ce stade ({@code OpenAgendaCollector}, LL-5006), la règle
 * du sprint « ne pas créer plusieurs collecteurs » n'est pas enfreinte.
 *
 * Critères LL-5008 :
 * <ul>
 *   <li><b>aucune duplication</b> : une {@code CollectedActivity} déjà
 *       importée (même {@code source}/{@code importKey}, voir
 *       {@code DeduplicationService}) met à jour l'{@code Activity}
 *       existante ({@code ActivityRepository#findBySourceIdAndImportKey})
 *       plutôt que d'en créer une nouvelle ;</li>
 *   <li><b>activité existante mise à jour si elle appartient à la même
 *       source</b> : la recherche de correspondance est scopée à
 *       {@code source.id()}, jamais globale ;</li>
 *   <li><b>activité supprimée de la source</b> : ⚠️ décision à valider —
 *       stratégie choisie : suppression douce (statut {@code ARCHIVED}),
 *       pas de suppression physique. Une activité déjà présente en base
 *       pour cette source mais absente de la dernière collecte est
 *       archivée plutôt que supprimée : plus prudent pour un MVP (une
 *       panne réseau partielle du collecteur, par exemple, ne doit pas
 *       effacer des activités réelles) et conserve un historique exploitable
 *       par LL-5009. ⚠️ Point à surveiller : la recherche/carte (LL-1007/
 *       LL-4002) ne filtre pas {@code status} par défaut — une activité
 *       {@code ARCHIVED} continuera donc d'apparaître tant qu'un filtre
 *       explicite n'est pas ajouté côté requête/frontend (hors périmètre
 *       de ce ticket) ;</li>
 *   <li><b>création manuelle non affectée</b> : le balayage d'archivage
 *       est scopé à {@code source.id()} de la source en cours d'import —
 *       les activités manuelles (source {@code MANUAL}) ont un
 *       {@code sourceId} différent et ne sont donc jamais concernées, par
 *       construction (voir {@code ActivityService#createActivity}).</li>
 * </ul>
 *
 * ⚠️ Autre décision à valider : une {@code CollectedActivity} rejetée par
 * {@code NormalizationService} (donnée invalide) n'est pas ajoutée aux
 * clés « vues » de cet import. Si elle correspond à une activité déjà
 * importée précédemment, cette activité sera donc archivée à ce passage
 * (traitée comme absente), plutôt que laissée telle quelle. Comportement
 * jugé acceptable pour un MVP (une donnée redevenue invalide ne doit pas
 * rester affichée telle quelle) mais à surveiller.
 *
 * Aucun déclencheur (endpoint, tâche planifiée) n'est ajouté par ce
 * ticket : ni {@code SPRINT_5.md} ni les critères de LL-5008 n'en
 * demandent un — {@code importAll()} est appelable directement (tests,
 * LL-5010) mais rien n'invoque encore cette méthode dans l'application en
 * cours d'exécution.
 *
 * <b>Journalisation (LL-5009)</b> : chaque import de source produit une
 * ligne de log (niveau {@code INFO}) résumant le résultat — voir
 * {@code ImportResult} pour le détail des compteurs. Pas de tableau de
 * bord d'administration (exclu explicitement par {@code SPRINT_5.md}) :
 * uniquement des logs applicatifs standard (SLF4J), consultables comme
 * n'importe quel autre log de l'application.
 *
 * ⚠️ Décision à valider : le traitement de chaque élément collecté est
 * isolé par un {@code try/catch} — une exception inattendue sur un
 * élément (bug, donnée totalement malformée) est comptée dans
 * {@code errors} et journalisée ({@code WARN}), sans interrompre le
 * traitement des autres éléments de cette source. De même, un échec total
 * de {@code Collector#collect()} (ex. {@code CollectorException} :
 * configuration manquante, panne réseau) est capturé, journalisé
 * ({@code ERROR}) et traduit en un {@code ImportResult} dégradé
 * ({@code fetched=0}, {@code errors=1}) plutôt que de faire échouer
 * {@code importAll()} pour les autres sources.
 */
@Service
public class ImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportService.class);

    /** Type par défaut attribué à une {@code Source} nouvellement créée à l'occasion d'un import. */
    private static final String DEFAULT_SOURCE_TYPE = "API";

    /** Statut de suppression douce, voir la javadoc de la classe. */
    private static final String ARCHIVED_STATUS = "ARCHIVED";

    private final List<Collector> collectors;
    private final NormalizationService normalizationService;
    private final DeduplicationService deduplicationService;
    private final SourceService sourceService;
    private final ActivityRepository activityRepository;

    public ImportService(
            List<Collector> collectors,
            NormalizationService normalizationService,
            DeduplicationService deduplicationService,
            SourceService sourceService,
            ActivityRepository activityRepository) {
        this.collectors = collectors;
        this.normalizationService = normalizationService;
        this.deduplicationService = deduplicationService;
        this.sourceService = sourceService;
        this.activityRepository = activityRepository;
    }

    /** Exécute l'import pour chaque {@code Collector} enregistré. */
    public List<ImportResult> importAll() {
        return collectors.stream().map(this::importFrom).toList();
    }

    private ImportResult importFrom(Collector collector) {
        LocalDateTime startedAt = LocalDateTime.now();
        Source source = sourceService.findOrCreateByName(collector.getSourceName(), DEFAULT_SOURCE_TYPE, null);

        List<CollectedActivity> collected;
        try {
            collected = collector.collect();
        } catch (RuntimeException exception) {
            LocalDateTime endedAt = LocalDateTime.now();
            LOGGER.error("Échec de la collecte pour la source '{}' : {}", source.name(), exception.getMessage(),
                    exception);
            return new ImportResult(source.name(), startedAt, endedAt, 0, 0, 0, 0, 1, 0);
        }

        int created = 0;
        int updated = 0;
        int ignored = 0;
        int errors = 0;
        Set<String> seenKeys = new HashSet<>();

        for (CollectedActivity item : collected) {
            try {
                String key = deduplicationService.computeDeduplicationKey(item);
                seenKeys.add(key);

                Optional<Activity> normalized = normalizationService.normalize(item);
                if (normalized.isEmpty()) {
                    ignored++;
                    continue;
                }

                Optional<Activity> existing = activityRepository.findBySourceIdAndImportKey(source.id(), key);
                Activity toSave = withSourceAndKey(
                        normalized.get(), existing.map(Activity::id).orElse(null), source.id(), key);
                activityRepository.save(toSave);

                if (existing.isPresent()) {
                    updated++;
                } else {
                    created++;
                }
            } catch (RuntimeException exception) {
                errors++;
                LOGGER.warn("Échec du traitement d'un élément collecté pour la source '{}' : {}",
                        source.name(), exception.getMessage(), exception);
            }
        }

        int archived = archiveActivitiesNoLongerInSource(source.id(), seenKeys);
        LocalDateTime endedAt = LocalDateTime.now();

        ImportResult result = new ImportResult(
                source.name(), startedAt, endedAt, collected.size(), created, updated, ignored, errors, archived);
        LOGGER.info("Import terminé pour la source '{}' : {}", source.name(), result);

        return result;
    }

    private int archiveActivitiesNoLongerInSource(Long sourceId, Set<String> seenImportKeys) {
        int archived = 0;
        for (Activity activity : activityRepository.findBySourceId(sourceId)) {
            boolean stillPresent = activity.importKey() != null && seenImportKeys.contains(activity.importKey());
            boolean alreadyArchived = ARCHIVED_STATUS.equals(activity.status());
            if (!stillPresent && !alreadyArchived) {
                activityRepository.save(withStatus(activity, ARCHIVED_STATUS));
                archived++;
            }
        }
        return archived;
    }

    private Activity withSourceAndKey(Activity activity, Long id, Long sourceId, String importKey) {
        return new Activity(
                id, activity.title(), activity.description(), activity.category(),
                activity.latitude(), activity.longitude(), activity.startDate(), activity.endDate(),
                activity.status(), sourceId, importKey, activity.url());
    }

    private Activity withStatus(Activity activity, String status) {
        return new Activity(
                activity.id(), activity.title(), activity.description(), activity.category(),
                activity.latitude(), activity.longitude(), activity.startDate(), activity.endDate(),
                status, activity.sourceId(), activity.importKey(), activity.url());
    }

}
