package com.locallife.backend.collector.application;

import com.locallife.backend.source.application.SourceService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Activation vérifiée d'une source : un essai à blanc ({@link SourceDryRunService}) est lancé, et la source
 * n'est passée à {@code ACTIVE} que si son verdict est {@code OK}. Configuration invalide, collecte en échec
 * ou résultat suspect : la source reste dans son état, et le bilan dit pourquoi. C'est le seul chemin
 * d'activation d'une source {@code DATATOURISME} ({@link SourceService#activateAfterVerification}).
 */
@Service
public class SourceActivationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceActivationService.class);

    private final SourceDryRunService dryRunService;
    private final SourceService sourceService;

    public SourceActivationService(SourceDryRunService dryRunService, SourceService sourceService) {
        this.dryRunService = dryRunService;
        this.sourceService = sourceService;
    }

    /**
     * @return {@link Optional#empty()} si aucune source ne correspond à {@code sourceId} ; sinon le bilan de
     *     l'essai, avec {@code activated} vrai seulement si la source a été activée
     */
    public Optional<SourceDryRunReport> activate(Long sourceId) {
        return dryRunService.dryRun(sourceId).map(report -> {
            if (report.verdict() != SourceDryRunReport.Verdict.OK) {
                LOGGER.warn("Activation refusée pour la source '{}' : {} {}", report.sourceName(),
                        report.verdict(), report.problems());
                return report;
            }
            sourceService.activateAfterVerification(sourceId);
            LOGGER.info("Source '{}' activée après essai à blanc : {} éléments collectés.",
                    report.sourceName(), report.collected());
            return report.withActivated();
        });
    }
}
