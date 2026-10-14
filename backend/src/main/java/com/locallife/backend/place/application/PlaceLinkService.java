package com.locallife.backend.place.application;

import com.locallife.backend.place.domain.PlaceMatcher;
import com.locallife.backend.place.domain.PlaceMatcher.Level;
import com.locallife.backend.place.infrastructure.PlaceMatchRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lien réversible lieu d'événement → lieu permanent (ADR-0011, § 7.2.9, migration V63), posé par un administrateur
 * à partir du rapport de rapprochement. Jamais automatique, jamais de fusion : seul {@code location.place_id} change.
 *
 * <p>Une paire n'est reliée que si elle est encore candidate (lieu d'événement utilisé, lieu permanent en attente
 * ou publié à 100 m au plus) et que la règle {@link PlaceMatcher} ne la classe pas {@link Level#NONE} : un
 * administrateur peut trancher une paire ambiguë, pas relier deux lieux sans rapport.
 */
@Service
public class PlaceLinkService {

    /** Plafond d'une liaison groupée, comme la modération. */
    public static final int MAX_BULK = PlaceModerationService.MAX_BULK;

    private final PlaceMatchRepository repository;

    public PlaceLinkService(PlaceMatchRepository repository) {
        this.repository = repository;
    }

    public record LinkRequest(Long eventLocationId, Long placeId) {
    }

    public record Refused(Long eventLocationId, Long placeId, String reason) {
    }

    public record LinkResult(List<LinkRequest> linked, List<Refused> refused) {
    }

    @Transactional
    public LinkResult link(List<LinkRequest> requests, Long adminId) {
        if (requests.size() > MAX_BULK) {
            throw new IllegalArgumentException("Au plus " + MAX_BULK + " liaisons à la fois.");
        }
        List<LinkRequest> linked = new ArrayList<>();
        List<Refused> refused = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (LinkRequest request : requests) {
            String reason = refusal(request, seen);
            if (reason == null && !repository.link(request.eventLocationId(), request.placeId(), adminId)) {
                reason = "pas un lieu d'événement";
            }
            if (reason == null) {
                linked.add(request);
            } else {
                refused.add(new Refused(request.eventLocationId(), request.placeId(), reason));
            }
        }
        return new LinkResult(linked, refused);
    }

    @Transactional
    public boolean unlink(Long eventLocationId) {
        return repository.unlink(eventLocationId);
    }

    private String refusal(LinkRequest request, Set<Long> seen) {
        if (request == null || request.eventLocationId() == null || request.placeId() == null) {
            return "'eventLocationId' et 'placeId' obligatoires";
        }
        if (!seen.add(request.eventLocationId())) {
            return "lieu d'événement présent deux fois";
        }
        Optional<PlaceMatchRepository.CandidatePair> pair = repository.findCandidatePair(
                request.eventLocationId(), request.placeId(), PlaceMatcher.MAX_DISTANCE_METERS);
        if (pair.isEmpty()) {
            return "paire non candidate (lieu inutilisé, lieu permanent refusé ou archivé, ou à plus de 100 m)";
        }
        if (PlaceMatcher.compare(pair.get().eventLocation(), pair.get().place()) == Level.NONE) {
            return "hors règle de rapprochement";
        }
        return null;
    }
}
