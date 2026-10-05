package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.domain.ClassificationRecord;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.activityclassification.infrastructure.ClassificationRecordRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Classification des éléments issus des sources (CAT-008, SPRINT_CATEG).
 *
 * <p>La décision est prise par {@link ActivityClassifier} (règles pures) ;
 * ce service résout le code V1 obtenu vers l'id de la catégorie existante et
 * consigne le résultat ({@code classification_record}, V38). Il ne crée
 * jamais de catégorie : un code absent ou inactif dans le référentiel donne
 * {@code NEEDS_REVIEW}, jamais une catégorie créée à la volée.
 */
@Service
public class ClassificationService {

    /** Raison de {@code NEEDS_REVIEW} : le classifieur a produit un code que le référentiel ne contient pas. */
    public static final String CATEGORY_NOT_IN_REFERENTIAL = "CATEGORY_NOT_IN_REFERENTIAL";

    private final CategoryRepository categoryRepository;
    private final ClassificationRecordRepository recordRepository;

    public ClassificationService(
            CategoryRepository categoryRepository, ClassificationRecordRepository recordRepository) {
        this.categoryRepository = categoryRepository;
        this.recordRepository = recordRepository;
    }

    /** Décide sans rien persister. */
    public ClassificationOutcome decide(String sourceCategory, List<String> sourceTags) {
        Decision decision = ActivityClassifier.classify(sourceCategory, sourceTags);
        if (decision.status() != ClassificationStatus.AUTO_CLASSIFIED) {
            return new ClassificationOutcome(decision, null);
        }
        Optional<Category> category = categoryRepository.findByCode(decision.categoryCode())
                .filter(Category::active);
        if (category.isEmpty()) {
            Decision unresolved = new Decision(
                    ClassificationStatus.NEEDS_REVIEW, null, CATEGORY_NOT_IN_REFERENTIAL,
                    List.of(decision.categoryCode()), "REVIEW:" + CATEGORY_NOT_IN_REFERENTIAL);
            return new ClassificationOutcome(unresolved, null);
        }
        return new ClassificationOutcome(decision, category.get().id());
    }

    /** Consigne qu'une activité a été créée à partir d'une classification automatique. */
    public void recordClassified(
            Long sourceId, String importKey, Long activityId, String title, String sourceCategory,
            ClassificationOutcome outcome) {
        upsert(sourceId, importKey, activityId, title, sourceCategory, outcome.decision());
    }

    /**
     * Consigne un élément non classé (aucune activité créée) pour relecture.
     * Mis à jour à chaque ré-import tant que l'élément n'est pas classé.
     */
    public void recordQuarantined(
            Long sourceId, String importKey, String title, String sourceCategory, ClassificationOutcome outcome) {
        upsert(sourceId, importKey, null, title, sourceCategory, outcome.decision());
    }

    private void upsert(
            Long sourceId, String importKey, Long activityId, String title, String sourceCategory,
            Decision decision) {
        Optional<ClassificationRecord> existing = recordRepository.findBySourceIdAndImportKey(sourceId, importKey);
        LocalDateTime now = LocalDateTime.now();
        String candidates = decision.candidateCodes().isEmpty() ? null : String.join(",", decision.candidateCodes());
        recordRepository.save(new ClassificationRecord(
                existing.map(ClassificationRecord::id).orElse(null),
                sourceId, importKey, activityId, decision.status(), decision.ruleId(), decision.reason(),
                candidates, truncate(sourceCategory), truncate(title),
                existing.map(ClassificationRecord::createdAt).orElse(now), now));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 255 ? value : value.substring(0, 255);
    }
}
