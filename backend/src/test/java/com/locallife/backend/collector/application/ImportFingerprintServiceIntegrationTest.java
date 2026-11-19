package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Déduplication entre sources sur une base réelle : même fiche DATAtourisme lue par deux sources (fichier
 * Occitanie et source « Languedoc » sur le même fichier), marché découpé par jours dans l'une et continu dans
 * l'autre.
 */
@SpringBootTest
@Transactional
class ImportFingerprintServiceIntegrationTest {

    private static final String MARKET_URI = "https://data.datatourisme.fr/34/marche-de-sete";
    private static final LocalDateTime WEEKLY_START = LocalDateTime.of(2026, 11, 20, 8, 0);
    private static final LocalDateTime YEARLY_START = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    private ImportFingerprintService importFingerprintService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sameDataTourismeUri_IsTheSameElement_EvenWithAnotherTitleOrStartDate() {
        long languedoc = source("ACTIVE");
        long occitanie = source("ACTIVE");
        activity(languedoc, "Marché de Sète", YEARLY_START, MARKET_URI);

        assertThat(importFingerprintService.existsLiveDuplicateInAnotherSource(
                occitanie, MARKET_URI, "Marché hebdomadaire de Sète", WEEKLY_START)).isTrue();
    }

    @Test
    void sameOpenAgendaSlug_StillNeedsTheSameTitleAndStartDate() {
        long first = source("ACTIVE");
        long second = source("ACTIVE");
        activity(first, "Concert", WEEKLY_START, "concert-du-port");

        assertThat(importFingerprintService.existsLiveDuplicateInAnotherSource(
                second, "concert-du-port", "Concert", WEEKLY_START)).isTrue();
        assertThat(importFingerprintService.existsLiveDuplicateInAnotherSource(
                second, "concert-du-port", "Concert", WEEKLY_START.plusDays(7))).isFalse();
    }

    @Test
    void anInactiveSource_NoLongerHoldsItsElements_AndItsCopiesAreArchived() {
        long languedoc = source("ACTIVE");
        long occitanie = source("ACTIVE");
        long oldCopy = activity(languedoc, "Marché de Sète", YEARLY_START, MARKET_URI);
        long newCopy = activity(occitanie, "Marché hebdomadaire de Sète", WEEKLY_START, MARKET_URI);

        // Deux sources actives : la copie la plus récente est le doublon.
        assertThat(importFingerprintService.findDuplicateActivityIds(occitanie)).containsExactly(newCopy);
        assertThat(importFingerprintService.findDuplicateActivityIds(languedoc)).isEmpty();

        // Source « Languedoc » désactivée : la source active prend le relais, l'ancienne copie est à archiver.
        jdbcTemplate.update("UPDATE source SET status = 'INACTIVE' WHERE id = ?", languedoc);
        assertThat(importFingerprintService.existsLiveDuplicateInAnotherSource(
                occitanie, MARKET_URI, "Marché hebdomadaire de Sète", WEEKLY_START)).isFalse();
        assertThat(importFingerprintService.findDuplicateActivityIds(occitanie)).containsExactly(oldCopy);
    }

    private long source(String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO source (name, type, url, status) VALUES (?, 'DATATOURISME', 'https://example.org/f.csv', ?) "
                        + "RETURNING id",
                Long.class, "DATAtourisme " + UUID.randomUUID(), status);
    }

    private long activity(long sourceId, String title, LocalDateTime startDate, String externalId) {
        String importKey = "external:" + externalId + ":" + UUID.randomUUID();
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO activity (title, status, latitude, longitude, start_date, published_at, source_id, "
                        + "import_key) VALUES (?, 'PUBLISHED', 43.40, 3.69, ?, now(), ?, ?) RETURNING id",
                Long.class, title, startDate, sourceId, importKey);
        importFingerprintService.record(sourceId, importKey, "hash", externalId);
        return id;
    }
}
