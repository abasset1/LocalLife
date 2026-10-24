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
 * ce service résout les codes V1 obtenus vers les ids des catégories existantes et
 * consigne le résultat ({@code classification_record}, V38). Il ne crée
 * jamais de catégorie : un code absent ou inactif dans le référentiel est
 * écarté, et si aucun code n'est résolu le résultat est {@code NEEDS_REVIEW},
 * jamais une catégorie créée à la volée.
 *
 * <p><b>Fallback titre</b> ({@link #decide(String, String, List)}) : si la source ne fournit aucun keyword
 * (ni catégorie d'origine, ni tag non blanc), ou si aucun de ses keywords n'est reconnu ; les mots-clés
 * viennent de {@code category_keyword}. Des keywords source reconnus décident seuls, exactement comme
 * {@link #decide(String, List)} : le titre n'est alors pas lu.
 *
 * <p><b>1..N catégories</b> (issue #45) : toutes les catégories détectées sont conservées, sans
 * principale ni secondaire, sans score ni arbitrage ; plusieurs catégories ne sont jamais un conflit.
 *
 * <p><b>Référentiel unique</b> (issue #50) : keywords source et titre sont comparés au même référentiel en
 * base ({@code category_keyword}), avec la même règle ({@link KeywordCategoryMatcher}). Seule différence :
 * les mots-clés de portée {@code SOURCE} ne s'appliquent qu'aux keywords source.
 */
@Service
public class ClassificationService {

    /** Raison de {@code NEEDS_REVIEW} : le classifieur a produit un code que le référentiel ne contient pas. */
    public static final String CATEGORY_NOT_IN_REFERENTIAL = "CATEGORY_NOT_IN_REFERENTIAL";

    /**
     * Origine tracée dans {@code classification_record.classification_source} quand la catégorie vient
     * des catégories par défaut de la source (voir {@link #sourceDefault}), pas d'un mot-clé.
     */
    public static final String SOURCE_DEFAULT = "SOURCE_DEFAULT";

    /**
     * Version des règles de décision, incluse dans {@link #referentialVersion()} : la changer quand une règle
     * de {@link #decide(String, String, List)} change, pour que l'import re-décide toute la quarantaine au
     * passage suivant (sinon un élément inchangé y resterait avec l'ancienne décision).
     */
    static final String DECISION_RULES_VERSION = "regles-2:titre-apres-keywords-source-non-reconnus";

    private final CategoryRepository categoryRepository;
    private final ClassificationRecordRepository recordRepository;
    private final CategoryKeywordRepository keywordRepository;

    /**
     * Référentiel chargé une fois pour un lot de décisions, voir {@link #beginBatch()}. Par thread : un
     * lot appartient au thread qui l'a ouvert (l'import), les autres appelants ne le voient pas.
     */
    private final ThreadLocal<Referential> batchReferential = new ThreadLocal<>();

    /** Mots-clés actifs et catégories, tels que lus en base à l'ouverture d'un lot. */
    private record Referential(
            List<CategoryKeyword> activeKeywords, Map<Long, Category> categoriesById,
            Map<String, Category> categoriesByCode) {
    }

    public ClassificationService(
            CategoryRepository categoryRepository, ClassificationRecordRepository recordRepository,
            CategoryKeywordRepository keywordRepository) {
        this.categoryRepository = categoryRepository;
        this.recordRepository = recordRepository;
        this.keywordRepository = keywordRepository;
    }

    /**
     * Ouvre un lot de décisions sur le thread courant : le référentiel (mots-clés actifs, catégories) est
     * lu une fois, puis réutilisé par tous les {@code decide(...)} jusqu'à {@link #endBatch()}.
     *
     * <p>Sans lot, chaque décision relit le référentiel (une requête pour les mots-clés, une par
     * catégorie rencontrée, une par code à résoudre) : sans importance pour une décision isolée, mais une
     * quinzaine de requêtes par élément pendant un import. Le référentiel ne doit pas changer pendant le
     * lot ; l'import en ouvre un par source. Toujours refermer dans un {@code finally}.
     */
    public void beginBatch() {
        List<Category> categories = categoryRepository.findAll();
        Map<Long, Category> categoriesById = new HashMap<>();
        Map<String, Category> categoriesByCode = new HashMap<>();
        for (Category category : categories) {
            categoriesById.put(category.id(), category);
            categoriesByCode.put(category.code(), category);
        }
        batchReferential.set(new Referential(
                keywordRepository.findByActiveTrueOrderByIdAsc(), categoriesById, categoriesByCode));
    }

    /** Referme le lot ouvert par {@link #beginBatch()} ; sans effet si aucun lot n'est ouvert. */
    public void endBatch() {
        batchReferential.remove();
    }

    private List<CategoryKeyword> activeKeywords() {
        Referential referential = batchReferential.get();
        return referential != null ? referential.activeKeywords() : keywordRepository.findByActiveTrueOrderByIdAsc();
    }

    private Optional<Category> categoryById(Long id) {
        Referential referential = batchReferential.get();
        return referential != null
                ? Optional.ofNullable(referential.categoriesById().get(id))
                : categoryRepository.findById(id);
    }

    private Optional<Category> categoryByCode(String code) {
        Referential referential = batchReferential.get();
        return referential != null
                ? Optional.ofNullable(referential.categoriesByCode().get(code))
                : categoryRepository.findByCode(code);
    }

    /**
     * Décide à partir des seuls keywords source, sans rien persister : le titre n'est pas utilisé. Les
     * keywords source sont comparés au référentiel {@code category_keyword} complet (portées {@code ALL} et
     * {@code SOURCE}), lu seulement si la source fournit au moins un keyword.
     */
    public ClassificationOutcome decide(String sourceCategory, List<String> sourceTags) {
        List<KeywordEntry> entries =
                hasSourceKeywords(sourceCategory, sourceTags) ? loadKeywordEntries(true) : List.of();
        return resolve(ActivityClassifier.classify(sourceCategory, sourceTags, entries));
    }

    /**
     * Décide en tenant compte du fallback titre, sans rien persister. Les keywords source (catégorie
     * d'origine ou tag non blanc) ont toujours priorité : s'ils donnent au moins une catégorie, le résultat
     * est exactement celui de {@link #decide(String, List)}. Sinon (aucun keyword source, ou aucun reconnu),
     * le titre est comparé aux mots-clés actifs de {@code category_keyword} de portée {@code ALL} (les
     * mots-clés {@code SOURCE}, trop ambigus dans un titre libre, sont exclus). Si le titre ne classe rien
     * non plus, des keywords source non reconnus restent {@code NEEDS_REVIEW} ({@code UNRECOGNIZED}).
     *
     * <p>Mesure à l'origine du repli après des keywords non reconnus (bêta, 24/10/2026) : 14 559 éléments
     * en quarantaine pour cette raison, souvent des étiquettes de public, de lieu ou d'organisateur
     * (« gratuit », « nantes », « CLAVIM ») ; le titre en classait environ 3 500.
     */
    public ClassificationOutcome decide(String title, String sourceCategory, List<String> sourceTags) {
        if (hasSourceKeywords(sourceCategory, sourceTags)) {
            ClassificationOutcome bySource = decide(sourceCategory, sourceTags);
            if (bySource.classified()) {
                return bySource;
            }
            ClassificationOutcome byTitle = decideByTitle(title);
            return byTitle.classified() ? byTitle : bySource;
        }
        return decideByTitle(title);
    }

    private ClassificationOutcome decideByTitle(String title) {
        List<KeywordEntry> entries = title == null || title.isBlank() ? List.of() : loadKeywordEntries(false);
        return resolve(TitleKeywordClassifier.classify(title, entries));
    }

    /**
     * Version du référentiel de classification : change dès qu'un mot-clé actif (texte, catégorie, portée),
     * l'état d'une catégorie ou les règles de décision ({@link #DECISION_RULES_VERSION}) changent. Tant
     * qu'elle est identique, {@link #decide(String, String, List)}
     * rend la même décision pour les mêmes entrées : l'import s'en sert pour ne pas re-décider à chaque
     * passage un élément en quarantaine inchangé.
     */
    public String referentialVersion() {
        StringBuilder state = new StringBuilder(DECISION_RULES_VERSION).append('\n');
        for (CategoryKeyword keyword : keywordRepository.findByActiveTrueOrderByIdAsc()) {
            state.append(keyword.categoryId()).append('|').append(keyword.keyword()).append('|')
                    .append(keyword.scope()).append('\n');
        }
        categoryRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Category::id))
                .forEach(category -> state.append(category.id()).append('|').append(category.code()).append('|')
                        .append(category.active()).append('\n'));
        return Integer.toHexString(state.toString().hashCode()) + "-" + state.length();
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
     * conservés : ils restent tracés ({@code matched_keywords}) et la catégorie inactive est écartée à la
     * résolution (voir {@link #resolve}).
     *
     * @param includeSourceScope {@code true} pour les keywords source (portées ALL et SOURCE),
     *                           {@code false} pour le titre (portée ALL seulement)
     */
    private List<KeywordEntry> loadKeywordEntries(boolean includeSourceScope) {
        Map<Long, Optional<String>> codes = new HashMap<>();
        List<KeywordEntry> entries = new ArrayList<>();
        for (CategoryKeyword keyword : activeKeywords()) {
            if (!includeSourceScope && !CategoryKeyword.SCOPE_ALL.equals(keyword.scope())) {
                continue;
            }
            Optional<String> code = codes.computeIfAbsent(keyword.categoryId(),
                    id -> categoryById(id).map(Category::code));
            code.ifPresent(value -> entries.add(new KeywordEntry(value, keyword.keyword())));
        }
        return entries;
    }

    /**
     * Résout les codes V1 d'une décision vers les catégories existantes et actives du référentiel.
     * Un code absent ou inactif est écarté (jamais créé) ; si aucun ne l'est, {@code NEEDS_REVIEW}
     * ({@code CATEGORY_NOT_IN_REFERENTIAL}, codes non résolus en candidats).
     */
    private ClassificationOutcome resolve(Decision decision) {
        if (decision.status() != ClassificationStatus.AUTO_CLASSIFIED) {
            return new ClassificationOutcome(decision, List.of());
        }
        List<String> codes = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (String code : decision.categoryCodes()) {
            categoryByCode(code).filter(Category::active).ifPresent(category -> {
                codes.add(code);
                ids.add(category.id());
            });
        }
        if (ids.isEmpty()) {
            Decision unresolved = new Decision(
                    ClassificationStatus.NEEDS_REVIEW, List.of(), CATEGORY_NOT_IN_REFERENTIAL,
                    decision.categoryCodes(), "REVIEW:" + CATEGORY_NOT_IN_REFERENTIAL,
                    decision.source(), decision.matchedKeywords());
            return new ClassificationOutcome(unresolved, List.of());
        }
        Decision resolved = new Decision(
                decision.status(), List.copyOf(codes), decision.reason(), decision.candidateCodes(),
                decision.ruleId(), decision.source(), decision.matchedKeywords());
        return new ClassificationOutcome(resolved, List.copyOf(ids));
    }

    /**
     * Classification par les catégories par défaut d'une source, quand ni les mots-clés source ni le
     * titre n'ont rien donné. Seules les catégories existantes et actives sont retenues ; s'il n'en reste
     * aucune, le résultat n'est pas classé ({@code UNCLASSIFIED}) et l'élément reste en quarantaine.
     *
     * @param categoryIds catégories par défaut de la source, peut être vide
     */
    public ClassificationOutcome sourceDefault(List<Long> categoryIds) {
        List<String> codes = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        if (categoryIds != null) {
            for (Long categoryId : categoryIds) {
                categoryById(categoryId).filter(Category::active).ifPresent(category -> {
                    codes.add(category.code());
                    ids.add(category.id());
                });
            }
        }
        if (ids.isEmpty()) {
            return new ClassificationOutcome(
                    new Decision(ClassificationStatus.UNCLASSIFIED, List.of(), null, List.of(),
                            "UNCLASSIFIED:NO_SIGNAL"),
                    List.of());
        }
        Decision decision = new Decision(
                ClassificationStatus.AUTO_CLASSIFIED, List.copyOf(codes), null, List.of(),
                SOURCE_DEFAULT + ":" + String.join("+", codes), SOURCE_DEFAULT, List.of());
        return new ClassificationOutcome(decision, List.copyOf(ids));
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
                decision.source(), joinMatchedKeywords(decision.matchedKeywords()),
                joinCodes(decision.categoryCodes())));
    }

    private static String joinCodes(List<String> codes) {
        return codes == null || codes.isEmpty() ? null : truncate(String.join(",", codes), 255);
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
