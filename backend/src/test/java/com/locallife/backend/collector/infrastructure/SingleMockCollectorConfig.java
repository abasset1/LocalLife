package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Remplace, pour les tests {@code @SpringBootTest} du pipeline d'import,
 * le {@link OpenAgendaCollectorFactory} réel par une version mockée qui
 * renvoie toujours le même {@code Collector} mocké et pilotable, quelle
 * que soit la {@link Source} en base.
 *
 * <p>Depuis LL-EF-005, {@code ImportService} ne reçoit plus une liste de
 * collecteurs fixée au démarrage ({@code OpenAgendaSourcesConfig},
 * supprimée) mais construit un collecteur par {@link Source} collectible
 * trouvée en base, via {@link OpenAgendaCollectorFactory#create(Source)}.
 * Pour isoler les tests d'un appel réseau réel sans avoir à connaître à
 * l'avance quelles sources exact seront présentes en base (l'ordre et le
 * contenu dépendent du jeu de données de chaque test), ce
 * {@code @TestConfiguration} remplace entièrement le factory par un mock
 * dont {@code create(any())} renvoie toujours le même {@link #collector()}
 * — chaque test reste libre de piloter {@code collector.collect()} comme
 * avant LL-EF-005, à condition d'avoir persisté au moins une {@link Source}
 * collectible (type {@code API}, statut {@code ACTIVE}, {@code agendaUid}
 * non vide) pour que {@code ImportService} appelle le factory.
 *
 * <p>Le bean {@code @Primary} ci-dessous prend le pas sur le
 * {@link OpenAgendaCollectorFactory} réel (annoté {@code @Component}) pour
 * l'injection dans {@code ImportService} — le bean réel continue d'exister
 * dans le contexte, mais n'est jamais utilisé grâce à {@code @Primary}.
 *
 * <p><b>Isolation des sources réelles</b> : ce collecteur mocké ne doit
 * jamais être appliqué aux agendas réels présents en base. Le
 * {@code SourceService} est donc lui aussi remplacé
 * ({@link ImportTestSourceService}) : {@code ImportService#importAll()} ne
 * parcourt que les sources créées par le test via
 * {@link ImportTestSourceService#createCollectibleTestSource(String)}.
 */
@TestConfiguration
public class SingleMockCollectorConfig {

    @Bean
    public Collector collector() {
        // collectWithReport() (méthode par défaut) réelle : elle délègue à collect(), stubbé par les tests.
        return Mockito.mock(Collector.class, Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
    }

    @Bean
    @Primary
    public OpenAgendaCollectorFactory testOpenAgendaCollectorFactory(Collector collector) {
        OpenAgendaCollectorFactory factory = Mockito.mock(OpenAgendaCollectorFactory.class);
        Mockito.when(factory.create(Mockito.any())).thenReturn(collector);
        return factory;
    }

    @Bean
    @Primary
    public ImportTestSourceService importTestSourceService(
            SourceRepository sourceRepository, ActivityRepository activityRepository) {
        return new ImportTestSourceService(sourceRepository, activityRepository);
    }
}
