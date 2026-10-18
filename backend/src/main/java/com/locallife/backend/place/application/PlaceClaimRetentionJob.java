package com.locallife.backend.place.application;

import com.locallife.backend.place.infrastructure.PlaceClaimRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Conservation des justificatifs de revendication (ADR-0012, décision D4) : le lien déclaré et les justificatifs
 * d'une demande close sont effacés 12 mois après sa clôture. La demande, sa décision et son historique restent.
 */
@Component
public class PlaceClaimRetentionJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceClaimRetentionJob.class);

    static final int RETENTION_MONTHS = 12;

    private final PlaceClaimRepository claimRepository;

    public PlaceClaimRetentionJob(PlaceClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    /** Chaque nuit à 3 h 30. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpiredEvidence() {
        int purged = claimRepository.purgeEvidence(OffsetDateTime.now().minusMonths(RETENTION_MONTHS));
        if (purged > 0) {
            LOGGER.info("Justificatifs de {} demande(s) de gestion effacés (conservation de {} mois).", purged,
                    RETENTION_MONTHS);
        }
    }
}
