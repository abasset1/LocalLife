package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.DiscoveredAgenda;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Annuaire des agendas OpenAgenda : liste les agendas <b>officiels</b> via
 * {@code GET /v2/agendas?official=1} (https://developers.openagenda.com/en/agendas/recherche), pour que
 * les sources ne soient plus seulement celles saisies une à une dans l'administration.
 *
 * <p>Tri {@code recentlyAddedEvents.desc} : les agendas qui publient en ce moment d'abord, pour qu'une
 * découverte limitée à quelques dizaines d'agendas ne retienne pas des agendas anciens et vides (le tri
 * par défaut est par identifiant croissant). Pagination par curseur {@code after[]}, 100 agendas par
 * page au plus (maximum de l'API).
 *
 * <p>⚠️ Écrit d'après la documentation, sans appel réel possible depuis l'environnement de développement
 * (clé API indisponible) : paramètres et forme de la réponse sont à confirmer au premier essai. Seuls
 * {@code uid}, {@code title} et {@code slug} sont lus ; tout autre champ est ignoré.
 */
@Component
public class OpenAgendaAgendaDirectory {

    private static final String BASE_URL = "https://api.openagenda.com";

    /** Maximum autorisé par l'API pour {@code size}. */
    private static final int PAGE_SIZE = 100;

    /** Garde-fou contre une pagination qui ne se terminerait pas. */
    private static final int MAX_PAGES = 50;

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public OpenAgendaAgendaDirectory(@Value("${openagenda.api-key:}") String apiKey) {
        this(RestClient.builder(), apiKey);
    }

    /** Constructeur package-privé : {@link RestClient.Builder} lié à un {@code MockRestServiceServer} en test. */
    OpenAgendaAgendaDirectory(RestClient.Builder builder, String apiKey) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
    }

    /**
     * Agendas officiels, les plus actifs d'abord, {@code limit} au plus.
     *
     * @throws CollectorException si la clé API n'est pas configurée ou si l'appel échoue
     */
    public List<DiscoveredAgenda> listOfficialAgendas(int limit) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CollectorException("OPENAGENDA_API_KEY doit être configurée pour découvrir les agendas.", null);
        }

        List<DiscoveredAgenda> agendas = new ArrayList<>();
        List<Object> after = null;
        int page = 0;

        while (agendas.size() < limit && page < MAX_PAGES) {
            List<Object> currentAfter = after;
            int size = Math.min(PAGE_SIZE, limit - agendas.size());
            AgendasResponse response;
            try {
                response = restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/v2/agendas")
                                    .queryParam("key", apiKey)
                                    .queryParam("official", "1")
                                    .queryParam("sort", "recentlyAddedEvents.desc")
                                    .queryParam("size", size);
                            if (currentAfter != null) {
                                for (Object value : currentAfter) {
                                    uriBuilder.queryParam("after[]", value == null ? "null" : String.valueOf(value));
                                }
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .body(AgendasResponse.class);
            } catch (RestClientException exception) {
                throw new CollectorException("Échec de la découverte des agendas OpenAgenda.", exception);
            }

            if (response == null || response.agendas() == null || response.agendas().isEmpty()) {
                break;
            }
            for (Agenda agenda : response.agendas()) {
                if (agenda.uid() != null && agendas.size() < limit) {
                    agendas.add(new DiscoveredAgenda(String.valueOf(agenda.uid()), agenda.title(), agenda.slug()));
                }
            }

            after = response.after();
            page++;
            // Page incomplète : c'était la dernière, même si l'API renvoie encore un curseur.
            if (after == null || after.isEmpty() || response.agendas().size() < size) {
                break;
            }
        }

        return agendas;
    }

    private record AgendasResponse(List<Agenda> agendas, List<Object> after) {
    }

    private record Agenda(Long uid, String title, String slug) {
    }

}
