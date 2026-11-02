package com.locallife.backend.pro.infrastructure;

import com.locallife.backend.user.domain.Role;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Rôle d'un compte, relu en base pour l'accès professionnel. Depuis V81, le rôle ne donne plus l'espace
 * professionnel (sauf à un administrateur) : c'est le type de compte, un abonnement Pro.
 */
@Repository
public class ProAccountRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Role> findRole(Long userId) {
        return jdbc.query("SELECT role FROM users WHERE id = :id", new MapSqlParameterSource("id", userId),
                (row, index) -> Role.valueOf(row.getString("role"))).stream().findFirst();
    }
}
