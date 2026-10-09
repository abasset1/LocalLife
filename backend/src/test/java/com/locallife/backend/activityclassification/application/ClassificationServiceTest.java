package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.activityclassification.domain.ClassificationRecord;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.activityclassification.infrastructure.CategoryKeywordRepository;
import com.locallife.backend.activityclassification.infrastructure.ClassificationRecordRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CAT-008 / issues #45, #50 — référentiel unique (keywords source et titre comparés à {@code category_keyword}),
 * résolution code → id, jamais de création de catégorie, consignation des cas non classés.
 */
@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 0, 0);

    /** Ids des catégories du référentiel de test. */
    private static final Map<String, Long> IDS = Map.of(
            "MARKETS", 1L, "CULTURE", 3L, "GASTRONOMY", 4L, "SPORT", 5L, "TOURISM", 8L, "EMPLOYMENT", 10L);

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ClassificationRecordRepository recordRepository;

    @Mock
    private CategoryKeywordRepository keywordRepository;

    @InjectMocks
    private ClassificationService service;

    private static Category category(Long id, String code, boolean active) {
        return new Category(id, code, active, NOW, NOW, Set.of());
    }

    private static CategoryKeyword keyword(String code, String keyword) {
        return keyword(code, keyword, CategoryKeyword.SCOPE_ALL);
    }

    private static CategoryKeyword keyword(String code, String keyword, String scope) {
        return new CategoryKeyword(null, IDS.get(code), keyword, scope, true, NOW, NOW);
    }

    /** Référentiel de test : mots-clés + catégories actives (stubs lenient, tous les tests ne lisent pas tout). */
    private void givenReferential(CategoryKeyword... keywords) {
        lenient().when(keywordRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(keywords));
        IDS.forEach((code, id) -> {
            lenient().when(categoryRepository.findById(id)).thenReturn(Optional.of(category(id, code, true)));
            lenient().when(categoryRepository.findByCode(code)).thenReturn(Optional.of(category(id, code, true)));
        });
    }

    private void givenDefaultReferential() {
        givenReferential(
                keyword("CULTURE", "théâtre"),
                keyword("CULTURE", "concert"),
                keyword("SPORT", "sport"),
                keyword("MARKETS", "marché"),
                keyword("MARKETS", "vide-grenier"),
                keyword("GASTRONOMY", "dégustation"),
                keyword("CULTURE", "patrimoine"),
                keyword("TOURISM", "patrimoine"),
                keyword("EMPLOYMENT", "recrutement"),
                keyword("EMPLOYMENT", "formation", CategoryKeyword.SCOPE_SOURCE));
    }

    // ---- Keywords source : même référentiel que le titre ----

    // --- Catégories par défaut de la source ---

    @Test
    void sourceDefaultClassifiesWithTheActiveCategoriesOfTheSource() {
        givenDefaultReferential();

        ClassificationOutcome outcome = service.sourceDefault(List.of(3L, 8L));

        assertTrue(outcome.classified());
        assertEquals(List.of("CULTURE", "TOURISM"), outcome.decision().categoryCodes());
        assertEquals(List.of(3L, 8L), outcome.categoryIds());
        assertEquals("SOURCE_DEFAULT", outcome.decision().source());
        assertEquals("SOURCE_DEFAULT:CULTURE+TOURISM", outcome.decision().ruleId());
        assertTrue(outcome.decision().matchedKeywords().isEmpty());
    }

    @Test
    void sourceDefaultIgnoresInactiveOrMissingCategories_AndDoesNotClassifyWhenNoneIsLeft() {
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category(3L, "CULTURE", false)));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        ClassificationOutcome outcome = service.sourceDefault(List.of(3L, 99L));

        assertFalse(outcome.classified());
        assertTrue(outcome.categoryIds().isEmpty());
    }

    @Test
    void sourceDefaultDoesNotClassifyWithoutAnyDefaultCategory() {
        assertFalse(service.sourceDefault(List.of()).classified());
    }

    // --- Lot de décisions (beginBatch / endBatch) ---

    @Test
    void batchReadsTheReferentialOnce_AndDecidesLikeWithoutBatch() {
        givenDefaultReferential();
        when(categoryRepository.findAll()).thenReturn(
                IDS.entrySet().stream().map(entry -> category(entry.getValue(), entry.getKey(), true)).toList());

        service.beginBatch();
        ClassificationOutcome fromSource = service.decide("Sans importance", "Concert", List.of("patrimoine"));
        ClassificationOutcome fromTitle = service.decide("Grand marché de Noël", null, List.of());
        service.endBatch();

        assertEquals(List.of("CULTURE", "TOURISM"), fromSource.decision().categoryCodes());
        assertEquals(List.of(3L, 8L), fromSource.categoryIds());
        assertEquals(List.of("MARKETS"), fromTitle.decision().categoryCodes());
        // Une lecture des mots-clés et une des catégories pour tout le lot, aucune par décision.
        verify(keywordRepository, times(1)).findByActiveTrueOrderByIdAsc();
        verify(categoryRepository, times(1)).findAll();
        verify(categoryRepository, never()).findById(any());
        verify(categoryRepository, never()).findByCode(any());
    }

    @Test
    void afterEndBatch_TheReferentialIsReadAgain() {
        givenDefaultReferential();
        when(categoryRepository.findAll()).thenReturn(List.of());

        service.beginBatch();
        service.endBatch();
        ClassificationOutcome outcome = service.decide("Sans importance", "Concert", List.of());

        assertEquals(List.of("CULTURE"), outcome.decision().categoryCodes());
        // Hors lot : un mot-clé modifié en base est pris en compte dès la décision suivante.
        verify(keywordRepository, times(2)).findByActiveTrueOrderByIdAsc();
    }

    @Test
    void sourceKeywordIsResolvedToTheExistingCategoryId() {
        givenDefaultReferential();

        ClassificationOutcome outcome = service.decide("théâtre", List.of());

        assertTrue(outcome.classified());
        assertEquals(List.of(3L), outcome.categoryIds());
        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, outcome.decision().status());
        assertEquals(ActivityClassifier.SOURCE, outcome.decision().source());
        assertEquals(List.of("théâtre"), outcome.decision().matchedKeywords());
    }

    @Test
    void sourceKeywordsUseTheSourceScopeToo() {
        givenDefaultReferential();

        assertEquals(List.of(10L), service.decide("Formation", List.of()).categoryIds());
    }

    @Test
    void decideKeepsAllSourceCategoriesAndResolvesEachOne() {
        givenDefaultReferential();

        ClassificationOutcome outcome = service.decide("concert", List.of("sport", "Journées du patrimoine"));

        assertTrue(outcome.classified());
        assertEquals(List.of(3L, 5L, 8L), outcome.categoryIds());
        assertEquals(List.of("CULTURE", "SPORT", "TOURISM"), outcome.decision().categoryCodes());
    }

    @Test
    void sourceKeywordsWithoutAnyMatchNeedReview() {
        givenDefaultReferential();

        ClassificationOutcome outcome = service.decide("Jeune public", List.of("zzz-inconnu"));

        assertFalse(outcome.classified());
        assertEquals(ClassificationStatus.NEEDS_REVIEW, outcome.decision().status());
        assertEquals(ActivityClassifier.UNRECOGNIZED, outcome.decision().reason());
    }

    @Test
    void noSourceKeywordDoesNotQueryTheReferential() {
        ClassificationOutcome outcome = service.decide(null, Arrays.asList(" ", null));

        assertEquals(ClassificationStatus.UNCLASSIFIED, outcome.decision().status());
        verifyNoInteractions(keywordRepository, categoryRepository);
    }

    @Test
    void decideNeverCreatesACategoryWhenTheCodeIsMissingFromTheReferential() {
        givenDefaultReferential();
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.empty());

        ClassificationOutcome outcome = service.decide("théâtre", List.of());

        assertFalse(outcome.classified());
        assertEquals(List.of(), outcome.categoryIds());
        assertEquals(ClassificationStatus.NEEDS_REVIEW, outcome.decision().status());
        assertEquals(ClassificationService.CATEGORY_NOT_IN_REFERENTIAL, outcome.decision().reason());
        assertEquals(List.of("CULTURE"), outcome.decision().candidateCodes());
    }

    @Test
    void decideDropsAMissingOrInactiveCategoryButKeepsTheOthers() {
        givenDefaultReferential();
        when(categoryRepository.findByCode("SPORT")).thenReturn(Optional.of(category(5L, "SPORT", false)));

        ClassificationOutcome outcome = service.decide("concert", List.of("sport"));

        assertTrue(outcome.classified());
        assertEquals(List.of(3L), outcome.categoryIds());
        assertEquals(List.of("CULTURE"), outcome.decision().categoryCodes());
    }

    @Test
    void decideNeedsReviewWhenNoCategoryCanBeResolved() {
        givenDefaultReferential();
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.empty());
        when(categoryRepository.findByCode("SPORT")).thenReturn(Optional.of(category(5L, "SPORT", false)));

        ClassificationOutcome outcome = service.decide("concert", List.of("sport"));

        assertFalse(outcome.classified());
        assertEquals(ClassificationService.CATEGORY_NOT_IN_REFERENTIAL, outcome.decision().reason());
        assertEquals(List.of("CULTURE", "SPORT"), outcome.decision().candidateCodes());
    }

    // ---- Fallback titre ----

    @Test
    void titleFallbackClassifiesWhenSourceKeywordsAreNull() {
        givenDefaultReferential();

        ClassificationOutcome outcome = service.decide("Grand marché de Noël", null, null);

        assertTrue(outcome.classified());
        assertEquals(List.of(1L), outcome.categoryIds());
        assertEquals(TitleKeywordClassifier.SOURCE, outcome.decision().source());
        assertEquals(List.of("marché"), outcome.decision().matchedKeywords());
    }

    @Test
    void titleFallbackClassifiesWhenSourceKeywordsAreEmptyOrBlank() {
        givenDefaultReferential();

        assertTrue(service.decide("Grand marché de Noël", "  ", List.of()).classified());
        assertTrue(service.decide("Grand marché de Noël", null, Arrays.asList(null, " ", "")).classified());
    }

    @Test
    void titleIgnoresSourceScopeKeywords() {
        givenDefaultReferential();

        // « formation » est de portée SOURCE : trop ambigu dans un titre libre.
        ClassificationOutcome outcome = service.decide("Formation aux premiers secours", null, List.of());

        assertFalse(outcome.classified());
        assertEquals(ClassificationStatus.UNCLASSIFIED, outcome.decision().status());
    }

    @Test
    void titleIsNeverReadWhenSourceKeywordsArePresent() {
        givenDefaultReferential();

        ClassificationOutcome outcome = service.decide("Grand marché de Noël", "théâtre", List.of());

        assertEquals(List.of(3L), outcome.categoryIds());
        assertEquals(ActivityClassifier.SOURCE, outcome.decision().source());
        // Même décision que la logique source seule.
        assertEquals(service.decide("théâtre", List.of()).decision(), outcome.decision());
    }

    @Test
    void unrecognisedSourceKeywordsDoNotFallBackToTheTitle() {
        givenDefaultReferential();

        ClassificationOutcome outcome = service.decide("Grand marché de Noël", "zzz-inconnu", List.of());

        assertEquals(ClassificationStatus.NEEDS_REVIEW, outcome.decision().status());
        assertEquals(List.of(), outcome.decision().categoryCodes());
    }

    @Test
    void titleKeywordsFromSeveralCategoriesAreAllResolved() {
        givenDefaultReferential();

        ClassificationOutcome outcome =
                service.decide("Marché gourmand avec dégustation et concert", null, List.of());

        assertTrue(outcome.classified());
        assertNull(outcome.decision().reason());
        assertEquals(List.of(3L, 1L, 4L), outcome.categoryIds());
        assertEquals(List.of("CULTURE", "MARKETS", "GASTRONOMY"), outcome.decision().categoryCodes());
    }

    @Test
    void titleWithoutMatchIsUnclassifiedNoSignal() {
        givenDefaultReferential();

        ClassificationOutcome outcome = service.decide("Marche nordique", null, List.of());

        assertFalse(outcome.classified());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", outcome.decision().ruleId());
        assertNull(outcome.decision().source());
    }

    @Test
    void blankTitleDoesNotQueryTheKeywordTable() {
        ClassificationOutcome outcome = service.decide("  ", null, List.of());

        assertEquals("UNCLASSIFIED:NO_SIGNAL", outcome.decision().ruleId());
        verifyNoInteractions(keywordRepository);
    }

    @Test
    void titleKeywordOfAnInactiveCategoryNeedsReviewAndKeepsTheTrace() {
        givenDefaultReferential();
        when(categoryRepository.findByCode("MARKETS")).thenReturn(Optional.of(category(1L, "MARKETS", false)));

        ClassificationOutcome outcome = service.decide("Grand marché de Noël", null, List.of());

        assertFalse(outcome.classified());
        assertEquals(ClassificationService.CATEGORY_NOT_IN_REFERENTIAL, outcome.decision().reason());
        assertEquals(TitleKeywordClassifier.SOURCE, outcome.decision().source());
        assertEquals(List.of("marché"), outcome.decision().matchedKeywords());
    }

    // ---- Consignation (classification_record) ----

    @Test
    void recordQuarantinedCreatesARecordWithoutActivity() {
        givenDefaultReferential();
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.empty());
        ClassificationOutcome outcome = service.decide("Jeune public", List.of("visite", "été"));

        service.recordQuarantined(10L, "k1", "Visite du château", "Jeune public", List.of("visite", "été"), outcome);

        verify(recordRepository).save(argThat(record -> record.id() == null
                && record.activityId() == null
                && record.status() == ClassificationStatus.NEEDS_REVIEW
                && "UNRECOGNIZED".equals(record.reason())
                && "REVIEW:UNRECOGNIZED".equals(record.ruleId())
                && "SOURCE_KEYWORD".equals(record.classificationSource())
                && record.categoryCodes() == null
                && "Jeune public".equals(record.sourceCategory())
                && "visite,été".equals(record.sourceTags())));
    }

    @Test
    void recordQuarantinedUpdatesTheExistingRecordOnReimport() {
        givenDefaultReferential();
        LocalDateTime created = LocalDateTime.of(2026, 10, 1, 8, 0);
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1"))
                .thenReturn(Optional.of(new ClassificationRecord(5L, 10L, "k1", null,
                        ClassificationStatus.NEEDS_REVIEW, "REVIEW:UNRECOGNIZED", "UNRECOGNIZED", null, "x", null,
                        "t", created, created, null, null, null)));
        ClassificationOutcome outcome = service.decide("Jeune public", List.of());

        service.recordQuarantined(10L, "k1", "t", "Jeune public", List.of(), outcome);

        verify(recordRepository).save(argThat(record ->
                Long.valueOf(5L).equals(record.id()) && created.equals(record.createdAt())));
    }

    @Test
    void recordClassifiedLinksTheRecordToTheCreatedActivityWithItsSourceTrace() {
        givenDefaultReferential();
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.empty());
        ClassificationOutcome outcome = service.decide("marché", List.of("recrutement"));

        service.recordClassified(10L, "k1", 99L, "Marché de Noël", "marché", List.of("recrutement"), outcome);

        verify(recordRepository).save(argThat(record -> Long.valueOf(99L).equals(record.activityId())
                && record.status() == ClassificationStatus.AUTO_CLASSIFIED
                && "AUTO:MARKETS+EMPLOYMENT".equals(record.ruleId())
                && record.reason() == null
                && record.candidateCategoryCodes() == null
                && "SOURCE_KEYWORD".equals(record.classificationSource())
                && "marché|recrutement".equals(record.matchedKeywords())
                && "MARKETS,EMPLOYMENT".equals(record.categoryCodes())));
    }

    @Test
    void recordClassifiedTracesTheTitleKeywordsAndAllCategories() {
        givenDefaultReferential();
        when(recordRepository.findBySourceIdAndImportKey(10L, "k1")).thenReturn(Optional.empty());
        ClassificationOutcome outcome = service.decide("Marché, vide grenier et dégustation", null, List.of());

        service.recordClassified(10L, "k1", 99L, "Marché, vide grenier et dégustation", null, List.of(), outcome);

        verify(recordRepository).save(argThat(record -> ClassificationStatus.AUTO_CLASSIFIED == record.status()
                && "TITLE_KEYWORD".equals(record.classificationSource())
                && "marché|vide-grenier|dégustation".equals(record.matchedKeywords())
                && "MARKETS,GASTRONOMY".equals(record.categoryCodes())
                && "TITLE_KEYWORD:AUTO:MARKETS+GASTRONOMY".equals(record.ruleId())));
    }

    @Test
    void recordTruncatesValuesLongerThanTheColumns() {
        givenDefaultReferential();
        when(recordRepository.findBySourceIdAndImportKey(any(), any())).thenReturn(Optional.empty());
        String longTitle = "x".repeat(400);
        ClassificationOutcome outcome = service.decide(longTitle, List.of());

        service.recordQuarantined(10L, "k1", longTitle, longTitle, List.of(longTitle, longTitle, longTitle,
                longTitle, longTitle), outcome);

        verify(recordRepository).save(argThat(record -> record.title().length() == 255
                && record.sourceCategory().length() == 255
                && record.sourceTags().length() == 1000));
    }
}
