package com.locallife.backend.pro.infrastructure;

import com.locallife.backend.user.domain.Role;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Rôle d'un compte et origine de son rôle {@code PRO} ({@code users.pro_role_source}, V71) : {@code ADMIN} quand un
 * administrateur l'a attribué, {@code SUBSCRIPTION} quand il découle d'une offre Pro validée. Colonne hors de
 * l'entité {@code User} : enregistrer un {@code User} ne la touche jamais.
 */
@Repository
public class ProAccountRepository {

    /** Origine du rôle {@code PRO}. */
    public enum ProRoleSource {
        ADMIN, SUBSCRIPTION
    }

    /** Rôle d'un compte et origine de son rôle {@code PRO} ({@code null} pour les autres rôles). */
    public record RoleState(Role role, ProRoleSource source) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ProAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RoleState> findRoleState(Long userId) {
        return jdbc.query("SELECT role, pro_role_source FROM users WHERE id = :id",
                new MapSqlParameterSource("id", userId), (row, index) -> new RoleState(
                        Role.valueOf(row.getString("role")),
                        row.getString("pro_role_source") == null ? null
                                : ProRoleSource.valueOf(row.getString("pro_role_source"))))
                .stream().findFirst();
    }

    /**
     * Pose le rôle et son origine, sans jamais toucher un administrateur ; renvoie {@code false} si rien n'a changé.
     */
    public boolean setRole(Long userId, Role role, ProRoleSource source) {
        return jdbc.update("""
                UPDATE users SET role = :role, pro_role_source = :source
                WHERE id = :id AND role <> 'ADMIN'
                  AND (role <> :role OR pro_role_source IS DISTINCT FROM :source)
                """, new MapSqlParameterSource("id", userId).addValue("role", role.name())
                .addValue("source", source == null ? null : source.name())) == 1;
    }
}
