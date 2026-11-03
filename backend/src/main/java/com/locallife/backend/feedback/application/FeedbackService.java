package com.locallife.backend.feedback.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggestions et anomalies envoyées par les utilisateurs connectés (table {@code feedback}, V82) : enregistrement,
 * consultation par les administrateurs, passage en « traité ». Chaque nouveau message prévient les administrateurs
 * ({@link FeedbackNotifier}).
 */
@Service
public class FeedbackService {

    public static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MIN_MESSAGE_LENGTH = 5;
    private static final int MAX_PAGE_URL_LENGTH = 500;
    private static final int MAX_USER_AGENT_LENGTH = 300;
    /** Au plus ce nombre de messages par compte et par heure : un formulaire de retour, pas une messagerie. */
    static final int MAX_PER_HOUR = 10;

    public enum Kind { SUGGESTION, BUG }

    public enum Status { NEW, DONE }

    /** Message tel que l'administration le consulte. */
    public record FeedbackView(
            Long id, Kind kind, String message, String pageUrl, String userAgent, Status status,
            LocalDateTime createdAt, LocalDateTime handledAt, Long userId, String username, String email) {
    }

    public record Page(List<FeedbackView> items, long total, long newCount) {
    }

    /** Saisie refusée (message vide, trop long, type inconnu, trop de messages). */
    public static class InvalidFeedbackException extends RuntimeException {
        public InvalidFeedbackException(String message) {
            super(message);
        }
    }

    private static final String COLUMNS = """
            f.id, f.kind, f.message, f.page_url, f.user_agent, f.status, f.created_at, f.handled_at,
            f.user_id, u.username, u.email""";

    private static final RowMapper<FeedbackView> VIEW = (rs, row) -> new FeedbackView(
            rs.getLong("id"), Kind.valueOf(rs.getString("kind")), rs.getString("message"), rs.getString("page_url"),
            rs.getString("user_agent"), Status.valueOf(rs.getString("status")), toLocal(rs.getTimestamp("created_at")),
            toLocal(rs.getTimestamp("handled_at")), rs.getLong("user_id"), rs.getString("username"),
            rs.getString("email"));

    private final JdbcTemplate jdbcTemplate;
    private final FeedbackNotifier notifier;

    public FeedbackService(JdbcTemplate jdbcTemplate, FeedbackNotifier notifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
    }

    @Transactional
    public FeedbackView submit(Long userId, String kind, String message, String pageUrl, String userAgent) {
        Kind parsedKind = parseKind(kind);
        String text = message == null ? "" : message.strip();
        if (text.length() < MIN_MESSAGE_LENGTH) {
            throw new InvalidFeedbackException("Écris quelques mots pour décrire ton idée ou le problème.");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new InvalidFeedbackException(
                    "Le message ne doit pas dépasser " + MAX_MESSAGE_LENGTH + " caractères.");
        }
        Integer recent = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM feedback WHERE user_id = ? AND created_at > ?", Integer.class, userId,
                Timestamp.valueOf(nowUtc().minusHours(1)));
        if (recent != null && recent >= MAX_PER_HOUR) {
            throw new InvalidFeedbackException("Tu as déjà envoyé beaucoup de messages : réessaie dans une heure.");
        }
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO feedback (user_id, kind, message, page_url, user_agent, created_at)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, userId, parsedKind.name(), text,
                truncate(pageUrl, MAX_PAGE_URL_LENGTH), truncate(userAgent, MAX_USER_AGENT_LENGTH),
                Timestamp.valueOf(nowUtc()));
        FeedbackView saved = findById(id).orElseThrow();
        notifier.notifyAdmins(saved);
        return saved;
    }

    /** Les plus récents d'abord ; {@code status} facultatif. */
    public Page find(Status status, int page, int size) {
        String where = status == null ? "" : " WHERE f.status = '" + status.name() + "'";
        List<FeedbackView> items = jdbcTemplate.query("SELECT " + COLUMNS
                + " FROM feedback f JOIN users u ON u.id = f.user_id" + where
                + " ORDER BY f.created_at DESC, f.id DESC LIMIT ? OFFSET ?", VIEW, size, (long) page * size);
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM feedback f" + where, Long.class);
        Long newCount = jdbcTemplate.queryForObject("SELECT count(*) FROM feedback WHERE status = 'NEW'", Long.class);
        return new Page(items, total == null ? 0 : total, newCount == null ? 0 : newCount);
    }

    /** Marque un message traité ({@code done}) ou de nouveau à traiter ; vide s'il n'existe pas. */
    @Transactional
    public Optional<FeedbackView> setDone(Long id, boolean done, Long adminId) {
        int updated = done
                ? jdbcTemplate.update(
                        "UPDATE feedback SET status = 'DONE', handled_at = ?, handled_by = ? WHERE id = ?",
                        Timestamp.valueOf(nowUtc()), adminId, id)
                : jdbcTemplate.update(
                        "UPDATE feedback SET status = 'NEW', handled_at = NULL, handled_by = NULL WHERE id = ?", id);
        return updated == 0 ? Optional.empty() : findById(id);
    }

    private Optional<FeedbackView> findById(Long id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM feedback f JOIN users u ON u.id = f.user_id "
                + "WHERE f.id = ?", VIEW, id).stream().findFirst();
    }

    private static Kind parseKind(String kind) {
        try {
            return Kind.valueOf(kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidFeedbackException("Type de message inconnu : SUGGESTION ou BUG.");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
