package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activityclassification.domain.ClassificationRecord;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.activityclassification.infrastructure.CategoryKeywordRepository;
import com.locallife.backend.activityclassification.infrastructure.ClassificationRecordRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** CAT-008 — résolution code → id, jamais de création de catégorie, consignation des cas non classés. */
@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ClassificationRecordRepository recordRepository;

    @Mock
    private CategoryKeywordRepository keywordRepository;

    @InjectMocks
    private ClassificationService service;

    private static Category category(Long id, String code, boolean active) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 0, 0);
        return new Category(id, code, active, now, now, Set.of());
    }

    @Test
    void decideResolvesAutoClassifiedCodeToTheExistingCategoryId() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(category(3L, "CULTURE", true)));

        ClassificationOutcome outcome = service.decide("théâtre", List.of());

        assertTrue(outcome.classified());
        assertEquals(List.of(3L), outcome.categoryIds());
        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, outcome.decision().status());
    }

    @Test
    void decideNeverCreatesACategoryWhenTheCodeIsMissingFromTheReferential() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.empty());

        ClassificationOutcome outcome = service.decide("théâtre", List.of());

        assertFalse(outcome.classified());
        assertEquals(List.of(), outcome.categoryIds());
        assertEquals(ClassificationStatus.NEEDS_REVIEW, outcome.decision().status());
        assertEquals(ClassificationService.CATEGORY_NOT_IN_REFERENTIAL, outcome.decision().reason());
        assertEquals(List.of("CULTURE"), outcome.decision().candidateCodes());
    }

    @Test
    void decideTreatsAnInactiveCategoryAsMissing() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(category(3L, "CULTURE", false)));

        ClassificationOutcome outcome = service.decide("théâtre", List.of());

        assertFalse(outcome.classified());
        assertEquals(ClassificationStatus.NEEDS_REVIEW, outcome.decision().status());
    }

    @Test
    void decideDoesNotTouchTheReferentialWhenNothingIsClassified() {
        ClassificationOutcome outcome = service.decide("Recrutement", List.of());

        assertFalse(outcome.classified());
        assertEquals("EMPLOYMENT_DOMAIN", outcome.decision().reason());
        org.mockito.Mockito.verifyNoInteractions(categoryRepository);
    }

    @Test
    void recordQuarantinedCreatesARecordWithoutActivity() {
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.empty());
        ClassificationOutcome outcome = new ClassificationOutcome(
                ActivityClassifier.classify("patrimoine", List.of()), List.of());

        service.recordQuarantined(10L, "k1", "Visite du château", "patrimoine", List.of("visite", "été"), outcome);

        verify(recordRepository).save(argThat(record ->
                record.id() == null
                        && record.activityId() == null
                        && record.status() == ClassificationStatus.NEEDS_REVIEW
                        && "AMBIGUOUS_CATEGORY".equals(record.reason())
                        && "CULTURE,TOURISM".equals(record.candidateCategoryCodes())
                        && "REVIEW:AMBIGUOUS_CATEGORY".equals(record.ruleId())
                        && "patrimoine".equals(record.sourceCategory())
                        && "visite,été".equals(record.sourceTags())));
    }

    @Test
    void recordQuarantinedUpdatesTheExistingRecordOnReimport() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 1, 8, 0);
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.of(
                new ClassificationRecord(5L, 10L, "k1", null, ClassificationStatus.NEEDS_REVIEW,
                        "REVIEW:UNRECOGNIZED", "UNRECOGNIZED", null, "x", null, "t", created, created, null, null, null)));
        ClassificationOutcome outcome = new ClassificationOutcome(
                ActivityClassifier.classify("patrimoine", List.of()), List.of());

        service.recordQuarantined(10L, "k1", "t", "patrimoine", List.of(), outcome);

        verify(recordRepository).save(argThat(record ->
                Long.valueOf(5L).equals(record.id()) && created.equals(record.createdAt())));
    }

    @Test
    void recordClassifiedLinksTheRecordToTheCreatedActivity() {
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.empty());
        ClassificationOutcome outcome = new ClassificationOutcome(
                ActivityClassifier.classify("marché", List.of()), List.of(7L));

        service.recordClassified(10L, "k1", 99L, "Marché de Noël", "marché", null, outcome);

        verify(recordRepository).save(argThat(record ->
                Long.valueOf(99L).equals(record.activityId())
                        && record.status() == ClassificationStatus.AUTO_CLASSIFIED
                        && "AUTO:MARKETS".equals(record.ruleId())
                        && record.reason() == null
                        && record.sourceTags() == null
                        && record.candidateCategoryCodes() == null));
    }

    @Test
    void recordTruncatesValuesLongerThanTheColumns() {
        when(recordRepository.findBySourceIdAndImportKey(any(), any())).thenReturn(Optional.empty());
        String longTitle = "x".repeat(400);
        ClassificationOutcome outcome = new ClassificationOutcome(
                ActivityClassifier.classify("patrimoine", List.of()), List.of());

        service.recordQuarantined(10L, "k1", longTitle, longTitle, List.of(longTitle, longTitle, longTitle, longTitle, longTitle), outcome);

        verify(recordRepository).save(argThat(record ->
                record.title().length() == 255 && record.sourceCategory().length() == 255
                        && record.sourceTags().length() == 1000));
    }

    @Test
    void decideKeepsAllSourceCategoriesAndResolvesEachOne() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(category(3L, "CULTURE", true)));
        when(categoryRepository.findByCode("SPORT")).thenReturn(Optional.of(category(5L, "SPORT", true)));

        ClassificationOutcome outcome = service.decide("concert", List.of("sport"));

        assertTrue(outcome.classified());
        assertEquals(List.of(3L, 5L), outcome.categoryIds());
        assertEquals(List.of("CULTURE", "SPORT"), outcome.decision().categoryCodes());
        org.mockito.Mockito.verifyNoInteractions(keywordRepository);
    }

    @Test
    void decideDropsAMissingOrInactiveCategoryButKeepsTheOthers() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(category(3L, "CULTURE", true)));
        when(categoryRepository.findByCode("SPORT")).thenReturn(Optional.of(category(5L, "SPORT", false)));

        ClassificationOutcome outcome = service.decide("concert", List.of("sport"));

        assertTrue(outcome.classified());
        assertEquals(List.of(3L), outcome.categoryIds());
        assertEquals(List.of("CULTURE"), outcome.decision().categoryCodes());
    }

    @Test
    void decideNeedsReviewWhenNoCategoryCanBeResolved() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.empty());
        when(categoryRepository.findByCode("SPORT")).thenReturn(Optional.of(category(5L, "SPORT", false)));

        ClassificationOutcome outcome = service.decide("concert", List.of("sport"));

        assertFalse(outcome.classified());
        assertEquals(ClassificationService.CATEGORY_NOT_IN_REFERENTIAL, outcome.decision().reason());
        assertEquals(List.of("CULTURE", "SPORT"), outcome.decision().candidateCodes());
    }

    // ---- Fallback titre (category_keyword) ----

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 0, 0);

    private static CategoryKeyword keyword(Long categoryId, String keyword) {
        return new CategoryKeyword(null, categoryId, keyword, true, NOW, NOW);
    }

    private void givenKeywords(CategoryKeyword... keywords) {
        when(keywordRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(keywords));
    }

    private void givenCategory(Long id, String code) {
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category(id, code, true)));
    }

    private void givenActiveCode(Long id, String code) {
        when(categoryRepository.findByCode(code)).thenReturn(Optional.of(category(id, code, true)));
    }

    @Test
    void titleFallbackClassifiesWhenSourceKeywordsAreNull() {
        givenKeywords(keyword(1L, "marché"));
        givenCategory(1L, "MARKETS");
        givenActiveCode(1L, "MARKETS");

        ClassificationOutcome outcome = service.decide("Grand marché de Noël", null, null);

        assertTrue(outcome.classified());
        assertEquals(List.of(1L), outcome.categoryIds());
        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, outcome.decision().status());
        assertEquals(TitleKeywordClassifier.SOURCE, outcome.decision().source());
        assertEquals(List.of("marché"), outcome.decision().matchedKeywords());
    }

    @Test
    void titleFallbackClassifiesWhenSourceKeywordsAreEmptyOrBlank() {
        givenKeywords(keyword(1L, "marché"));
        givenCategory(1L, "MARKETS");
        givenActiveCode(1L, "MARKETS");

        assertTrue(service.decide("Grand marché de Noël", "  ", List.of()).classified());
        List<String> blankTags = java.util.Arrays.asList(null, " ", "");
        assertTrue(service.decide("Grand marché de Noël", null, blankTags).classified());
    }

    @Test
    void titleIsNeverReadWhenSourceKeywordsArePresent() {
        givenActiveCode(3L, "CULTURE");

        ClassificationOutcome outcome = service.decide("Marche nordique", "théâtre", List.of());

        assertEquals(List.of(3L), outcome.categoryIds());
        assertNull(outcome.decision().source());
        org.mockito.Mockito.verifyNoInteractions(keywordRepository);
        // Même décision que la logique source seule.
        assertEquals(service.decide("théâtre", List.of()).decision(), outcome.decision());
    }

    @Test
    void sourceTagsAloneAlsoDisableTheTitleFallback() {
        ClassificationOutcome outcome = service.decide("Grand marché de Noël", null, List.of("patrimoine"));

        assertEquals(ActivityClassifier.classify(null, List.of("patrimoine")), outcome.decision());
        org.mockito.Mockito.verifyNoInteractions(keywordRepository);
    }

    @Test
    void unrecognisedSourceKeywordsDoNotFallBackToTheTitle() {
        // Un keyword source présent mais inconnu reste NEEDS_REVIEW (UNRECOGNIZED) : le titre ne le remplace pas.
        ClassificationOutcome outcome = service.decide("Grand marché de Noël", "zzz-inconnu", List.of());

        assertEquals(ClassificationStatus.NEEDS_REVIEW, outcome.decision().status());
        org.mockito.Mockito.verifyNoInteractions(keywordRepository);
    }

    @Test
    void titleKeywordsFromSeveralCategoriesAreAllResolved() {
        givenKeywords(keyword(1L, "marché"), keyword(4L, "dégustation"), keyword(2L, "concert"));
        givenCategory(1L, "MARKETS");
        givenCategory(4L, "GASTRONOMY");
        givenCategory(2L, "CULTURE");
        givenActiveCode(1L, "MARKETS");
        givenActiveCode(4L, "GASTRONOMY");
        givenActiveCode(2L, "CULTURE");

        ClassificationOutcome outcome = service.decide("Marché gourmand avec dégustation et concert", null, List.of());

        assertTrue(outcome.classified());
        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, outcome.decision().status());
        assertNull(outcome.decision().reason());
        assertEquals(List.of(1L, 4L, 2L), outcome.categoryIds());
        assertEquals(List.of("MARKETS", "GASTRONOMY", "CULTURE"), outcome.decision().categoryCodes());
    }

    @Test
    void titleWithoutMatchIsUnclassifiedNoSignal() {
        givenKeywords(keyword(1L, "marché"));
        givenCategory(1L, "MARKETS");

        ClassificationOutcome outcome = service.decide("Marche nordique", null, List.of());

        assertFalse(outcome.classified());
        assertEquals(ClassificationStatus.UNCLASSIFIED, outcome.decision().status());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", outcome.decision().ruleId());
        assertNull(outcome.decision().source());
    }

    @Test
    void blankTitleDoesNotQueryTheKeywordTable() {
        ClassificationOutcome outcome = service.decide("  ", null, List.of());

        assertEquals("UNCLASSIFIED:NO_SIGNAL", outcome.decision().ruleId());
        org.mockito.Mockito.verifyNoInteractions(keywordRepository);
    }

    @Test
    void titleKeywordOfAnInactiveCategoryNeedsReviewAndKeepsTheTrace() {
        givenKeywords(keyword(1L, "marché"));
        givenCategory(1L, "MARKETS");
        when(categoryRepository.findByCode("MARKETS")).thenReturn(Optional.of(category(1L, "MARKETS", false)));

        ClassificationOutcome outcome = service.decide("Grand marché de Noël", null, List.of());

        assertFalse(outcome.classified());
        assertEquals(ClassificationService.CATEGORY_NOT_IN_REFERENTIAL, outcome.decision().reason());
        assertEquals(TitleKeywordClassifier.SOURCE, outcome.decision().source());
        assertEquals(List.of("marché"), outcome.decision().matchedKeywords());
    }

    @Test
    void recordClassifiedTracesTheTitleKeywordsAndAllCategories() {
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.empty());
        givenKeywords(keyword(1L, "marché"), keyword(1L, "vide-grenier"), keyword(4L, "dégustation"));
        givenCategory(1L, "MARKETS");
        givenCategory(4L, "GASTRONOMY");
        givenActiveCode(1L, "MARKETS");
        givenActiveCode(4L, "GASTRONOMY");
        ClassificationOutcome outcome = service.decide("Marché, vide grenier et dégustation", null, List.of());

        service.recordClassified(10L, "k1", 99L, "Marché, vide grenier et dégustation", null, List.of(), outcome);

        verify(recordRepository).save(argThat(record ->
                ClassificationStatus.AUTO_CLASSIFIED == record.status()
                        && "TITLE_KEYWORD".equals(record.classificationSource())
                        && "marché|vide-grenier|dégustation".equals(record.matchedKeywords())
                        && "MARKETS,GASTRONOMY".equals(record.categoryCodes())
                        && "TITLE_KEYWORD:AUTO:MARKETS+GASTRONOMY".equals(record.ruleId())));
    }

    @Test
    void recordForSourceKeywordsHasNoTitleTrace() {
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.empty());
        givenActiveCode(3L, "CULTURE");
        ClassificationOutcome outcome = service.decide("t", "théâtre", List.of());

        service.recordClassified(10L, "k1", 99L, "t", "théâtre", List.of(), outcome);

        verify(recordRepository).save(argThat(record ->
                record.classificationSource() == null && record.matchedKeywords() == null
                        && "CULTURE".equals(record.categoryCodes())));
    }

    @Test
    void recordQuarantinedHasNoCategoryCodes() {
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.empty());
        ClassificationOutcome outcome = service.decide("t", "patrimoine", List.of());

        service.recordQuarantined(10L, "k1", "t", "patrimoine", List.of(), outcome);

        verify(recordRepository).save(argThat(record -> record.categoryCodes() == null));
    }

}
