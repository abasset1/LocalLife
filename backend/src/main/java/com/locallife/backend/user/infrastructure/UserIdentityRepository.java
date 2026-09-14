package com.locallife.backend.user.infrastructure;

import com.locallife.backend.user.domain.AuthProvider;
import com.locallife.backend.user.domain.UserIdentity;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository UserIdentity (LL-EF-011). Étend {@link Repository}
 * (interface marqueur) plutôt que {@code CrudRepository}, même
 * convention que {@link UserRepository}/{@code ActivityRepository} :
 * seules les méthodes explicitement listées ici sont disponibles.
 */
public interface UserIdentityRepository extends Repository<UserIdentity, Long> {

    UserIdentity save(UserIdentity userIdentity);

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
