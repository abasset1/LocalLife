package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.DiscoveredAgenda;
import com.locallife.backend.collector.domain.DiscoveryScope;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Annuaire des agendas OpenAgenda : une page de {@code GET /v2/agendas}
 * (https://developers.openagenda.com/en/agendas/recherche) par appel. Le parcours complet, sa reprise et
 * son bilan sont tenus par {@code AgendaDiscoveryService}, qui enregistre le curseur après chaque page.
 *
 * <p>Périmètre : {@link DiscoveryScope#OFFICIAL} ajoute {@code official=1} ; {@link DiscoveryScope#ALL}
 * n'ajoute aucun filtre. Aucun paramètre de tri n'est envoyé : le tri par défaut de l'API (identifiant
 * croissant, d'après la documentation) est stable pendant un parcours long, alors qu'un tri par activité
 * récente ({@code recentlyAddedEvents.desc}, utilisé auparavant) déplace un agenda qui publie pendant le
 * parcours, qui peut alors être sauté ou lu deux fois. Pagination par curseur {@code after[]}, 100 agendas
 * par page (maximum de l'API).
 *
 * <p>⚠️ Écrit d'après la documentation, sans appel réel possible depuis l'environnement de développement
 * (clé API indisponible) : paramètres, tri par défaut, forme du curseur et champ {@code total} sont à
 * confirmer au premier essai (voir {@code COLLECTOR_OPERATIONS.md}). Seuls {@code uid}, {@code title},
 * {@code slug} et {@code official} sont lus ; tout autre champ est ignoré.
 */
@Component
public class OpenAgendaAgendaDirectory {

    private static final String BASE_URL = "https://api.openagenda.com";

    /** Maximum autorisé par l'API pour {@code size}. */
    public static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final String apiKey;
    private final OpenAgendaRetry retry;

    @Autowired
    public OpenAgendaAgendaDirectory(
            @Value("${openagenda.api-key:}") String apiKey,
            @Value("${openagenda.http.max-attempts:" + OpenAgendaRetry.DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts) {
        this(OpenAgendaErrors.restClientBuilder(), apiKey, OpenAgendaRetry.withDefaults(maxAttempts));
    }

    /** Constructeur package-privé : {@link RestClient.Builder} lié à un {@code MockRestServiceServer} en test. */
    OpenAgendaAgendaDirectory(RestClient.Builder builder, String apiKey) {
        this(builder, apiKey, OpenAgendaRetry.immediate(1));
    }

    OpenAgendaAgendaDirectory(RestClient.Builder builder, String apiKey, OpenAgendaRetry retry) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
        this.retry = retry;
    }

    /** @throws CollectorException si la clé API n'est pas configurée */
    public void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CollectorException("OPENAGENDA_API_KEY doit être configurée pour découvrir les agendas.", null);
        }
    }

    /**
     * Une page de l'annuaire.
     *
     * @param after curseur renvoyé par la page précédente, {@code null} pour la première
     * @throws CollectorException si la clé manque, ou si l'appel échoue après les reprises bornées
     */
    public AgendaPage fetchPage(DiscoveryScope scope, List<Object> after) {
        requireApiKey();
        AgendasResponse response = retry.call("Échec de la découverte des agendas OpenAgenda", () -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v2/agendas")
                            .queryParam("key", apiKey)
                            .queryParam("size", PAGE_SIZE);
                    if (scope == DiscoveryScope.OFFICIAL) {
                        uriBuilder.queryParam("official", "1");
                    }
                    OpenAgendaCursor.append(uriBuilder, after);
                    return uriBuilder.build();
                })
                .retrieve()
                .body(AgendasResponse.class));

        if (response == null || response.agendas() == null) {
            return new AgendaPage(null, null, null);
        }
        List<DiscoveredAgenda> agendas = new ArrayList<>();
        for (Agenda agenda : response.agendas()) {
            agendas.add(new DiscoveredAgenda(
                    agenda.uid() == null ? null : String.valueOf(agenda.uid()),
                    agenda.title(),
                    agenda.slug(),
                    scope == DiscoveryScope.OFFICIAL && agenda.official() == null ? Boolean.TRUE : agenda.official()));
        }
        return new AgendaPage(agendas, response.after(), response.total());
    }

    /**
     * Une page de l'annuaire, telle que reçue.
     *
     * @param agendas agendas de la page, uid {@code null} compris (ignorés par l'appelant) ; {@code null} si la
     *                réponse ne contient pas de tableau {@code agendas} (réponse incohérente)
     * @param after   curseur de la page suivante ; {@code null} ou vide : fin de l'annuaire
     * @param total   nombre total annoncé par l'API, {@code null} s'il n'est pas fourni
     */
    public record AgendaPage(List<DiscoveredAgenda> agendas, List<Object> after, Integer total) {
    }

    private record AgendasResponse(List<Agenda> agendas, List<Object> after, Integer total) {
    }

    private record Agenda(Long uid, String title, String slug, Boolean official) {
    }

}
