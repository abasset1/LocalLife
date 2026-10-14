package com.locallife.backend.collector.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.infrastructure.DataTourismeCollectorFactory;
import com.locallife.backend.collector.infrastructure.OpenAgendaCollectorFactory;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Essai à blanc d'une source : collecte réelle, puis ce qu'un import ferait (créations, mises à jour,
 * archivage), <b>sans aucune écriture</b> — ni activité, ni empreinte, ni date de synchronisation. Sert à
 * vérifier une source avant de l'activer ({@link SourceActivationService}).
 *
 * <p>Verdict ({@link SourceDryRunReport.Verdict}) :
 * <ul>
 *   <li>{@code FAILED} : type non collectable, configuration invalide
 *       ({@link SourceService#validateDataTourismeConfiguration}) ou collecte en échec (fichier suspect,
 *       réseau : voir {@code DataTourismeCollector}) ;</li>
 *   <li>{@code SUSPICIOUS} : collecte réussie mais aucun élément, plus de la moitié des éléments rejetés par
 *       la normalisation, ou disparition massive d'activités à venir (même garde-fou que l'import,
 *       {@link ImportService#planArchive}) ;</li>
 *   <li>{@code OK} sinon.</li>
 * </ul>
 */
@Service
public class SourceDryRunService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceDryRunService.class);

    /**
     * Au-delà (strictement plus de la moitié des éléments collectés), la normalisation rejette trop d'éléments :
     * format ou correspondance de champs à vérifier. 2 sur 4 n'est pas suspect, 3 sur 4 l'est. Valeur initiale
     * de protection technique, pas une règle métier.
     */
    static final double MAX_INVALID_RATIO = 0.5;

    private static final int SAMPLE_SIZE = 5;
    private static final String API_TYPE = "API";

    private final SourceService sourceService;
    private final OpenAgendaCollectorFactory openAgendaCollectorFactory;
    private final DataTourismeCollectorFactory dataTourismeCollectorFactory;
    private final NormalizationService normalizationService;
    private final DeduplicationService deduplicationService;
    private final ActivityRepository activityRepository;

    public SourceDryRunService(
            SourceService sourceService,
            OpenAgendaCollectorFactory openAgendaCollectorFactory,
            DataTourismeCollectorFactory dataTourismeCollectorFactory,
            NormalizationService normalizationService,
            DeduplicationService deduplicationService,
            ActivityRepository activityRepository) {
        this.sourceService = sourceService;
        this.openAgendaCollectorFactory = openAgendaCollectorFactory;
        this.dataTourismeCollectorFactory = dataTourismeCollectorFactory;
        this.normalizationService = normalizationService;
        this.deduplicationService = deduplicationService;
        this.activityRepository = activityRepository;
    }

    /** @return {@link Optional#empty()} si aucune source ne correspond à {@code sourceId} */
    public Optional<SourceDryRunReport> dryRun(Long sourceId) {
        return sourceService.getSourceById(sourceId).map(this::dryRun);
    }

    SourceDryRunReport dryRun(Source source) {
        Optional<String> configurationProblem = configurationProblem(source);
        if (configurationProblem.isPresent()) {
            return SourceDryRunReport.failed(source.id(), source.name(), configurationProblem.get());
        }
        List<CollectedActivity> collected;
        try {
            collected = ImportService.collectorFor(source, openAgendaCollectorFactory, dataTourismeCollectorFactory)
                    .collect();
        } catch (RuntimeException exception) {
            LOGGER.warn("Essai à blanc : échec de la collecte pour la source '{}'", source.name(), exception);
            return SourceDryRunReport.failed(source.id(), source.name(),
                    "Collecte en échec : " + ImportService.displayableCause(exception));
        }

        List<Activity> existing = activityRepository.findBySourceId(source.id());
        Set<String> existingKeys = new HashSet<>();
        existing.forEach(activity -> existingKeys.add(activity.importKey()));
        Set<String> seenKeys = new HashSet<>();
        int invalid = 0;
        int wouldCreate = 0;
        int wouldUpdate = 0;
        for (CollectedActivity item : collected) {
            String key = deduplicationService.computeDeduplicationKey(item);
            seenKeys.add(key);
            if (normalizationService.normalize(item).isEmpty()) {
                invalid++;
            } else if (existingKeys.contains(key)) {
                wouldUpdate++;
            } else {
                wouldCreate++;
            }
        }
        ImportService.ArchivePlan plan =
                ImportService.planArchive(existing, seenKeys, LocalDate.now().atStartOfDay());
        int archiveWithheld = plan.suspicious() ? plan.upcoming().size() : 0;
        int wouldArchive = plan.past().size() + (plan.suspicious() ? 0 : plan.upcoming().size());

        List<String> problems = new ArrayList<>();
        if (collected.isEmpty()) {
            problems.add("Aucun élément collecté : vérifier la ressource et le filtre de départements.");
        } else if (invalid > collected.size() * MAX_INVALID_RATIO) {
            problems.add(invalid + " éléments sur " + collected.size() + " rejetés par la normalisation.");
        }
        if (plan.suspicious()) {
            problems.add(archiveWithheld + " activités à venir sur " + plan.upcomingPublished()
                    + " disparaîtraient : disparition massive, archivage retenu.");
        }
        SourceDryRunReport.Verdict verdict = problems.isEmpty()
                ? SourceDryRunReport.Verdict.OK
                : SourceDryRunReport.Verdict.SUSPICIOUS;
        List<String> sample = collected.stream().limit(SAMPLE_SIZE).map(CollectedActivity::title).toList();
        return new SourceDryRunReport(source.id(), source.name(), verdict, List.copyOf(problems), collected.size(),
                invalid, wouldCreate, wouldUpdate, wouldArchive, archiveWithheld, sample, false);
    }

    private static Optional<String> configurationProblem(Source source) {
        if (SourceService.DATATOURISME_TYPE.equals(source.type())) {
            try {
                SourceService.validateDataTourismeConfiguration(
                        source.url(), source.agendaUid(), source.regionFilter());
                return Optional.empty();
            } catch (IllegalArgumentException exception) {
                return Optional.of("Configuration invalide : " + exception.getMessage());
            }
        }
        if (API_TYPE.equals(source.type())) {
            boolean hasAgenda = source.agendaUid() != null && !source.agendaUid().isBlank();
            return hasAgenda ? Optional.empty()
                    : Optional.of("Configuration invalide : identifiant d'agenda OpenAgenda manquant.");
        }
        if (SourceService.DATATOURISME_PLACES_TYPE.equals(source.type())) {
            return Optional.of("Source de lieux : utiliser l'essai à blanc et l'activation de l'administration "
                    + "des lieux (/api/v1/admin/places/sources/{id}).");
        }
        return Optional.of("Type de source non collectable : " + source.type() + ".");
    }
}
