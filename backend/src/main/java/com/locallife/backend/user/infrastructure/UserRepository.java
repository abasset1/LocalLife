package com.locallife.backend.user.infrastructure;

import com.locallife.backend.user.domain.User;
import java.util.Optional;
import org.springframework.data.repository.Repository;

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

}
