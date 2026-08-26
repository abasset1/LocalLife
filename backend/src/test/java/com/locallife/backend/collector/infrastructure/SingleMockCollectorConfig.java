package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.Collector;
import java.util.List;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Remplace, pour les tests {@code @SpringBootTest} du pipeline d'import,
 * le {@code List<Collector>} réel produit par {@link OpenAgendaSourcesConfig}
 * par une liste contenant un unique {@code Collector} mocké et pilotable
 * (LL-8009).
 *
 * <p>Avant LL-8009, {@code OpenAgendaCollector} était un unique
 * {@code @Component}, remplaçable directement par
 * {@code @MockitoBean private Collector collector;} (type unique dans le
 * contexte). Depuis que {@link OpenAgendaSourcesConfig} peut enregistrer
 * plusieurs {@code OpenAgendaCollector} réels (un par agenda configuré,
 * potentiellement 2 avec la configuration actuelle : agenda de
 * démonstration + Avignon Culture), remplacer un seul {@code Collector}
 * ne suffit plus à isoler le pipeline d'un appel réseau réel, et les
 * tests qui comptent le nombre d'{@code ImportResult} obtenus (un par
 * collecteur) casseraient dès qu'un deuxième agenda est configuré.
 *
 * <p>Le bean {@code @Primary} ci-dessous prend le pas sur celui de
 * {@link OpenAgendaSourcesConfig} pour l'injection de
 * {@code List<Collector>} dans {@code ImportService} — le bean réel
 * continue d'exister dans le contexte (construction d'objet uniquement,
 * aucun appel réseau tant que {@code collect()} n'est pas invoqué), mais
 * n'est jamais utilisé par {@code ImportService} grâce à {@code @Primary}.
 */
@TestConfiguration
public class SingleMockCollectorConfig {

    @Bean
    public Collector collector() {
        return Mockito.mock(Collector.class);
    }

    @Bean
    @Primary
    public List<Collector> testOpenAgendaCollectors(Collector collector) {
        return List.of(collector);
    }
}
