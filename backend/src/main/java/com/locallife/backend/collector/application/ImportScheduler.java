package com.locallife.backend.collector.application;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Planification automatique de l'import (LL-8005).
 *
 * Execute regulierement le pipeline d'import pour tous les collecteurs
 * configures. Utilise @Scheduled de Spring Boot pour une planification
 * simple et declenchement periodique sans infrastructure externe.
 *
 * <b>Frequence</b> : toutes les heures (configurable via la cron expression).
 * Cette frequence permet de :
 * - capturer les nouveaux evenements publies regulierement ;
 * - mettre a jour les evenements existants ;
 * - archiver les evenements supprimes des sources ;
 * - eviter une charge trop frequente sur l'API OpenAgenda.
 *
 * <b>Journalisation</b> : chaque execution produit un log INFO avec le
 * resultat global (nombre de sources traitees, temps d'execution).
 * Les details par source sont deja journalises par ImportService.
 *
 * <b>Gestion des echecs</b> : les echecs de collecte pour une source
 * sont captures et journalises par ImportService sans interrompre
 * le traitement des autres sources. Une exception inattendue dans ce
 * scheduler est capturee et journalisee au niveau ERROR.
 */
@Component
public class ImportScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportScheduler.class);

    private final ImportService importService;

    public ImportScheduler(ImportService importService) {
        this.importService = importService;
    }

    /**
     * Execute l'import automatique pour tous les collecteurs.
     *
     * Cron expression : "0 0 * * * *" = toutes les heures, a la minute 0.
     * Format : seconde, minute, heure, jour, mois, jour de la semaine
     *
     * Exemples alternatifs :
     * - "0 0 *&#47;6 * * *" = toutes les 6 heures
     * - "0 0 0 * * *" = tous les jours a minuit
     * - "0 0 *&#47;2 * * *" = toutes les 2 heures
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledImport() {
        LocalDateTime startedAt = LocalDateTime.now();
        LOGGER.info("Demarrage de l'import planifie pour tous les collecteurs");

        try {
            List<ImportResult> results = importService.importAll();
            LocalDateTime endedAt = LocalDateTime.now();

            long totalFetched = results.stream().mapToLong(ImportResult::fetched).sum();
            long totalCreated = results.stream().mapToLong(ImportResult::created).sum();
            long totalUpdated = results.stream().mapToLong(ImportResult::updated).sum();
            long totalArchived = results.stream().mapToLong(ImportResult::archived).sum();
            long totalErrors = results.stream().mapToLong(ImportResult::errors).sum();

            LOGGER.info(
                    "Import planifie termine en {}ms - "
                    + "Recapitulatif : {} sources, {} evenements recuperes, {} crees, "
                    + "{} mis a jour, {} archives, {} erreurs",
                    java.time.Duration.between(startedAt, endedAt).toMillis(),
                    results.size(),
                    totalFetched,
                    totalCreated,
                    totalUpdated,
                    totalArchived,
                    totalErrors);

        } catch (Exception exception) {
            LOGGER.error("Erreur inattendue lors de l'import planifie", exception);
        }
    }

}
