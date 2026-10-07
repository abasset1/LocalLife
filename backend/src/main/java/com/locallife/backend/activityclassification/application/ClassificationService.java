package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.activityclassification.domain.ClassificationRecord;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.activityclassification.infrastructure.CategoryKeywordRepository;
import com.locallife.backend.activityclassification.infrastructure.ClassificationRecordRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Classification des éléments issus des sources (CAT-008, SPRINT_CATEG).
 *
 * <p>La décision est prise par {@link ActivityClassifier} (règles pures) ;
 * ce service résout le code V1 obtenu vers l'id de la catégorie existante et
 * consigne le résultat ({@code classification_record}, V38). Il ne crée
 * jamais de catégorie : un code absent ou inactif dans le référentiel donne
 * {@code NEEDS_REVIEW}, jamais une catégorie créée à la volée.
 *
 * <p><b>Fallback titre</b> ({@link #decide(String, String, List)}) : uniquement si la source ne fournit
 * aucun keyword (ni catégorie d'origine, ni tag non blanc) ; les mots-clés viennent de
 * {@code category_keyword}. Si la source fournit des keywords, la logique est strictement celle de
 * {@link #decide(String, List)} : le titre n'est pas lu.
 */
@Service
public class ClassificationService {

    /** Raison de {@code NEEDS_REVIEW} : le classifieur a produit un code que le référentiel ne contient pas. */
    public static final String CATEGORY_NOT_IN_REFERENTIAL = "CATEGORY_NOT_IN_REFERENTIAL";

    private final CategoryRepository categoryRepository;
    private final ClassificationRecordRepository recordRepository;
    private final CategoryKeywordRepository keywordRepository;

    public ClassificationService(
            CategoryRepository categoryRepository, ClassificationRecordRepository recordRepository,
            CategoryKeywordRepository keywordRepository) {
        this.categoryRepository = categoryRepository;
        this.recordRepository = recordRepository;
        this.keywordRepository = keywordRepository;
    }

    /**
     * Décide à partir des seuls keywords source, sans rien persister. Comportement historique
     * (CAT-008), inchangé : le titre n'est pas utilisé.
     */
    public ClassificationOutcome decide(String sourceCategory, List<String> sourceTags) {
        return resolve(ActivityClassifier.classify(sourceCategory, sourceTags));
    }

    /**
     * Décide en tenant compte du fallback titre, sans rien persister. Les keywords source (catégorie
     * d'origine ou tag non blanc) ont toujours priorité : s'il y en a, le résultat est exactement celui de
     * {@link #decide(String, List)}. Sinon, le titre est comparé aux mots-clés actifs de
     * {@code category_keyword}.
     */
    public ClassificationOutcome decide(String title, String sourceCategory, List<String> sourceTags) {
        if (hasSourceKeywords(sourceCategory, sourceTags)) {
            return decide(sourceCategory, sourceTags);
        }
        List<KeywordEntry> entries = title == null || title.isBlank() ? List.of() : loadKeywordEntries();
        return resolve(TitleKeywordClassifier.classify(title, entries));
    }

    /**
     * Mêmes signaux que {@link ActivityClassifier#classify} : vrai dès qu'une catégorie d'origine ou un
     * tag non blanc existe. Absent, null, vide ou uniquement blanc → fallback titre.
     */
    static boolean hasSourceKeywords(String sourceCategory, List<String> sourceTags) {
        if (sourceCategory != null && !sourceCategory.isBlank()) {
            return true;
        }
        return sourceTags != null && sourceTags.stream().anyMatch(tag -> tag != null && !tag.isBlank());
    }

    /**
     * Mots-clés actifs avec le code de leur catégorie. Les mots-clés d'une catégorie inactive sont
     * conservés : ils comptent comme candidats (donc visibles en cas de conflit) et une décision
     * finale sur une catégorie inactive donne {@code NEEDS_REVIEW}, jamais une résolution silencieuse.
     */
    private List<KeywordEntry> loadKeywordEntries() {
        Map<Long, Optional<String>> codes = new HashMap<>();
        List<KeywordEntry> entries = new ArrayList<>();
        for (CategoryKeyword keyword : keywordRepository.findByActiveTrueOrderByIdAsc()) {
            Optional<String> code = codes.computeIfAbsent(keyword.categoryId(),
                    id -> categoryRepository.findById(id).map(Category::code));
            code.ifPresent(value -> entries.add(new KeywordEntry(value, keyword.keyword())));
        }
        return entries;
    }

    /** Résout le code V1 d'une décision vers la catégorie existante du référentiel. */
    private ClassificationOutcome resolve(Decision decision) {
        if (decision.status() != ClassificationStatus.AUTO_CLASSIFIED) {
            return new ClassificationOutcome(decision, null);
        }
        Optional<Category> category = categoryRepository.findByCode(decision.categoryCode())
                .filter(Category::active);
        if (category.isEmpty()) {
            Decision unresolved = new Decision(
                    ClassificationStatus.NEEDS_REVIEW, null, CATEGORY_NOT_IN_REFERENTIAL,
                    List.of(decision.categoryCode()), "REVIEW:" + CATEGORY_NOT_IN_REFERENTIAL,
                    decision.source(), decision.matchedKeywords());
            return new ClassificationOutcome(unresolved, null);
        }
        return new ClassificationOutcome(decision, category.get().id());
    }

    /** Consigne qu'une activité a été créée à partir d'une classification automatique. */
    public void recordClassified(
            Long sourceId, String importKey, Long activityId, String title, String sourceCategory,
            List<String> sourceTags, ClassificationOutcome outcome) {
        upsert(sourceId, importKey, activityId, title, sourceCategory, sourceTags, outcome.decision());
    }

    /**
     * Consigne un élément non classé (aucune activité créée) pour relecture.
     * Mis à jour à chaque ré-import tant que l'élément n'est pas classé.
     */
    public void recordQuarantined(
            Long sourceId, String importKey, String title, String sourceCategory, List<String> sourceTags,
            ClassificationOutcome outcome) {
        upsert(sourceId, importKey, null, title, sourceCategory, sourceTags, outcome.decision());
    }

    private void upsert(
            Long sourceId, String importKey, Long activityId, String title, String sourceCategory,
            List<String> sourceTags, Decision decision) {
        Optional<ClassificationRecord> existing = recordRepository.findBySourceIdAndImportKey(sourceId, importKey);
        LocalDateTime now = LocalDateTime.now();
        String candidates = decision.candidateCodes().isEmpty() ? null : String.join(",", decision.candidateCodes());
        recordRepository.save(new ClassificationRecord(
                existing.map(ClassificationRecord::id).orElse(null),
                sourceId, importKey, activityId, decision.status(), decision.ruleId(), decision.reason(),
                candidates, truncate(sourceCategory, 255), joinTags(sourceTags), truncate(title, 255),
                existing.map(ClassificationRecord::createdAt).orElse(now), now,
                decision.source(), joinMatchedKeywords(decision.matchedKeywords())));
    }

    private static String joinMatchedKeywords(List<String> keywords) {
        return keywords == null || keywords.isEmpty() ? null : truncate(String.join("|", keywords), 500);
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream().filter(tag -> tag != null && !tag.isBlank()).collect(Collectors.joining(","));
        return joined.isEmpty() ? null : truncate(joined, 1000);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
