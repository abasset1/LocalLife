package com.locallife.backend.collector.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Sélection d'événements DATAtourisme (V77) : source créée par la migration, lecture par la fabrique. */
@SpringBootTest
@Transactional
class DataTourismeEventSelectionIntegrationTest {

    private static final String SOURCE_NAME = "DATAtourisme France — vide-greniers et brocantes (data.gouv.fr)";

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private DataTourismeCollectorFactory factory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Source franceSalesSource() {
        return sourceRepository.findByName(SOURCE_NAME).orElseThrow();
    }

    @Test
    void migration_ShouldCreateTheFranceSalesSource_InactiveWithMarketsByDefault() {
        Source source = franceSalesSource();

        assertThat(source.type()).isEqualTo("DATATOURISME");
        assertThat(source.status()).isEqualTo("INACTIVE");
        assertThat(source.url())
                .isEqualTo("https://www.data.gouv.fr/api/1/datasets/r/2c6023f2-ffce-4c1a-9728-bdc020e463b5");
        assertThat(source.regionFilter()).isNull();
        assertThat(jdbcTemplate.queryForList(
                "SELECT c.code FROM source_default_category d JOIN category c ON c.id = d.category_id "
                        + "WHERE d.source_id = ?", String.class, source.id()))
                .containsExactly("MARKETS");
    }

    @Test
    void factory_ShouldReadTheSelection_AndNoneForOtherSources() {
        assertThat(factory.eventSelection(franceSalesSource()))
                .isEqualTo(DataTourismeEventSelection.VIDE_GRENIERS_BROCANTES);
        Source paca = sourceRepository.findByName("DATAtourisme PACA (data.gouv.fr)").orElseThrow();
        assertThat(factory.eventSelection(paca)).isNull();
        assertThat(factory.eventSelection(new Source(null, "nouvelle", "DATATOURISME", null, "INACTIVE", null, null,
                null))).isNull();
    }

    @Test
    void adminUpdate_ShouldKeepTheSelection() {
        Source source = franceSalesSource();

        sourceService.updateSource(source.id(), source.name() + " (renommée)", source.type(), source.url(),
                source.status(), null, "64,40");

        assertThat(factory.eventSelection(sourceRepository.findById(source.id()).orElseThrow()))
                .isEqualTo(DataTourismeEventSelection.VIDE_GRENIERS_BROCANTES);
    }

    @Test
    void database_ShouldRefuseAnUnknownSelection() {
        Long id = franceSalesSource().id();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE source SET event_selection = 'INCONNUE' WHERE id = ?", id));
    }

    @Test
    void database_ShouldRefuseASelectionOnAnotherSourceType() {
        Long manualId = jdbcTemplate.queryForObject("SELECT id FROM source WHERE type = 'MANUAL'", Long.class);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE source SET event_selection = 'VIDE_GRENIERS_BROCANTES' WHERE id = ?", manualId));
    }
}
