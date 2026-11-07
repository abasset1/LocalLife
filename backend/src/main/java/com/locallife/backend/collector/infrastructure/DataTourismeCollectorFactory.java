package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.domain.Source;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Construit un {@link DataTourismeCollector} à partir d'une {@link Source}
 * de type {@code DATATOURISME}, comme {@link OpenAgendaCollectorFactory}
 * pour OpenAgenda : la source est lue en base à chaque import, aucune
 * configuration au démarrage.
 *
 * <p>Le client HTTP suit les redirections : l'URL stable d'une ressource
 * data.gouv.fr ({@code /api/1/datasets/r/{resourceId}}) répond par une
 * redirection vers le fichier du jour.
 */
@Component
public class DataTourismeCollectorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataTourismeCollectorFactory.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    private final JdbcTemplate jdbcTemplate;
    private final String apiKey;
    /** Lecture des réponses de l'API DATAtourisme (comme ailleurs dans le projet : pas de bean partagé). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataTourismeCollectorFactory(JdbcTemplate jdbcTemplate, @Value("${datatourisme.api-key:}") String apiKey) {
        this.jdbcTemplate = jdbcTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Collecteur d'événements du CSV, complété par l'API DATAtourisme quand une clé est configurée
     * ({@code DATATOURISME_API_KEY}, voir {@link DataTourismeApiEnricher}).
     */
    public Collector create(Source source) {
        DataTourismeCollector collector = new DataTourismeCollector(
                restClientBuilder(), source.url(), source.name(), source.regionFilter(), eventSelection(source));
        DataTourismeApiEnricher enricher = new DataTourismeApiEnricher(restClientBuilder(), apiKey, objectMapper);
        if (!enricher.isEnabled()) {
            LOGGER.info("API DATAtourisme non configurée (DATATOURISME_API_KEY vide) : '{}' importée depuis le CSV "
                    + "seul, sans description détaillée ni tarifs.", source.name());
            return collector;
        }
        return collector.withEnricher(enricher);
    }

    /**
     * Sélection d'événements de la source ({@code source.event_selection}, V77), lue à chaque collecte : colonne
     * hors du modèle {@link Source}, réglée par migration ou en base, comme les catégories par défaut.
     */
    DataTourismeEventSelection eventSelection(Source source) {
        if (source.id() == null) {
            return null;
        }
        List<String> values = jdbcTemplate.queryForList(
                "SELECT event_selection FROM source WHERE id = ?", String.class, source.id());
        return values.isEmpty() ? null : DataTourismeEventSelection.fromStoredValue(values.get(0));
    }

    /** Collecteur des lieux d'une source {@code DATATOURISME_LIEUX} (ADR-0011, phase B), même client HTTP. */
    public DataTourismePlaceCollector createPlaceCollector(Source source) {
        return new DataTourismePlaceCollector(
                restClientBuilder(), source.url(), source.name(), source.regionFilter());
    }

    private static RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
