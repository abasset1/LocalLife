package com.locallife.backend.auth.infrastructure;

import com.locallife.backend.auth.domain.PasswordResetToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository PasswordResetToken — uniquement les opérations nécessaires à
 * la réinitialisation de mot de passe (LL-EF-007).
 *
 * <p>Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository}, même patron que {@code UserRepository} : seules
 * les méthodes explicitement listées ici sont disponibles.
 */
public interface PasswordResetTokenRepository extends Repository<PasswordResetToken, Long> {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findAllByUserIdAndUsedFalse(Long userId);

}
