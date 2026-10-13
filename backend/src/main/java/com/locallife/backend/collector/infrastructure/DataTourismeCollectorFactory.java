package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.domain.Source;
import java.net.http.HttpClient;
import java.time.Duration;
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

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    public Collector create(Source source) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new DataTourismeCollector(
                RestClient.builder().requestFactory(requestFactory), source.url(), source.name(),
                source.regionFilter());
    }
}
