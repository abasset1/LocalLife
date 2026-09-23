package com.locallife.backend.auth.infrastructure;

import com.locallife.backend.auth.domain.EmailVerificationToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository EmailVerificationToken — uniquement les opérations nécessaires
 * à la vérification de l'adresse email.
 *
 * <p>Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository}, même patron que {@code PasswordResetTokenRepository}.
 */
public interface EmailVerificationTokenRepository extends Repository<EmailVerificationToken, Long> {

    EmailVerificationToken save(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    List<EmailVerificationToken> findAllByUserIdAndUsedFalse(Long userId);

}
