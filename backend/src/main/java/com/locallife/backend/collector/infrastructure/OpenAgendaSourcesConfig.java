package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.Collector;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Enregistre un {@link OpenAgendaCollector} par agenda OpenAgenda
 * réellement configuré (LL-8004/LL-8009).
 *
 * <p><b>Écart trouvé pendant LL-8009</b> (décision go/no-go de la
 * bêta) : LL-8004 avait ajouté les propriétés
 * {@code openagenda.avignon-culture-uid} (et trois placeholders
 * commentés pour spectacles/patrimoine/loisirs) dans
 * {@code application.properties}, mais {@code OpenAgendaCollector}
 * restait un unique {@code @Component} Spring construit à partir de
 * {@code openagenda.agenda-uid} seul — les propriétés {@code
 * avignon-*} n'étaient donc jamais lues par aucun bean, et une seule
 * exécution du collecteur ne portait jamais que sur un seul agenda (la
 * source de démonstration historique {@code openagenda.agenda-uid},
 * pas même un agenda Avignon), en contradiction avec le critère
 * d'acceptation explicite de LL-8004 (« une exécution du collector doit
 * récupérer des activités provenant de plusieurs agendas Avignon »).
 *
 * <p>Cette classe construit directement un {@code List<Collector>} (un
 * {@code OpenAgendaCollector} par agenda dont l'uid n'est pas vide) —
 * {@code ImportService} injecte {@code List<Collector>}, et Spring
 * utilise telle quelle une collection déjà construite par un
 * {@code @Bean} plutôt que d'agréger des beans individuels, donc aucun
 * changement n'est nécessaire côté {@code ImportService}.
 * {@code OpenAgendaCollector} n'est donc plus un {@code @Component}
 * auto-détecté (voir sa Javadoc).
 *
 * <p><b>Limite actuelle</b> : seul {@code openagenda.avignon-culture-uid}
 * porte une vraie valeur (79839448) ; les trois autres agendas Avignon
 * (spectacles/patrimoine/loisirs) restent des placeholders vides tant
 * qu'Alex n'a pas identifié et configuré leurs identifiants réels — une
 * fois fait, aucune modification de code n'est nécessaire : définir les
 * variables d'environnement correspondantes suffit (voir {@code
 * application.properties}).
 */
@Configuration
public class OpenAgendaSourcesConfig {

    @Bean
    public List<Collector> openAgendaCollectors(
            @Value("${openagenda.api-key:}") String apiKey,
            @Value("${OPENAGENDA_REGION_FILTER:}") String regionFilter,
            @Value("${openagenda.agenda-uid:}") String defaultAgendaUid,
            @Value("${openagenda.source-name:OpenAgenda}") String defaultSourceName,
            @Value("${openagenda.avignon-culture-uid:}") String avignonCultureUid,
            @Value("${openagenda.avignon-culture-name:OpenAgenda Avignon Culture}") String avignonCultureName,
            @Value("${openagenda.avignon-spectacles-uid:}") String avignonSpectaclesUid,
            @Value("${openagenda.avignon-spectacles-name:OpenAgenda Avignon Spectacles}")
                    String avignonSpectaclesName,
            @Value("${openagenda.avignon-patrimoine-uid:}") String avignonPatrimoineUid,
            @Value("${openagenda.avignon-patrimoine-name:OpenAgenda Avignon Patrimoine}")
                    String avignonPatrimoineName,
            @Value("${openagenda.avignon-loisirs-uid:}") String avignonLoisirsUid,
            @Value("${openagenda.avignon-loisirs-name:OpenAgenda Avignon Loisirs}")
                    String avignonLoisirsName) {

        List<Collector> collectors = new ArrayList<>();
        addIfConfigured(collectors, apiKey, defaultAgendaUid, defaultSourceName, regionFilter);
        addIfConfigured(collectors, apiKey, avignonCultureUid, avignonCultureName, regionFilter);
        addIfConfigured(collectors, apiKey, avignonSpectaclesUid, avignonSpectaclesName, regionFilter);
        addIfConfigured(collectors, apiKey, avignonPatrimoineUid, avignonPatrimoineName, regionFilter);
        addIfConfigured(collectors, apiKey, avignonLoisirsUid, avignonLoisirsName, regionFilter);
        return collectors;
    }

    /**
     * N'enregistre un collecteur que si son {@code agendaUid} est
     * réellement renseigné — un agenda non configuré (placeholder vide)
     * est silencieusement ignoré plutôt que d'ajouter un collecteur voué
     * à échouer à chaque import ({@link OpenAgendaCollector#collect()}
     * lève sinon une {@code CollectorException} à chaque exécution).
     */
    private void addIfConfigured(
            List<Collector> collectors, String apiKey, String agendaUid, String sourceName, String regionFilter) {
        if (agendaUid != null && !agendaUid.isBlank()) {
            collectors.add(new OpenAgendaCollector(RestClient.builder(), apiKey, agendaUid, sourceName, regionFilter));
        }
    }
}
