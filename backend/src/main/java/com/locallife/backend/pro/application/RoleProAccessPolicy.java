package com.locallife.backend.pro.application;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Accès professionnel accordé par le rôle {@code PRO} (ADR-0012, décision D1). Le rôle est relu en base à chaque
 * appel, pas dans le JWT : un rôle attribué ou retiré par un administrateur prend effet immédiatement, sans attendre
 * l'expiration du jeton.
 */
@Service
public class RoleProAccessPolicy implements ProAccessPolicy {

    private final UserRepository userRepository;

    public RoleProAccessPolicy(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean hasProAccess(Long userId) {
        return userId != null && userRepository.findById(userId).map(user -> user.role() == Role.PRO).orElse(false);
    }
}
