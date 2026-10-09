package com.locallife.backend.collector.application;

import com.locallife.backend.collector.domain.DiscoveredAgenda;
import com.locallife.backend.collector.infrastructure.OpenAgendaAgendaDirectory;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Découverte automatique des agendas OpenAgenda officiels : crée une source collectible pour chaque
 * agenda officiel qui n'en a pas encore, au lieu de les saisir un à un dans l'administration.
 *
 * <p>Règles :
 * <ul>
 *   <li>un agenda déjà présent comme source (même {@code agendaUid}) n'est jamais recréé ni modifié, quel
 *       que soit son statut : désactiver une source découverte suffit donc à l'écarter durablement ;</li>
 *   <li>une source créée est de type {@code API}, active, sans filtre région, avec l'URL publique de
 *       l'agenda : elle est collectée dès l'import suivant ({@link ImportService}), comme une source
 *       saisie à la main ;</li>
 *   <li>la découverte ne lance aucun import et ne supprime rien.</li>
 * </ul>
 *
 * <p>Déclenchée à la demande ({@code POST /api/v1/admin/import/discover-agendas}), avec une limite : le
 * nombre d'agendas officiels se compte en milliers, alors que la déduplication entre sources n'existe pas
 * encore.
 */
@Service
public class AgendaDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgendaDiscoveryService.class);

    private static final String API_SOURCE_TYPE = "API";

    private static final String AGENDA_PUBLIC_URL_PREFIX = "https://openagenda.com/";

    /** Longueur de {@code source.name}. */
    private static final int MAX_NAME_LENGTH = 255;

    private final OpenAgendaAgendaDirectory agendaDirectory;
    private final SourceService sourceService;

    public AgendaDiscoveryService(OpenAgendaAgendaDirectory agendaDirectory, SourceService sourceService) {
        this.agendaDirectory = agendaDirectory;
        this.sourceService = sourceService;
    }

    /**
     * @param limit nombre maximum d'agendas officiels à examiner, les plus actifs d'abord
     */
    public AgendaDiscoveryResult discoverOfficialAgendas(int limit) {
        List<DiscoveredAgenda> agendas = agendaDirectory.listOfficialAgendas(limit);

        Set<String> knownAgendaUids = sourceService.getAllSources().stream()
                .map(Source::agendaUid)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        List<String> createdNames = new ArrayList<>();
        int alreadyKnown = 0;
        for (DiscoveredAgenda agenda : agendas) {
            // add() refuse aussi un agenda renvoyé deux fois par l'API dans la même découverte.
            if (!knownAgendaUids.add(agenda.uid())) {
                alreadyKnown++;
                continue;
            }
            String name = sourceName(agenda);
            sourceService.createSource(name, API_SOURCE_TYPE, publicUrl(agenda), agenda.uid(), null);
            createdNames.add(name);
        }

        LOGGER.info("Découverte des agendas OpenAgenda officiels : {} examinés, {} sources créées, {} déjà connus",
                agendas.size(), createdNames.size(), alreadyKnown);
        return new AgendaDiscoveryResult(agendas.size(), createdNames.size(), alreadyKnown, List.copyOf(createdNames));
    }

    private String sourceName(DiscoveredAgenda agenda) {
        String name = agenda.title() == null || agenda.title().isBlank()
                ? "Agenda OpenAgenda " + agenda.uid()
                : agenda.title().trim();
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    private String publicUrl(DiscoveredAgenda agenda) {
        return agenda.slug() == null || agenda.slug().isBlank() ? null : AGENDA_PUBLIC_URL_PREFIX + agenda.slug();
    }

}
