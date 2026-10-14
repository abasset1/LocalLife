package com.locallife.backend.place.application;

import java.util.List;

/**
 * Bilan d'un import de lieux (ADR-0011, phase B), réel ou à blanc.
 *
 * @param verdict         {@code OK}, {@code SUSPICIOUS} (rien d'anormal n'est écrit : archivage suspendu, ou
 *                        aucun lieu retenu) ou {@code FAILED} (collecte en échec, rien n'est écrit)
 * @param collected       lieux lus dans le fichier (départements de la source)
 * @param whitelisted     parmi eux, lieux retenus par la liste blanche
 * @param created         nouveaux lieux, en attente de modération
 * @param updated         lieux connus dont les données ont changé
 * @param backToPending   lieux publiés repassés en attente (changement important)
 * @param reappeared      lieux archivés réapparus, de nouveau en attente
 * @param archived        lieux disparus de la source, archivés
 * @param archiveWithheld lieux disparus non archivés (disparition massive jugée suspecte)
 * @param rejectedKept    lieux refusés retrouvés dans la source, laissés refusés
 */
public record PlaceImportReport(
        Long sourceId,
        String sourceName,
        boolean dryRun,
        Verdict verdict,
        int collected,
        int whitelisted,
        int created,
        int updated,
        int backToPending,
        int reappeared,
        int archived,
        int archiveWithheld,
        int rejectedKept,
        List<String> problems) {

    public enum Verdict {
        OK,
        SUSPICIOUS,
        FAILED
    }

    static PlaceImportReport failed(Long sourceId, String sourceName, boolean dryRun, String problem) {
        return new PlaceImportReport(sourceId, sourceName, dryRun, Verdict.FAILED, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(problem));
    }
}
