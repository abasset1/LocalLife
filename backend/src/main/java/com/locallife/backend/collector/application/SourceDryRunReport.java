package com.locallife.backend.collector.application;

import java.util.List;

/**
 * Bilan d'un essai à blanc ({@link SourceDryRunService}) : ce qu'un import ferait pour une source, sans
 * qu'aucune écriture n'ait eu lieu.
 *
 * <ul>
 *   <li>{@code verdict} : {@code OK} (activation possible), {@code FAILED} (configuration invalide ou collecte
 *       en échec : rien n'a pu être collecté) ou {@code SUSPICIOUS} (collecte réussie mais résultat à vérifier,
 *       voir {@code problems}) ;</li>
 *   <li>{@code collected} : éléments renvoyés par le collecteur ; {@code invalid} : parmi eux, rejetés par la
 *       normalisation ; {@code wouldCreate}/{@code wouldUpdate} : nouveaux ou déjà importés ;</li>
 *   <li>{@code wouldArchive} : activités qu'un import archiverait ; {@code archiveWithheld} : activités à
 *       venir que le garde-fou d'archivage retiendrait ;</li>
 *   <li>{@code sampleTitles} : premiers titres collectés, pour un contrôle visuel ;</li>
 *   <li>{@code activated} : vrai seulement si la source a été activée à l'issue de cet essai
 *       ({@link SourceActivationService}).</li>
 * </ul>
 */
public record SourceDryRunReport(
        Long sourceId,
        String sourceName,
        Verdict verdict,
        List<String> problems,
        int collected,
        int invalid,
        int wouldCreate,
        int wouldUpdate,
        int wouldArchive,
        int archiveWithheld,
        List<String> sampleTitles,
        boolean activated) {

    /** Issue de l'essai. */
    public enum Verdict {
        OK,
        FAILED,
        SUSPICIOUS
    }

    static SourceDryRunReport failed(Long sourceId, String sourceName, String problem) {
        return new SourceDryRunReport(
                sourceId, sourceName, Verdict.FAILED, List.of(problem), 0, 0, 0, 0, 0, 0, List.of(), false);
    }

    SourceDryRunReport withActivated() {
        return new SourceDryRunReport(sourceId, sourceName, verdict, problems, collected, invalid, wouldCreate,
                wouldUpdate, wouldArchive, archiveWithheld, sampleTitles, true);
    }
}
