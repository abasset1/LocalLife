package com.locallife.backend.category.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Écritures du référentiel des catégories et de leurs mots-clés depuis l'administration. Les lectures publiques
 * restent sur {@link CategoryRepository} (lecture seule) ; ce repository est le seul point d'écriture applicatif
 * de {@code category}, {@code category_translation} et {@code category_keyword}.
 *
 * <p>Rien n'est jamais supprimé : une catégorie ou un mot-clé se désactive ({@code active = false}), ce qui
 * conserve les rattachements d'activités et la traçabilité des classifications passées.
 */
@Repository
public class CategoryAdminRepository {

    /** Seule locale éditée depuis l'administration (V1, voir {@code V32__refactor_category_referential.sql}). */
    public static final String LOCALE = "fr-FR";

    private final NamedParameterJdbcTemplate jdbc;

    public CategoryAdminRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CategoryRow(Long id, String code, boolean active, String label, String description) {
    }

    public record KeywordRow(Long id, Long categoryId, String keyword, String scope, boolean active) {
    }

    /** Toutes les catégories, actives ou non, avec leur libellé {@code fr-FR} (ou {@code null}). */
    public List<CategoryRow> findAllCategories() {
        return jdbc.query("""
                SELECT c.id, c.code, c.active, t.label, t.description
                FROM category c
                LEFT JOIN category_translation t ON t.category_id = c.id AND t.locale = :locale
                ORDER BY COALESCE(t.label, c.code), c.id
                """, new MapSqlParameterSource("locale", LOCALE), (rs, i) -> new CategoryRow(
                rs.getLong("id"), rs.getString("code"), rs.getBoolean("active"), rs.getString("label"),
                rs.getString("description")));
    }

    public Optional<CategoryRow> findCategoryByCode(String code) {
        return findAllCategories().stream().filter(row -> row.code().equals(code)).findFirst();
    }

    public boolean categoryCodeExists(String code) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM category WHERE code = :code",
                new MapSqlParameterSource("code", code), Integer.class);
        return count != null && count > 0;
    }

    public Long insertCategory(String code) {
        return jdbc.queryForObject("INSERT INTO category (code) VALUES (:code) RETURNING id",
                new MapSqlParameterSource("code", code), Long.class);
    }

    public void setCategoryActive(Long categoryId, boolean active) {
        jdbc.update("UPDATE category SET active = :active, updated_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", categoryId).addValue("active", active));
    }

    /** Crée ou remplace le libellé et la description {@code fr-FR} de la catégorie. */
    public void upsertTranslation(Long categoryId, String label, String description) {
        jdbc.update("""
                INSERT INTO category_translation (category_id, locale, label, description)
                VALUES (:categoryId, :locale, :label, :description)
                ON CONFLICT (category_id, locale) DO UPDATE
                    SET label = EXCLUDED.label, description = EXCLUDED.description
                """, new MapSqlParameterSource("categoryId", categoryId).addValue("locale", LOCALE)
                .addValue("label", label).addValue("description", description));
        jdbc.update("UPDATE category SET updated_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", categoryId));
    }

    /** Tous les mots-clés, actifs ou non, par ordre alphabétique. */
    public List<KeywordRow> findAllKeywords() {
        return jdbc.query("SELECT id, category_id, keyword, scope, active FROM category_keyword ORDER BY keyword, id",
                new MapSqlParameterSource(), (rs, i) -> keywordRow(rs));
    }

    public Optional<KeywordRow> findKeywordById(Long id) {
        return jdbc.query("SELECT id, category_id, keyword, scope, active FROM category_keyword WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> keywordRow(rs)).stream().findFirst();
    }

    /** Même mot-clé déjà présent dans la catégorie (contrainte {@code uq_category_keyword}). */
    public Optional<KeywordRow> findKeyword(Long categoryId, String keyword) {
        return jdbc.query("""
                SELECT id, category_id, keyword, scope, active FROM category_keyword
                WHERE category_id = :categoryId AND keyword = :keyword
                """, new MapSqlParameterSource("categoryId", categoryId).addValue("keyword", keyword),
                (rs, i) -> keywordRow(rs)).stream().findFirst();
    }

    public Long insertKeyword(Long categoryId, String keyword, String scope) {
        return jdbc.queryForObject("""
                INSERT INTO category_keyword (category_id, keyword, scope)
                VALUES (:categoryId, :keyword, :scope) RETURNING id
                """, new MapSqlParameterSource("categoryId", categoryId).addValue("keyword", keyword)
                .addValue("scope", scope), Long.class);
    }

    public void updateKeyword(Long id, boolean active, String scope) {
        jdbc.update("""
                UPDATE category_keyword SET active = :active, scope = :scope, updated_at = now() WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("active", active).addValue("scope", scope));
    }

    private static KeywordRow keywordRow(ResultSet rs) throws SQLException {
        return new KeywordRow(rs.getLong("id"), rs.getLong("category_id"), rs.getString("keyword"),
                rs.getString("scope"), rs.getBoolean("active"));
    }
}
