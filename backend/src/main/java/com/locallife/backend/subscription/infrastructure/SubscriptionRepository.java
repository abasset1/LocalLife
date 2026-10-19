package com.locallife.backend.subscription.infrastructure;

import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.subscription.domain.Subscription;
import com.locallife.backend.subscription.domain.SubscriptionStatus;
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

/** Abonnements et leur journal (BILLING-01, ADR-0014 ; tables {@code subscription}, {@code subscription_event}). */
@Repository
public class SubscriptionRepository {

    private static final String SELECT = """
            SELECT s.*, u.email FROM subscription s JOIN users u ON u.id = s.user_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SubscriptionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Nouvelle demande ; renvoie son id.
     *
     * @throws org.springframework.dao.DuplicateKeyException une demande est déjà en attente
     */
    public Long insertRequest(Long userId, Plan plan) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO subscription (user_id, plan, status) VALUES (:userId, :plan, 'REQUESTED')",
                new MapSqlParameterSource("userId", userId).addValue("plan", plan.name()), key, new String[] {"id"});
        return key.getKey().longValue();
    }

    public Optional<Subscription> findById(Long id) {
        return jdbc.query(SELECT + "WHERE s.id = :id", new MapSqlParameterSource("id", id),
                (row, index) -> toSubscription(row)).stream().findFirst();
    }

    /** Abonnement en cours (actif ou résilié non encore expiré par la tâche planifiée). */
    public Optional<Subscription> findCurrent(Long userId) {
        return jdbc.query(SELECT + "WHERE s.user_id = :userId AND s.status IN ('ACTIVE', 'CANCELED')",
                new MapSqlParameterSource("userId", userId), (row, index) -> toSubscription(row)).stream().findFirst();
    }

    public Optional<Subscription> findRequested(Long userId) {
        return jdbc.query(SELECT + "WHERE s.user_id = :userId AND s.status = 'REQUESTED'",
                new MapSqlParameterSource("userId", userId), (row, index) -> toSubscription(row)).stream().findFirst();
    }

    public List<Subscription> findHistory(Long userId, int limit) {
        return jdbc.query(SELECT + "WHERE s.user_id = :userId ORDER BY s.updated_at DESC, s.id DESC LIMIT :limit",
                new MapSqlParameterSource("userId", userId).addValue("limit", limit),
                (row, index) -> toSubscription(row));
    }

    public List<Subscription> findByStatuses(Collection<SubscriptionStatus> statuses, int limit, int offset) {
        return jdbc.query(SELECT + """
                WHERE s.status IN (:statuses) ORDER BY s.created_at, s.id LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("statuses", statuses.stream().map(Enum::name).toList())
                .addValue("limit", limit).addValue("offset", offset), (row, index) -> toSubscription(row));
    }

    /**
     * Change le statut s'il vaut toujours {@code from}, et la période si elle est fournie ; {@code false} si
     * l'abonnement a changé entre-temps.
     */
    public boolean transition(Long id, SubscriptionStatus from, SubscriptionStatus to, Change change) {
        return jdbc.update("""
                UPDATE subscription SET status = :to, updated_at = now(),
                       current_period_start = CASE WHEN CAST(:setStart AS boolean) THEN CAST(:start AS timestamptz)
                                                   ELSE current_period_start END,
                       current_period_end = CASE WHEN CAST(:setEnd AS boolean) THEN CAST(:end AS timestamptz)
                                                 ELSE current_period_end END,
                       cancel_at_period_end = COALESCE(CAST(:cancelAtPeriodEnd AS boolean), cancel_at_period_end),
                       decided_by = COALESCE(CAST(:decidedBy AS bigint), decided_by),
                       decision_reason = COALESCE(CAST(:reason AS text), decision_reason)
                WHERE id = :id AND status = :from
                """, new MapSqlParameterSource("id", id).addValue("from", from.name()).addValue("to", to.name())
                .addValue("setStart", change.setStart()).addValue("start", change.start())
                .addValue("setEnd", change.setEnd()).addValue("end", change.end())
                .addValue("cancelAtPeriodEnd", change.cancelAtPeriodEnd()).addValue("decidedBy", change.decidedBy())
                .addValue("reason", change.reason())) == 1;
    }

    /**
     * Ce qu'une transition change en plus du statut ; {@code setStart}/{@code setEnd} : écrire la valeur, même nulle.
     */
    public record Change(
            boolean setStart, OffsetDateTime start, boolean setEnd, OffsetDateTime end, Boolean cancelAtPeriodEnd,
            Long decidedBy, String reason) {

        public static Change none() {
            return new Change(false, null, false, null, null, null, null);
        }
    }

    /** Abonnements en cours dont la période est échue. */
    public List<Subscription> findDue(OffsetDateTime now) {
        return jdbc.query(SELECT + """
                WHERE s.status IN ('ACTIVE', 'CANCELED') AND s.current_period_end IS NOT NULL
                  AND s.current_period_end <= :now
                """, new MapSqlParameterSource("now", now), (row, index) -> toSubscription(row));
    }

    public void insertEvent(Long subscriptionId, Long actorId, SubscriptionStatus from, SubscriptionStatus to,
            String reason) {
        jdbc.update("""
                INSERT INTO subscription_event (subscription_id, actor_id, from_status, to_status, reason)
                VALUES (:subscriptionId, :actorId, :from, :to, :reason)
                """, new MapSqlParameterSource("subscriptionId", subscriptionId).addValue("actorId", actorId)
                .addValue("from", from == null ? null : from.name()).addValue("to", to.name())
                .addValue("reason", reason));
    }

    private static Subscription toSubscription(ResultSet row) throws SQLException {
        return new Subscription(row.getLong("id"), row.getLong("user_id"), row.getString("email"),
                Plan.valueOf(row.getString("plan")), SubscriptionStatus.valueOf(row.getString("status")),
                row.getObject("current_period_start", OffsetDateTime.class),
                row.getObject("current_period_end", OffsetDateTime.class), row.getBoolean("cancel_at_period_end"),
                row.getString("provider"), row.getString("decision_reason"),
                row.getObject("created_at", OffsetDateTime.class), row.getObject("updated_at", OffsetDateTime.class));
    }
}
