package com.locallife.backend.collector.application;

import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository;
import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository.CatalogEntry;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Création de sources pour les agendas du catalogue choisis par un administrateur (AGD-03), séparée de la
 * découverte : seule cette étape rend un agenda collectable par l'import.
 *
 * <p>Règles :
 * <ul>
 *   <li>lot borné et configurable ({@code locallife.discovery.max-activation-batch}) : une demande plus grande
 *       est refusée en entier, jamais tronquée en silence ;</li>
 *   <li>un agenda qui a déjà une source (même {@code agendaUid}), quel qu'en soit le statut, n'est ni recréé ni
 *       modifié : une source désactivée par un administrateur le reste ;</li>
 *   <li>un identifiant absent du catalogue est signalé, pas créé : seul un agenda réellement découvert peut
 *       devenir une source ;</li>
 *   <li>une source créée est de type {@code API}, active, sans filtre région, avec l'URL publique de l'agenda :
 *       elle est collectée à l'import suivant. Aucun import n'est lancé ici.</li>
 * </ul>
 */
@Service
public class AgendaActivationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgendaActivationService.class);

    public static final int DEFAULT_MAX_BATCH = 50;

    private static final String API_SOURCE_TYPE = "API";
    private static final String AGENDA_PUBLIC_URL_PREFIX = "https://openagenda.com/";

    /** Longueur de {@code source.name}. */
    private static final int MAX_NAME_LENGTH = 255;

    private final AgendaDiscoveryRepository repository;
    private final SourceService sourceService;
    private final int maxBatch;

    @Autowired
    public AgendaActivationService(
            AgendaDiscoveryRepository repository,
            SourceService sourceService,
            @Value("${locallife.discovery.max-activation-batch:" + DEFAULT_MAX_BATCH + "}") int maxBatch) {
        if (maxBatch < 1) {
            throw new IllegalArgumentException("locallife.discovery.max-activation-batch doit être positif.");
        }
        this.repository = repository;
        this.sourceService = sourceService;
        this.maxBatch = maxBatch;
    }

    public int maxBatch() {
        return maxBatch;
    }

    /**
     * @throws IllegalArgumentException si la liste est vide ou dépasse {@link #maxBatch()} (après dédoublonnage)
     */
    public AgendaActivationResult createSources(List<String> agendaUids) {
        Set<String> requested = new LinkedHashSet<>();
        if (agendaUids != null) {
            agendaUids.stream().filter(uid -> uid != null && !uid.isBlank()).map(String::trim)
                    .forEach(requested::add);
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("Aucun agenda sélectionné.");
        }
        if (requested.size() > maxBatch) {
            throw new IllegalArgumentException(requested.size() + " agendas sélectionnés : au plus " + maxBatch
                    + " par lot (locallife.discovery.max-activation-batch). Aucune source créée.");
        }

        Map<String, CatalogEntry> catalog = repository.findByUids(requested).stream()
                .collect(Collectors.toMap(CatalogEntry::agendaUid, Function.identity()));
        Set<String> knownAgendaUids = sourceService.getAllSources().stream()
                .map(Source::agendaUid)
                .filter(uid -> uid != null && !uid.isBlank())
                .collect(Collectors.toSet());

        List<AgendaActivationResult.CreatedSource> created = new ArrayList<>();
        List<String> alreadyKnown = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (String uid : requested) {
            CatalogEntry entry = catalog.get(uid);
            if (entry == null) {
                notFound.add(uid);
            } else if (knownAgendaUids.contains(uid)) {
                alreadyKnown.add(uid);
            } else {
                Source source = sourceService.createSource(
                        sourceName(entry), API_SOURCE_TYPE, publicUrl(entry), uid, null);
                created.add(new AgendaActivationResult.CreatedSource(source.id(), source.name(), uid));
            }
        }
        LOGGER.info("Création de sources depuis le catalogue : {} demandées, {} créées, {} déjà connues, "
                + "{} hors catalogue", requested.size(), created.size(), alreadyKnown.size(), notFound.size());
        return new AgendaActivationResult(requested.size(), List.copyOf(created), List.copyOf(alreadyKnown),
                List.copyOf(notFound));
    }

    private static String sourceName(CatalogEntry entry) {
        String name = entry.title() == null || entry.title().isBlank()
                ? "Agenda OpenAgenda " + entry.agendaUid()
                : entry.title().trim();
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    static String publicUrl(String slug) {
        return slug == null || slug.isBlank() ? null : AGENDA_PUBLIC_URL_PREFIX + slug;
    }

    private static String publicUrl(CatalogEntry entry) {
        return publicUrl(entry.slug());
    }
}
