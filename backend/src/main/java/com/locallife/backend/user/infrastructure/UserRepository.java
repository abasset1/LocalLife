package com.locallife.backend.user.infrastructure;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Repository User — uniquement les opérations nécessaires au Sprint 2 :
 * création d'un compte et recherche.
 *
 * Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository} : seules les méthodes explicitement listées ici
 * sont disponibles.
 */
public interface UserRepository extends Repository<User, Long> {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    /**
     * Recherche par nom d'utilisateur sans tenir compte de la casse (LS-000),
     * servie par l'index {@code users_username_lower_key}.
     */
    @Query("SELECT * FROM users WHERE lower(username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    boolean existsByRole(Role role);

}
