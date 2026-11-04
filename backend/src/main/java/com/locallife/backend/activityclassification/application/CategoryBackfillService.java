package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.infrastructure.CategoryBackfillRepository;
import com.locallife.backend.activityclassification.infrastructure.CategoryBackfillRepository.Candidate;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applique une catégorie aux activités <b>déjà importées</b>, depuis l'administration.
 *
 * <p>À l'import, une activité existante garde ses catégories : un mot-clé ajouté ensuite (« marché de noël »
 * dans une nouvelle catégorie) ne touchait donc que les éléments nouveaux ou en quarantaine. Ce rattrapage
 * re-décide chaque activité à venir avec le référentiel actuel ({@link ClassificationService#decide(String,
 * String, List)}, mêmes règles qu'à l'import, titre et signaux source) et lui <b>ajoute</b> la catégorie si
 * la décision la contient. Rien n'est retiré : les catégories existantes, y compris posées par un humain,
 * sont conservées.
 */
@Service
public class CategoryBackfillService {

    /**
     * @param examined activités à venir qui ne portaient pas la catégorie
     * @param added    activités auxquelles la catégorie a été ajoutée
     */
    public record BackfillResult(String categoryCode, int examined, int added) {
    }

    private final CategoryRepository categoryRepository;
    private final CategoryBackfillRepository backfillRepository;
    private final ClassificationService classificationService;

    public CategoryBackfillService(
            CategoryRepository categoryRepository, CategoryBackfillRepository backfillRepository,
            ClassificationService classificationService) {
        this.categoryRepository = categoryRepository;
        this.backfillRepository = backfillRepository;
        this.classificationService = classificationService;
    }

    /**
     * @return {@code Optional.empty()} si la catégorie n'existe pas
     * @throws IllegalArgumentException si la catégorie est désactivée (la classification ne l'attribue pas)
     */
    @Transactional
    public Optional<BackfillResult> applyToExistingActivities(String categoryCode) {
        Optional<Category> found = categoryRepository.findByCode(categoryCode);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Category category = found.get();
        if (!category.active()) {
            throw new IllegalArgumentException(
                    "La catégorie « " + categoryCode + " » est désactivée : réactivez-la d'abord.");
        }
        List<Candidate> candidates = backfillRepository.findCandidates(category.id());
        List<Long> matching = new ArrayList<>();
        classificationService.beginBatch();
        try {
            for (Candidate candidate : candidates) {
                ClassificationOutcome outcome = classificationService.decide(
                        candidate.title(), candidate.sourceCategory(), candidate.sourceTags());
                if (outcome.classified() && outcome.categoryIds().contains(category.id())) {
                    matching.add(candidate.activityId());
                }
            }
        } finally {
            classificationService.endBatch();
        }
        int added = backfillRepository.addCategory(category.id(), matching);
        return Optional.of(new BackfillResult(categoryCode, candidates.size(), added));
    }
}
