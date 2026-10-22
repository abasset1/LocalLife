package com.locallife.backend.user.infrastructure;

import com.locallife.backend.user.domain.AccountStatus;
import com.locallife.backend.user.domain.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Lecture des comptes pour l'administration et le contrôle d'accès, et seule écriture du statut de compte
 * ({@code users.account_status}, V69). Le statut n'est pas porté par l'entité {@code User} : une sauvegarde d'un
 * {@code User} ne peut donc jamais le modifier. Ne lit jamais {@code password_hash}.
 */
@Repository
public class AccountRepository {

    private static final String ACCOUNT_COLUMNS = "id, username, email, role, email_verified, created_at, "
            + "account_status, status_reason, status_changed_at";

    private final JdbcTemplate jdbcTemplate;

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Droits courants d'un compte, relus à chaque requête authentifiée.
     *
     * @param tokensValidAfter jetons émis avant cette date (UTC) refusés : mot de passe changé ou réinitialisé
     *                         (V70) ; {@code null} si aucun
     */
    public record Access(Long userId, String email, Role role, AccountStatus status, LocalDateTime tokensValidAfter) {
    }

    /** Compte vu par l'administration : aucun secret (ni hash de mot de passe, ni jeton). */
    public record Account(
            Long id,
            String username,
            String email,
            Role role,
            boolean emailVerified,
            LocalDateTime createdAt,
            AccountStatus status,
            String statusReason,
            LocalDateTime statusChangedAt) {
    }

    public Optional<Access> findAccess(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "SELECT id, email, role, account_status, tokens_valid_after FROM users WHERE id = ?",
                (resultSet, rowNumber) -> {
                    Timestamp validAfter = resultSet.getTimestamp("tokens_valid_after");
                    return new Access(resultSet.getLong("id"), resultSet.getString("email"),
                            Role.valueOf(resultSet.getString("role")),
                            AccountStatus.valueOf(resultSet.getString("account_status")),
                            validAfter == null ? null : validAfter.toLocalDateTime());
                }, userId)
                .stream().findFirst();
    }

    public Optional<AccountStatus> findStatus(Long userId) {
        return findAccess(userId).map(Access::status);
    }

    public Optional<Account> findById(Long id) {
        return jdbcTemplate.query("SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE id = ?", ACCOUNT, id)
                .stream().findFirst();
    }

    /**
     * @param query texte cherché dans l'adresse ou le nom d'utilisateur (insensible à la casse), ou identifiant
     *              exact ; {@code null} : tous les comptes
     */
    public List<Account> search(String query, Role role, AccountStatus status, int offset, int limit) {
        List<Object> arguments = new ArrayList<>();
        String where = where(query, role, status, arguments);
        arguments.add(limit);
        arguments.add(offset);
        return jdbcTemplate.query("SELECT " + ACCOUNT_COLUMNS + " FROM users" + where
                + " ORDER BY created_at DESC NULLS LAST, id DESC LIMIT ? OFFSET ?", ACCOUNT, arguments.toArray());
    }

    public long count(String query, Role role, AccountStatus status) {
        List<Object> arguments = new ArrayList<>();
        String where = where(query, role, status, arguments);
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM users" + where, Long.class,
                arguments.toArray());
        return count == null ? 0 : count;
    }

    /** Change le statut ; le motif et l'auteur du dernier changement restent affichés à l'administration. */
    public void updateStatus(Long id, AccountStatus status, String reason, Long changedBy, LocalDateTime at) {
        jdbcTemplate.update("UPDATE users SET account_status = ?, status_reason = ?, status_changed_by = ?, "
                + "status_changed_at = ? WHERE id = ?", status.name(), reason, changedBy, Timestamp.valueOf(at), id);
    }

    /** Refuse désormais les jetons émis avant {@code at} (UTC) : mot de passe changé ou réinitialisé. */
    public void revokeTokensIssuedBefore(Long id, LocalDateTime at) {
        jdbcTemplate.update("UPDATE users SET tokens_valid_after = ? WHERE id = ?", Timestamp.valueOf(at), id);
    }

    /** Suppression définitive du compte ; le reste suit les clés étrangères (CASCADE ou SET NULL). */
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }

    private static String where(String query, Role role, AccountStatus status, List<Object> arguments) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (query != null && !query.isBlank()) {
            String trimmed = query.trim();
            String pattern = "%" + trimmed.toLowerCase().replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            where.append(" AND (lower(email) LIKE ? OR lower(username) LIKE ?");
            arguments.add(pattern);
            arguments.add(pattern);
            if (trimmed.matches("\\d{1,18}")) {
                where.append(" OR id = ?");
                arguments.add(Long.parseLong(trimmed));
            }
            where.append(")");
        }
        if (role != null) {
            where.append(" AND role = ?");
            arguments.add(role.name());
        }
        if (status != null) {
            where.append(" AND account_status = ?");
            arguments.add(status.name());
        }
        return where.toString();
    }

    private static final RowMapper<Account> ACCOUNT = AccountRepository::mapAccount;

    private static Account mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp changedAt = resultSet.getTimestamp("status_changed_at");
        return new Account(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("email"),
                Role.valueOf(resultSet.getString("role")),
                resultSet.getBoolean("email_verified"),
                createdAt == null ? null : createdAt.toLocalDateTime(),
                AccountStatus.valueOf(resultSet.getString("account_status")),
                resultSet.getString("status_reason"),
                changedAt == null ? null : changedAt.toLocalDateTime());
    }

    /**
     * Dernière activité du compte, au jour près (RGPD, 22/10/2026) : base de la suppression des comptes inactifs.
     * Une activité retire l'avertissement d'inactivité éventuel. Au plus une écriture par jour et par compte.
     */
    public void touchActivity(Long id) {
        jdbcTemplate.update("""
                UPDATE users SET last_active_at = now(), inactivity_warned_at = NULL
                WHERE id = ? AND (last_active_at IS NULL OR last_active_at < now() - interval '1 day')
                """, id);
    }

    /** Consentement à l'inscription : date et version des conditions et de la politique de confidentialité. */
    public void recordConsent(Long id, String termsVersion) {
        jdbcTemplate.update("UPDATE users SET terms_accepted_at = now(), terms_version = ? WHERE id = ?",
                termsVersion, id);
    }
}
