package com.locallife.backend.place.application;

import com.locallife.backend.place.domain.Place;
import com.locallife.backend.place.domain.PlaceStatus;
import com.locallife.backend.place.infrastructure.PlaceRepository;
import com.locallife.backend.place.infrastructure.PlaceRepository.ModerationItem;
import com.locallife.backend.place.infrastructure.PlaceRepository.ModerationQuery;
import com.locallife.backend.source.application.SourceService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Modération des lieux permanents par les administrateurs (ADR-0011, phase B, décision du 14/10/2026) et
 * activation vérifiée d'une source de lieux.
 */
@Service
public class PlaceModerationService {

    /** Plafond d'une action groupée, pour garder la transaction et la réponse raisonnables. */
    public static final int MAX_BULK = 500;

    private final PlaceRepository placeRepository;
    private final PlaceImportService placeImportService;
    private final SourceService sourceService;

    public PlaceModerationService(
            PlaceRepository placeRepository, PlaceImportService placeImportService, SourceService sourceService) {
        this.placeRepository = placeRepository;
        this.placeImportService = placeImportService;
        this.sourceService = sourceService;
    }

    /** Page de la file de modération et nombre total de lieux correspondants. */
    public record ModerationPage(List<ModerationItem> items, long total) {
    }

    public ModerationPage find(ModerationQuery query) {
        long[] total = {0};
        List<ModerationItem> items = placeRepository.findForModeration(query, total);
        return new ModerationPage(items, total[0]);
    }

    public Optional<Place> findById(Long id) {
        return placeRepository.findById(id);
    }

    /**
     * Publie ou refuse ; renvoie les ids effectivement modifiés (les transitions non permises sont ignorées).
     *
     * @throws IllegalArgumentException cible autre que {@code PUBLISHED}/{@code REJECTED}, ou trop d'ids
     */
    public List<Long> moderate(Collection<Long> ids, PlaceStatus target, Long adminId) {
        if (target != PlaceStatus.PUBLISHED && target != PlaceStatus.REJECTED) {
            throw new IllegalArgumentException("Action de modération inconnue : publier ou refuser.");
        }
        if (ids.size() > MAX_BULK) {
            throw new IllegalArgumentException("Au plus " + MAX_BULK + " lieux par action groupée.");
        }
        return placeRepository.moderate(ids, target, adminId);
    }

    /**
     * Active une source de lieux après un essai à blanc réussi ; renvoie le bilan de l'essai (la source n'est
     * activée que si son verdict est {@code OK}). Vide si la source n'existe pas ou n'est pas une source de lieux.
     */
    public Optional<PlaceImportReport> activate(Long sourceId) {
        Optional<PlaceImportReport> report = placeImportService.run(sourceId, true);
        report.filter(r -> r.verdict() == PlaceImportReport.Verdict.OK)
                .ifPresent(r -> sourceService.activateAfterVerification(sourceId));
        return report;
    }
}
