package com.locallife.backend.category.application;

import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.category.infrastructure.CategoryAdminRepository;
import com.locallife.backend.category.infrastructure.CategoryAdminRepository.CategoryRow;
import com.locallife.backend.category.infrastructure.CategoryAdminRepository.KeywordRow;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des catégories et des mots-clés de classification depuis l'administration : ajout, renommage,
 * activation / désactivation. Jamais de suppression : une catégorie désactivée n'est plus proposée (filtres,
 * préférences, alertes) ni attribuée par la classification, mais les activités qui la portent la gardent ; un
 * mot-clé désactivé n'est plus lu par la classification ({@code ClassificationService}).
 *
 * <p>Les changements prennent effet au prochain import : la version du référentiel
 * ({@code ClassificationService#referentialVersion}) change, les éléments en quarantaine sont donc re-décidés.
 */
@Service
public class CategoryAdminService {

    /** Même forme que les codes existants ({@code CULTURE}, {@code EMPLOYMENT}...) : {@code SCREAMING_SNAKE_CASE}. */
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,49}");
    private static final int LABEL_MAX_LENGTH = 255;
    private static final int KEYWORD_MAX_LENGTH = 100;
    private static final Set<String> SCOPES = Set.of(CategoryKeyword.SCOPE_ALL, CategoryKeyword.SCOPE_SOURCE);

    private final CategoryAdminRepository repository;

    public CategoryAdminService(CategoryAdminRepository repository) {
        this.repository = repository;
    }

    /** Conflit avec l'existant (code de catégorie déjà pris, mot-clé déjà actif dans la catégorie) → 409. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public record KeywordView(Long id, String keyword, String scope, boolean active) {
    }

    public record CategoryView(String code, String label, String description, boolean active,
            List<KeywordView> keywords) {
    }

    /** Toutes les catégories (actives ou non) avec tous leurs mots-clés (actifs ou non). */
    @Transactional(readOnly = true)
    public List<CategoryView> findAll() {
        Map<Long, List<KeywordView>> keywordsByCategory = repository.findAllKeywords().stream()
                .collect(Collectors.groupingBy(KeywordRow::categoryId, Collectors.mapping(
                        CategoryAdminService::toView, Collectors.toList())));
        return repository.findAllCategories().stream()
                .map(row -> toView(row, keywordsByCategory.getOrDefault(row.id(), List.of())))
                .toList();
    }

    @Transactional
    public CategoryView createCategory(String code, String label, String description) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new IllegalArgumentException(
                    "Code invalide : lettres majuscules, chiffres et _, commençant par une lettre (ex. BIEN_ETRE).");
        }
        String normalizedLabel = requireLabel(label);
        if (repository.categoryCodeExists(normalizedCode)) {
            throw new ConflictException("La catégorie " + normalizedCode + " existe déjà.");
        }
        Long id = repository.insertCategory(normalizedCode);
        repository.upsertTranslation(id, normalizedLabel, blankToNull(description));
        return find(normalizedCode).orElseThrow();
    }

    /**
     * Met à jour les champs fournis (non {@code null}) ; {@code Optional.empty()} si la catégorie n'existe pas.
     */
    @Transactional
    public Optional<CategoryView> updateCategory(String code, String label, String description, Boolean active) {
        Optional<CategoryRow> existing = repository.findCategoryByCode(code);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        CategoryRow row = existing.get();
        if (label != null || description != null) {
            String newLabel = label != null ? requireLabel(label) : row.label() != null ? row.label() : row.code();
            String newDescription = description != null ? blankToNull(description) : row.description();
            repository.upsertTranslation(row.id(), newLabel, newDescription);
        }
        if (active != null && active != row.active()) {
            repository.setCategoryActive(row.id(), active);
        }
        return find(code);
    }

    /**
     * Ajoute un mot-clé à la catégorie ; {@code Optional.empty()} si la catégorie n'existe pas. Un mot-clé
     * identique mais désactivé est réactivé (avec la portée demandée) plutôt que dupliqué.
     */
    @Transactional
    public Optional<KeywordView> addKeyword(String categoryCode, String keyword, String scope) {
        Optional<CategoryRow> category = repository.findCategoryByCode(categoryCode);
        if (category.isEmpty()) {
            return Optional.empty();
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedScope = normalizeScope(scope, CategoryKeyword.SCOPE_ALL);
        Long categoryId = category.get().id();
        Optional<KeywordRow> existing = repository.findKeyword(categoryId, normalizedKeyword);
        if (existing.isPresent()) {
            if (existing.get().active()) {
                throw new ConflictException(
                        "Le mot-clé « " + normalizedKeyword + " » existe déjà dans cette catégorie.");
            }
            repository.updateKeyword(existing.get().id(), true, normalizedScope);
            return repository.findKeywordById(existing.get().id()).map(CategoryAdminService::toView);
        }
        Long id = repository.insertKeyword(categoryId, normalizedKeyword, normalizedScope);
        return repository.findKeywordById(id).map(CategoryAdminService::toView);
    }

    /** Active / désactive un mot-clé ou change sa portée ; {@code Optional.empty()} s'il n'existe pas. */
    @Transactional
    public Optional<KeywordView> updateKeyword(Long id, Boolean active, String scope) {
        Optional<KeywordRow> existing = repository.findKeywordById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        KeywordRow row = existing.get();
        repository.updateKeyword(id, active != null ? active : row.active(), normalizeScope(scope, row.scope()));
        return repository.findKeywordById(id).map(CategoryAdminService::toView);
    }

    private Optional<CategoryView> find(String code) {
        return findAll().stream().filter(view -> view.code().equals(code)).findFirst();
    }

    /**
     * Mot-clé en minuscules, espaces réduits : la comparaison ({@code KeywordMatcher}) ignore déjà la casse,
     * cela évite seulement deux lignes « Concert » et « concert ». Les accents sont conservés (significatifs).
     */
    static String normalizeKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ").toLowerCase(Locale.FRENCH);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Le mot-clé est obligatoire.");
        }
        if (normalized.length() > KEYWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("Le mot-clé dépasse " + KEYWORD_MAX_LENGTH + " caractères.");
        }
        return normalized;
    }

    private static String normalizeScope(String scope, String fallback) {
        if (scope == null || scope.isBlank()) {
            return fallback;
        }
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(normalized)) {
            throw new IllegalArgumentException("Portée inconnue : ALL ou SOURCE.");
        }
        return normalized;
    }

    private static String requireLabel(String label) {
        String normalized = label == null ? "" : label.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Le libellé est obligatoire.");
        }
        if (normalized.length() > LABEL_MAX_LENGTH) {
            throw new IllegalArgumentException("Le libellé dépasse " + LABEL_MAX_LENGTH + " caractères.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static KeywordView toView(KeywordRow row) {
        return new KeywordView(row.id(), row.keyword(), row.scope(), row.active());
    }

    private static CategoryView toView(CategoryRow row, List<KeywordView> keywords) {
        return new CategoryView(row.code(), row.label() != null ? row.label() : row.code(), row.description(),
                row.active(), keywords);
    }
}
