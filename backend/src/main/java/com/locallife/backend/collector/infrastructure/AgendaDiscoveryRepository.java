package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.application.AgendaDiscoveryRun;
import com.locallife.backend.collector.domain.DiscoveredAgenda;
import com.locallife.backend.collector.domain.DiscoveryScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Accès au catalogue des agendas découverts ({@code discovered_agenda}) et aux parcours de l'annuaire
 * ({@code agenda_discovery_run}), tables V68.
 */
@Repository
public class AgendaDiscoveryRepository {

    public static final String PROVIDER = "OPENAGENDA";

    private static final int MAX_TITLE_LENGTH = 512;
    private static final int MAX_SLUG_LENGTH = 255;
    private static final int MAX_REASON_LENGTH = 1000;

    /** Source déjà créée pour l'agenda (la plus ancienne, s'il y en a plusieurs). */
    private static final String KNOWN_SOURCE_JOIN = """
            LEFT JOIN LATERAL (
                SELECT s.id, s.name, s.status FROM source s WHERE s.agenda_uid = d.agenda_uid ORDER BY s.id LIMIT 1
            ) src ON TRUE
            """;

    private final JdbcTemplate jdbcTemplate;

    public AgendaDiscoveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- Catalogue ---

    /**
     * Insère l'agenda, ou met à jour titre, slug, statut officiel et date de dernière observation s'il est déjà
     * au catalogue : un agenda n'y figure jamais deux fois.
     *
     * @return {@code true} si l'agenda vient d'entrer au catalogue
     */
    public boolean upsert(DiscoveredAgenda agenda, Long runId, LocalDateTime seenAt) {
        Boolean inserted = jdbcTemplate.queryForObject("""
                INSERT INTO discovered_agenda
                    (provider, agenda_uid, title, slug, official, first_seen_at, last_seen_at, last_run_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (provider, agenda_uid) DO UPDATE SET
                    title = EXCLUDED.title,
                    slug = EXCLUDED.slug,
                    official = COALESCE(EXCLUDED.official, discovered_agenda.official),
                    last_seen_at = EXCLUDED.last_seen_at,
                    last_run_id = EXCLUDED.last_run_id
                RETURNING (xmax = 0)
                """, Boolean.class, PROVIDER, agenda.uid(), truncate(agenda.title(), MAX_TITLE_LENGTH),
                truncate(agenda.slug(), MAX_SLUG_LENGTH), agenda.official(), Timestamp.valueOf(seenAt),
                Timestamp.valueOf(seenAt), runId);
        return Boolean.TRUE.equals(inserted);
    }

    /** Filtre de l'aperçu : agendas sans source, avec source, ou tous. */
    public enum CatalogFilter { NEW, KNOWN, ALL }

    /** Une ligne de l'aperçu : l'agenda et, s'il en a une, sa source. */
    public record CatalogEntry(
            String agendaUid,
            String title,
            String slug,
            Boolean official,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            Long sourceId,
            String sourceName,
            String sourceStatus) {
    }

    public List<CatalogEntry> search(CatalogFilter filter, String query, Boolean official, int offset, int limit) {
        List<Object> arguments = new ArrayList<>();
        String where = where(filter, query, official, arguments);
        arguments.add(limit);
        arguments.add(offset);
        return jdbcTemplate.query("SELECT d.agenda_uid, d.title, d.slug, d.official, d.first_seen_at, "
                + "d.last_seen_at, src.id AS source_id, src.name AS source_name, src.status AS source_status "
                + "FROM discovered_agenda d " + KNOWN_SOURCE_JOIN + where
                + " ORDER BY lower(d.title) NULLS LAST, d.id LIMIT ? OFFSET ?", CATALOG_ENTRY, arguments.toArray());
    }

