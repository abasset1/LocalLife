package com.locallife.backend.activity.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppression définitive d'activités par un administrateur : une activité précise, ou toutes celles
 * qui répondent à des critères (source, statut, période de début), ou toutes.
 *
 * <p>Contrairement à {@link ContributionService#delete}, les activités importées sont aussi
 * supprimables. Une activité importée encore présente dans sa source reviendra au prochain import.
 *
 * <p>La suppression se fait en SQL, par lots : passer par les services un par un serait trop lent
 * pour plusieurs milliers d'activités. Les tables qui référencent {@code activity} sans
 * {@code ON DELETE CASCADE} sont vidées d'abord ({@link #DEPENDENT_TABLES}) ; les autres (favoris,
 * catégories, alertes envoyées, relectures) partent en cascade. Un test vérifie que cette liste
 * couvre toutes les tables concernées du schéma, pour qu'une nouvelle table ne bloque pas la
 * suppression. Les lieux et médias, partageables, restent.
 */
@Service
public class ActivityBulkDeletionService {

    /** Tables liées à {@code activity} sans suppression en cascade, vidées avant les activités. */
    static final List<String> DEPENDENT_TABLES = List.of(
            "activity_media", "activity_link", "accessibility", "activity_tag", "activity_metadata",
            "activity_activity_type", "activity_audience", "activity_organizer_type");

    static final Set<String> STATUSES = Set.of("PENDING", "PUBLISHED", "REJECTED", "ARCHIVED", "CANCELLED");

    private static final int BATCH_SIZE = 1_000;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ActivityBulkDeletionService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Critères de suppression ; {@code null} = pas de filtre sur ce champ. Les dates portent sur le
     * début de l'activité, bornes incluses. {@code all} doit être vrai pour viser toutes les
     * activités : un formulaire vide ne peut pas tout supprimer par mégarde.
     */
    public record Criteria(Long sourceId, String status, LocalDate startFrom, LocalDate startTo, boolean all) {

        boolean hasFilter() {
            return sourceId != null || status != null || startFrom != null || startTo != null;
        }
    }

    /** Nombre d'activités visées, pour l'aperçu avant suppression. */
    public long count(Criteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = whereClause(criteria, params);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity a" + where, params, Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Supprime les activités visées.
     *
     * @param expectedCount nombre affiché à l'administrateur dans l'aperçu : si les activités visées
     *     ont changé entre-temps (import en cours…), rien n'est supprimé
     * @return le nombre d'activités supprimées
     * @throws IllegalArgumentException si les critères sont invalides
     * @throws DeletionCountChangedException si le nombre visé ne correspond plus à {@code expectedCount}
     */
    @Transactional
    public int delete(Criteria criteria, long expectedCount) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = whereClause(criteria, params);
        List<Long> ids = jdbcTemplate.queryForList("SELECT a.id FROM activity a" + where, params, Long.class);
        if (ids.size() != expectedCount) {
            throw new DeletionCountChangedException(expectedCount, ids.size());
        }
        return deleteByIds(ids);
    }

    /** Supprime une activité, importée ou non. {@code false} si elle n'existe pas. */
    @Transactional
    public boolean deleteOne(long id) {
        return deleteByIds(List.of(id)) == 1;
    }

    private int deleteByIds(List<Long> ids) {
        int deleted = 0;
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<Long> batchIds = ids.subList(start, Math.min(ids.size(), start + BATCH_SIZE));
            Map<String, List<Long>> batch = Map.of("ids", batchIds);
            jdbcTemplate.update("DELETE FROM occurrence WHERE schedule_id IN "
                    + "(SELECT id FROM schedule WHERE activity_id IN (:ids))", batch);
            jdbcTemplate.update("DELETE FROM schedule WHERE activity_id IN (:ids)", batch);
            for (String table : DEPENDENT_TABLES) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE activity_id IN (:ids)", batch);
            }
            deleted += jdbcTemplate.update("DELETE FROM activity WHERE id IN (:ids)", batch);
        }
        return deleted;
    }

    private static String whereClause(Criteria criteria, MapSqlParameterSource params) {
        if (!criteria.all() && !criteria.hasFilter()) {
            throw new IllegalArgumentException(
                    "Choisis au moins un critère (source, statut ou période), ou confirme la suppression de toutes "
                            + "les activités.");
        }
        if (criteria.status() != null && !STATUSES.contains(criteria.status())) {
            throw new IllegalArgumentException("Statut inconnu : " + criteria.status() + ".");
        }
        if (criteria.startFrom() != null && criteria.startTo() != null
                && criteria.startFrom().isAfter(criteria.startTo())) {
            throw new IllegalArgumentException("La date de début de période doit précéder la date de fin.");
        }
        List<String> conditions = new ArrayList<>();
        if (criteria.sourceId() != null) {
            conditions.add("a.source_id = :sourceId");
            params.addValue("sourceId", criteria.sourceId());
        }
        if (criteria.status() != null) {
            conditions.add("a.status = :status");
            params.addValue("status", criteria.status());
        }
        if (criteria.startFrom() != null) {
            conditions.add("a.start_date >= :startFrom");
            params.addValue("startFrom", criteria.startFrom().atStartOfDay());
        }
        if (criteria.startTo() != null) {
            conditions.add("a.start_date < :startToExclusive");
            params.addValue("startToExclusive", criteria.startTo().plusDays(1).atStartOfDay());
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /** Les activités visées ont changé depuis l'aperçu : rien n'a été supprimé. */
    public static class DeletionCountChangedException extends RuntimeException {

        public DeletionCountChangedException(long expected, long actual) {
            super("Le nombre d'activités visées a changé depuis l'aperçu (" + actual + " au lieu de " + expected
                    + ") : rien n'a été supprimé. Vérifie l'aperçu puis recommence.");
        }
    }
}
