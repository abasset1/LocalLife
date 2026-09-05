package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.domain.Source;
import org.junit.jupiter.api.Test;

/**
 * Remplace {@code OpenAgendaSourcesConfigTest} (LL-EF-005) : les agendas ne
 * sont plus une liste fixe construite au démarrage à partir de propriétés
 * — le filtrage par agenda « configuré ou non » (uid vide, source
 * inactive) est désormais la responsabilité d'{@code ImportService}, testée
 * dans {@code ImportServiceTest} (voir
 * {@code importAll_ShouldIgnoreNonCollectibleSources}). Cette classe se
 * limite à vérifier que le factory construit correctement un collecteur à
 * partir d'une {@code Source}.
 */
class OpenAgendaCollectorFactoryTest {

    private final OpenAgendaCollectorFactory factory = new OpenAgendaCollectorFactory("api-key");

    @Test
    void create_ShouldReturnCollectorUsingSourceNameAsSourceName() {
        Source source = new Source(
                1L, "OpenAgenda Ville d'Avignon", "API", null, "ACTIVE", null, "79839448", null);

        Collector collector = factory.create(source);

        assertNotNull(collector);
        assertEquals("OpenAgenda Ville d'Avignon", collector.getSourceName());
    }

    @Test
    void create_ShouldReturnDistinctCollectors_ForDifferentSources() {
        Source first = new Source(1L, "Agenda 1", "API", null, "ACTIVE", null, "111", null);
        Source second = new Source(2L, "Agenda 2", "API", null, "ACTIVE", null, "222", null);

        Collector firstCollector = factory.create(first);
        Collector secondCollector = factory.create(second);

        assertEquals("Agenda 1", firstCollector.getSourceName());
        assertEquals("Agenda 2", secondCollector.getSourceName());
    }

}
