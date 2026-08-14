package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Premier collecteur réel (LL-5006). Source retenue : l'API officielle
 * OpenAgenda (https://developers.openagenda.com/), sur l'agenda choisi par
 * Alex — décision validée le 14/08/2026 (voir {@code PROJECT_STATUS.md})
 * après comparaison avec Open Data AMP / DATAtourisme : format JSON plus
 * simple à parser, au prix d'une clé API à gérer (pas de secret committé,
 * comme {@code jwt.secret}).
 *
 * Configuration requise (variables d'environnement, valeurs vides par
 * défaut — {@link #collect()} échoue explicitement tant qu'elles ne sont
 * pas renseignées, plutôt que de planter le démarrage de l'application) :
 * <ul>
 *   <li>{@code OPENAGENDA_API_KEY} — clé publique OpenAgenda (compte
 *       gratuit, voir developers.openagenda.com/authentification) ;</li>
 *   <li>{@code OPENAGENDA_AGENDA_UID} — identifiant numérique de l'agenda
 *       ciblé (visible en pied de barre latérale sur openagenda.com une
 *       fois l'agenda choisi) ;</li>
 *   <li>{@code OPENAGENDA_SOURCE_NAME} — optionnel, nom lisible à
 *       utiliser comme {@code Source.name} (voir {@code SOURCE_CONTRACT.md}
 *       et {@code getSourceName()} ci-dessous). Par défaut {@code
 *       "OpenAgenda"}.</li>
 * </ul>
 *
 * ⚠️ Décisions prises pour ce premier collecteur, à valider :
 * <ul>
 *   <li>un seul événement par occurrence à venir ({@code nextTiming}),
 *       pas une entrée par créneau de {@code timings} — un événement
 *       récurrent produit donc une seule {@code CollectedActivity} (sa
 *       prochaine occurrence), pas une par répétition ;</li>
 *   <li>catégorie dérivée du premier mot clé français ({@code
 *       keywords.fr[0]}) — OpenAgenda n'a pas de champ « catégorie »
 *       dédié sur les événements ;</li>
 *   <li>les événements sans lieu physique ({@code location} absent, ex.
 *       événements en ligne) sont ignorés — non pertinents pour une
 *       application de découverte d'activités géolocalisées ;</li>
 *   <li>URL source reconstruite ({@code
 *       https://openagenda.com/agendas/{agendaUid}/events/{slug}}) : la
 *       documentation OpenAgenda ne fournit pas d'URL canonique directe
 *       dans la réponse de lecture des événements.</li>
 * </ul>
 */
@Component
public class OpenAgendaCollector implements Collector {

    private static final String BASE_URL = "https://api.openagenda.com";

    private final RestClient restClient;
    private final String apiKey;
    private final String agendaUid;
    private final String sourceName;

    public OpenAgendaCollector(
            @Value("${openagenda.api-key:}") String apiKey,
            @Value("${openagenda.agenda-uid:}") String agendaUid,
            @Value("${openagenda.source-name:OpenAgenda}") String sourceName) {
        this(RestClient.builder(), apiKey, agendaUid, sourceName);
    }

    /**
     * Constructeur visible package-privé pour les tests : permet d'injecter
     * un {@link RestClient.Builder} lié à un {@code MockRestServiceServer}
     * plutôt que d'appeler la vraie API OpenAgenda, comme
     * {@code GeocodingService}.
     */
    OpenAgendaCollector(RestClient.Builder builder, String apiKey, String agendaUid, String sourceName) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
        this.agendaUid = agendaUid;
        this.sourceName = sourceName;
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public List<CollectedActivity> collect() {
        if (isBlank(apiKey) || isBlank(agendaUid)) {
            throw new CollectorException(
                    "OPENAGENDA_API_KEY et OPENAGENDA_AGENDA_UID doivent être configurés pour collecter.", null);
        }

        OpenAgendaEventsResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/agendas/{agendaUid}/events")
                            .queryParam("key", apiKey)
                            .build(agendaUid))
                    .retrieve()
                    .body(OpenAgendaEventsResponse.class);
        } catch (RestClientException exception) {
            throw new CollectorException("Échec de la collecte depuis OpenAgenda.", exception);
        }

        if (response == null || response.events() == null) {
            return List.of();
        }

        return response.events().stream()
                .filter(event -> event.location() != null)
                .map(this::toCollectedActivity)
                .toList();
    }

    private CollectedActivity toCollectedActivity(OpenAgendaEvent event) {
        OpenAgendaTiming timing = event.nextTiming() != null ? event.nextTiming() : event.lastTiming();
        return new CollectedActivity(
                text(event.title()),
                text(event.description()),
                toLocalDateTime(timing == null ? null : timing.begin()),
                toLocalDateTime(timing == null ? null : timing.end()),
                firstKeyword(event.keywords()),
                event.location().latitude(),
                event.location().longitude(),
                "https://openagenda.com/agendas/" + agendaUid + "/events/" + event.slug(),
                event.slug(),
                sourceName);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Map<String, String> multilingual) {
        return multilingual == null ? null : multilingual.get("fr");
    }

    private String firstKeyword(Map<String, List<String>> keywords) {
        if (keywords == null) {
            return null;
        }
        List<String> frenchKeywords = keywords.get("fr");
        return (frenchKeywords == null || frenchKeywords.isEmpty()) ? null : frenchKeywords.get(0);
    }

    private LocalDateTime toLocalDateTime(String isoOffsetDateTime) {
        return isoOffsetDateTime == null ? null : OffsetDateTime.parse(isoOffsetDateTime).toLocalDateTime();
    }

    /** Sous-ensemble de la réponse JSON OpenAgenda qui nous intéresse. */
    private record OpenAgendaEventsResponse(List<OpenAgendaEvent> events) {
    }

    private record OpenAgendaEvent(
            String slug,
            Map<String, String> title,
            Map<String, String> description,
            Map<String, List<String>> keywords,
            OpenAgendaLocation location,
            OpenAgendaTiming nextTiming,
            OpenAgendaTiming lastTiming) {
    }

    private record OpenAgendaLocation(double latitude, double longitude) {
    }

    private record OpenAgendaTiming(String begin, String end) {
    }

}
