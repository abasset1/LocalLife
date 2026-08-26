package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.collector.domain.Collector;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Vérifie l'écart trouvé/corrigé pendant LL-8009 : un agenda dont l'uid
 * est configuré doit produire un {@code Collector} distinct, et un
 * agenda non configuré (uid vide) ne doit pas en produire un (pour ne
 * pas ajouter un collecteur voué à échouer à chaque import).
 */
class OpenAgendaSourcesConfigTest {

    private final OpenAgendaSourcesConfig config = new OpenAgendaSourcesConfig();

    @Test
    void openAgendaCollectors_ShouldRegisterOneCollectorPerConfiguredAgenda() {
        List<Collector> collectors = config.openAgendaCollectors(
                "api-key",
                "",
                "86244142",
                "OpenAgenda Ministere culture",
                "79839448",
                "OpenAgenda Ville d'Avignon",
                "",
                "OpenAgenda Avignon Spectacles",
                "",
                "OpenAgenda Avignon Patrimoine",
                "",
                "OpenAgenda Avignon Loisirs");

        // Seuls les deux agendas avec un uid non vide (defaut + Avignon culture)
        // doivent produire un collecteur ; spectacles/patrimoine/loisirs (uid vide) non.
        assertEquals(2, collectors.size());
        Set<String> sourceNames =
                collectors.stream().map(Collector::getSourceName).collect(Collectors.toSet());
        assertEquals(Set.of("OpenAgenda Ministere culture", "OpenAgenda Ville d'Avignon"), sourceNames);
    }

    @Test
    void openAgendaCollectors_ShouldRegisterNothing_WhenAllAgendaUidsBlank() {
        List<Collector> collectors =
                config.openAgendaCollectors("api-key", "", "", "OpenAgenda", "", "A", "", "B", "", "C", "", "D");

        assertTrue(collectors.isEmpty());
    }

    @Test
    void openAgendaCollectors_ShouldRegisterAllFour_WhenAllAvignonAgendasConfigured() {
        List<Collector> collectors = config.openAgendaCollectors(
                "api-key",
                "",
                "86244142",
                "OpenAgenda Ministere culture",
                "79839448",
                "OpenAgenda Ville d'Avignon",
                "11111111",
                "OpenAgenda Avignon Spectacles",
                "22222222",
                "OpenAgenda Avignon Patrimoine",
                "33333333",
                "OpenAgenda Avignon Loisirs");

        assertEquals(5, collectors.size());
    }
}
