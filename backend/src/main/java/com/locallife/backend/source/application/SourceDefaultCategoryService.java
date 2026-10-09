package com.locallife.backend.source.application;

import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catégories par défaut d'une source (table {@code source_default_category}, V56) : appliquées par
 * l'import à un élément que ni ses mots-clés source ni son titre ne permettent de classer, au lieu de
 * le laisser en quarantaine.
 *
 * <p>Réglées par un administrateur (une source thématique — agenda de salle de concert, de festival —
 * dit par nature la catégorie de ses événements), jamais déduites automatiquement. Une source sans
 * catégorie par défaut se comporte comme avant.
 */
@Service
public class SourceDefaultCategoryService {

    private final JdbcTemplate jdbcTemplate;
    private final SourceRepository sourceRepository;
    private final CategoryRepository categoryRepository;

    public SourceDefaultCategoryService(
            JdbcTemplate jdbcTemplate, SourceRepository sourceRepository, CategoryRepository categoryRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.sourceRepository = sourceRepository;
        this.categoryRepository = categoryRepository;
    }

    /** Ids des catégories par défaut d'une source, triés par id ; liste vide si aucune. */
    public List<Long> findCategoryIds(Long sourceId) {
        return jdbcTemplate.queryForList(
                "SELECT category_id FROM source_default_category WHERE source_id = ? ORDER BY category_id",
                Long.class, sourceId);
    }

    /** Codes des catégories par défaut d'une source, dans l'ordre des ids de catégorie. */
    public List<String> findCategoryCodes(Long sourceId) {
        return jdbcTemplate.queryForList(
                "SELECT c.code FROM source_default_category d JOIN category c ON c.id = d.category_id "
                        + "WHERE d.source_id = ? ORDER BY c.id",
                String.class, sourceId);
    }

    /**
     * Codes des catégories par défaut de toutes les sources qui en ont, en une requête : id de source →
     * codes. Une source sans catégorie par défaut est absente de la map.
     */
    public Map<Long, List<String>> findAllCategoryCodes() {
        Map<Long, List<String>> codesBySourceId = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT d.source_id, c.code FROM source_default_category d "
                        + "JOIN category c ON c.id = d.category_id ORDER BY d.source_id, c.id",
                resultSet -> {
                    codesBySourceId.computeIfAbsent(resultSet.getLong("source_id"), id -> new ArrayList<>())
                            .add(resultSet.getString("code"));
                });
        return codesBySourceId;
    }

    /**
     * Remplace les catégories par défaut d'une source. Une liste vide ou {@code null} les supprime toutes.
     *
     * @return les codes enregistrés, sans doublon, dans l'ordre fourni
     * @throws NoSuchElementException   si la source n'existe pas
     * @throws IllegalArgumentException si un code est inconnu ou désigne une catégorie inactive
     */
    @Transactional
    public List<String> replace(Long sourceId, List<String> categoryCodes) {
        sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Source introuvable : " + sourceId));

        Set<String> codes = new LinkedHashSet<>();
        if (categoryCodes != null) {
            for (String code : categoryCodes) {
                if (code != null && !code.isBlank()) {
                    codes.add(code.trim());
                }
            }
        }
        List<Long> categoryIds = new ArrayList<>();
        for (String code : codes) {
            Category category = categoryRepository.findByCode(code)
                    .filter(Category::active)
                    .orElseThrow(() -> new IllegalArgumentException("Catégorie inconnue ou inactive : " + code));
            categoryIds.add(category.id());
        }

        jdbcTemplate.update("DELETE FROM source_default_category WHERE source_id = ?", sourceId);
        for (Long categoryId : categoryIds) {
            jdbcTemplate.update(
                    "INSERT INTO source_default_category (source_id, category_id) VALUES (?, ?)",
                    sourceId, categoryId);
        }
        return List.copyOf(codes);
    }

}
