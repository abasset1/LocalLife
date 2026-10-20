package com.locallife.backend.collector.application;

import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository;
import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository.CatalogFilter;
import org.springframework.stereotype.Service;

/** Consultation paginée du catalogue des agendas découverts. */
@Service
public class DiscoveredAgendaCatalogService {

    public static final int MAX_PAGE_SIZE = 200;

    private final AgendaDiscoveryRepository repository;

    public DiscoveredAgendaCatalogService(AgendaDiscoveryRepository repository) {
        this.repository = repository;
    }

    /** @throws IllegalArgumentException si {@code page} est négative ou {@code size} hors de 1..{@value #MAX_PAGE_SIZE} */
    public DiscoveredAgendaView search(CatalogFilter filter, String query, Boolean official, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Le paramètre 'page' doit être positif ou nul.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Le paramètre 'size' doit être compris entre 1 et " + MAX_PAGE_SIZE
                    + ".");
        }
        long total = repository.count(filter, query, official);
        var items = repository.search(filter, query, official, Math.multiplyExact(page, size), size).stream()
                .map(entry -> new DiscoveredAgendaView.Item(entry.agendaUid(), entry.title(),
                        AgendaActivationService.publicUrl(entry.slug()), entry.official(), entry.firstSeenAt(),
                        entry.lastSeenAt(), entry.sourceId(), entry.sourceName(), entry.sourceStatus()))
                .toList();
        return new DiscoveredAgendaView(items, total, page, size);
    }
}
