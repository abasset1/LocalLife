package com.locallife.backend.place.infrastructure;

import com.locallife.backend.place.domain.ClaimStatus;
import com.locallife.backend.place.domain.PlaceClaim;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Demandes d'administration de lieux et leur historique (ADR-0012, § 3.1 : tables {@code place_claim} et
 * {@code place_claim_event}). N'écrit jamais dans {@code location}.
 */
@Repository
public class PlaceClaimRepository {

    /** Transition enregistrée dans l'historique d'une demande. */
    public record ClaimEvent(
            Long actorId, ClaimStatus fromStatus, ClaimStatus toStatus, String reason, OffsetDateTime occurredAt) {
    }

    private static final String CLAIM_SELECT = """
            SELECT c.*, l.name AS place_name, l.city AS place_city, u.email AS requester_email
            FROM place_claim c
            JOIN location l ON l.id = c.location_id
            LEFT JOIN users u ON u.id = c.requester_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PlaceClaimRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Nouvelle demande en brouillon ; renvoie son id.
     *
     * @throws org.springframework.dao.DuplicateKeyException une demande active existe déjà pour ce lieu et ce
     *         professionnel ({@code uq_place_claim_active})
     */
    public Long insertDraft(Long locationId, Long requesterId, String relationship, String evidence) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO place_claim (location_id, requester_id, status, relationship, evidence)
                VALUES (:locationId, :requesterId, 'DRAFT', :relationship, :evidence)
                """, new MapSqlParameterSource("locationId", locationId).addValue("requesterId", requesterId)
                .addValue("relationship", relationship).addValue("evidence", evidence), key, new String[] {"id"});
        return key.getKey().longValue();
    }

    public Optional<PlaceClaim> findById(Long id) {
        return jdbc.query(CLAIM_SELECT + "WHERE c.id = :id", new MapSqlParameterSource("id", id),
                (row, index) -> toClaim(row)).stream().findFirst();
    }

    public List<PlaceClaim> findByRequester(Long requesterId) {
        return jdbc.query(CLAIM_SELECT + "WHERE c.requester_id = :requesterId ORDER BY c.updated_at DESC, c.id DESC",
                new MapSqlParameterSource("requesterId", requesterId), (row, index) -> toClaim(row));
    }

    /** File de modération : les plus anciennes soumissions d'abord. */
    public List<PlaceClaim> findByStatuses(Collection<ClaimStatus> statuses, int limit, int offset) {
        return jdbc.query(CLAIM_SELECT + """
                WHERE c.status IN (:statuses)
                ORDER BY c.submitted_at NULLS LAST, c.id
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("statuses", statuses.stream().map(Enum::name).toList())
                .addValue("limit", limit).addValue("offset", offset), (row, index) -> toClaim(row));
    }

    /** Demandes en cours sur un lieu, brouillons exclus (le modérateur ne voit que ce qui lui a été envoyé). */
    public List<PlaceClaim> findSubmittedForLocation(Long locationId) {
        return jdbc.query(CLAIM_SELECT + """
                WHERE c.location_id = :locationId AND c.status IN ('SUBMITTED', 'IN_REVIEW', 'INFO_REQUESTED')
                ORDER BY c.submitted_at, c.id
                """, new MapSqlParameterSource("locationId", locationId), (row, index) -> toClaim(row));
    }

    public void updateText(Long id, String relationship, String evidence) {
        jdbc.update("""
                UPDATE place_claim SET relationship = :relationship, evidence = :evidence, updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("relationship", relationship)
                .addValue("evidence", evidence));
    }

    /**
     * Change le statut si la demande est toujours dans le statut lu (deux modérateurs ne décident pas la même
     * demande). Renvoie {@code false} si elle a changé entre-temps.
     */
    public boolean transition(Long id, ClaimStatus from, ClaimStatus to, Long moderatorId, String reason) {
        boolean decision = to == ClaimStatus.APPROVED || to == ClaimStatus.REJECTED
                || to == ClaimStatus.INFO_REQUESTED;
        return jdbc.update("""
                UPDATE place_claim SET status = :to, updated_at = now(),
                       submitted_at = CASE WHEN :to = 'SUBMITTED' THEN now() ELSE submitted_at END,
                       decided_at = CASE WHEN :decision THEN now() ELSE decided_at END,
                       decision_reason = CASE WHEN :decision THEN :reason ELSE decision_reason END,
                       moderator_id = COALESCE(:moderatorId, moderator_id)
                WHERE id = :id AND status = :from
                """, new MapSqlParameterSource("id", id).addValue("from", from.name()).addValue("to", to.name())
                .addValue("decision", decision).addValue("reason", reason).addValue("moderatorId", moderatorId))
                == 1;
    }

    public void insertEvent(Long claimId, Long actorId, ClaimStatus from, ClaimStatus to, String reason) {
        jdbc.update("""
                INSERT INTO place_claim_event (claim_id, actor_id, from_status, to_status, reason)
                VALUES (:claimId, :actorId, :from, :to, :reason)
                """, new MapSqlParameterSource("claimId", claimId).addValue("actorId", actorId)
                .addValue("from", from == null ? null : from.name()).addValue("to", to.name())
                .addValue("reason", reason));
    }

    public List<ClaimEvent> findEvents(Long claimId) {
        return jdbc.query("""
                SELECT actor_id, from_status, to_status, reason, occurred_at FROM place_claim_event
                WHERE claim_id = :claimId ORDER BY id
                """, new MapSqlParameterSource("claimId", claimId), (row, index) -> new ClaimEvent(
                        row.getObject("actor_id", Long.class),
                        row.getString("from_status") == null ? null : ClaimStatus.valueOf(row.getString("from_status")),
                        ClaimStatus.valueOf(row.getString("to_status")), row.getString("reason"),
                        row.getObject("occurred_at", OffsetDateTime.class)));
    }

    /**
     * Efface le lien déclaré et les justificatifs des demandes closes avant {@code closedBefore} (décision D4). Une
     * demande close n'est plus modifiée : sa dernière modification est sa clôture. Renvoie le nombre de demandes
     * effacées.
     */
    public int purgeEvidence(OffsetDateTime closedBefore) {
        return jdbc.update("""
                UPDATE place_claim SET relationship = NULL, evidence = NULL, evidence_purged_at = now()
                WHERE evidence_purged_at IS NULL
                  AND status IN ('APPROVED', 'REJECTED', 'WITHDRAWN')
                  AND updated_at < :closedBefore
                """, new MapSqlParameterSource("closedBefore", closedBefore));
    }

    private static PlaceClaim toClaim(ResultSet row) throws SQLException {
        return new PlaceClaim(
                row.getLong("id"), row.getLong("location_id"), row.getString("place_name"),
                row.getString("place_city"), row.getObject("requester_id", Long.class),
                row.getString("requester_email"), ClaimStatus.valueOf(row.getString("status")),
                row.getString("relationship"), row.getString("evidence"), row.getString("decision_reason"),
                row.getObject("moderator_id", Long.class), row.getObject("created_at", OffsetDateTime.class),
                row.getObject("submitted_at", OffsetDateTime.class), row.getObject("decided_at", OffsetDateTime.class),
                row.getObject("updated_at", OffsetDateTime.class),
                row.getObject("evidence_purged_at", OffsetDateTime.class));
    }
}