    public long count(CatalogFilter filter, String query, Boolean official) {
        List<Object> arguments = new ArrayList<>();
        String where = where(filter, query, official, arguments);
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM discovered_agenda d " + KNOWN_SOURCE_JOIN + where, Long.class,
                arguments.toArray());
        return count == null ? 0 : count;
    }

    /** Entrées du catalogue pour ces identifiants ; un identifiant absent du catalogue n'a pas d'entrée. */
    public List<CatalogEntry> findByUids(Collection<String> agendaUids) {
        if (agendaUids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", agendaUids.stream().map(uid -> "?").toList());
        List<Object> arguments = new ArrayList<>();
        arguments.add(PROVIDER);
        arguments.addAll(agendaUids);
        return jdbcTemplate.query("SELECT d.agenda_uid, d.title, d.slug, d.official, d.first_seen_at, "
                + "d.last_seen_at, src.id AS source_id, src.name AS source_name, src.status AS source_status "
                + "FROM discovered_agenda d " + KNOWN_SOURCE_JOIN
                + "WHERE d.provider = ? AND d.agenda_uid IN (" + placeholders + ")", CATALOG_ENTRY,
                arguments.toArray());
    }

    private static String where(CatalogFilter filter, String query, Boolean official, List<Object> arguments) {
        StringBuilder where = new StringBuilder(" WHERE d.provider = ?");
        arguments.add(PROVIDER);
        if (filter == CatalogFilter.NEW) {
            where.append(" AND src.id IS NULL");
        } else if (filter == CatalogFilter.KNOWN) {
            where.append(" AND src.id IS NOT NULL");
        }
        if (official != null) {
            where.append(official ? " AND d.official IS TRUE" : " AND d.official IS NOT TRUE");
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            where.append(" AND (lower(d.title) LIKE ? OR lower(d.slug) LIKE ? OR d.agenda_uid = ?)");
            arguments.add(pattern);
            arguments.add(pattern);
            arguments.add(query.trim());
        }
        return where.toString();
    }

    private static final RowMapper<CatalogEntry> CATALOG_ENTRY = (resultSet, rowNumber) -> new CatalogEntry(
            resultSet.getString("agenda_uid"),
            resultSet.getString("title"),
            resultSet.getString("slug"),
            (Boolean) resultSet.getObject("official"),
            toLocalDateTime(resultSet.getTimestamp("first_seen_at")),
            toLocalDateTime(resultSet.getTimestamp("last_seen_at")),
            (Long) resultSet.getObject("source_id"),
            resultSet.getString("source_name"),
            resultSet.getString("source_status"));

    // --- Parcours ---

    public Optional<AgendaDiscoveryRun> findInProgress(DiscoveryScope scope) {
        return jdbcTemplate.query("SELECT * FROM agenda_discovery_run WHERE provider = ? AND scope = ? "
                + "AND status = 'IN_PROGRESS'", RUN, PROVIDER, scope.name()).stream().findFirst();
    }

    public Optional<AgendaDiscoveryRun> findLatest(DiscoveryScope scope) {
        return jdbcTemplate.query("SELECT * FROM agenda_discovery_run WHERE provider = ? AND scope = ? "
                + "ORDER BY id DESC LIMIT 1", RUN, PROVIDER, scope.name()).stream().findFirst();
    }

    public Optional<AgendaDiscoveryRun> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM agenda_discovery_run WHERE id = ?", RUN, id).stream().findFirst();
    }

    public AgendaDiscoveryRun create(DiscoveryScope scope, LocalDateTime now) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO agenda_discovery_run (provider, scope, status, started_at, updated_at)
                VALUES (?, ?, 'IN_PROGRESS', ?, ?) RETURNING id
                """, Long.class, PROVIDER, scope.name(), Timestamp.valueOf(now), Timestamp.valueOf(now));
        return findById(id).orElseThrow();
    }

    /** Enregistre l'avancement (compteurs, curseur, statut) ; appelé dans la transaction de la page traitée. */
    public void save(AgendaDiscoveryRun run) {
        jdbcTemplate.update("""
                UPDATE agenda_discovery_run SET status = ?, cursor = ?, pages_read = ?, examined = ?, new_count = ?,
                    already_known = ?, ignored = ?, errors = ?, provider_total = ?, stop_reason = ?, updated_at = ?,
                    ended_at = ?
                WHERE id = ?
                """, run.status().name(), run.cursor(), run.pagesRead(), run.examined(), run.newCount(),
                run.alreadyKnown(), run.ignored(), run.errors(), run.providerTotal(),
                truncate(run.stopReason(), MAX_REASON_LENGTH), Timestamp.valueOf(run.updatedAt()),
                run.endedAt() == null ? null : Timestamp.valueOf(run.endedAt()), run.id());
    }

    private static final RowMapper<AgendaDiscoveryRun> RUN = AgendaDiscoveryRepository::mapRun;

    private static AgendaDiscoveryRun mapRun(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AgendaDiscoveryRun(
                resultSet.getLong("id"),
                DiscoveryScope.valueOf(resultSet.getString("scope")),
                AgendaDiscoveryRun.Status.valueOf(resultSet.getString("status")),
                resultSet.getString("cursor"),
                resultSet.getInt("pages_read"),
                resultSet.getInt("examined"),
                resultSet.getInt("new_count"),
                resultSet.getInt("already_known"),
                resultSet.getInt("ignored"),
                resultSet.getInt("errors"),
                (Integer) resultSet.getObject("provider_total"),
                resultSet.getString("stop_reason"),
                toLocalDateTime(resultSet.getTimestamp("started_at")),
                toLocalDateTime(resultSet.getTimestamp("updated_at")),
                toLocalDateTime(resultSet.getTimestamp("ended_at")));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
