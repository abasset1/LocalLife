package com.locallife.backend.user.application;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service User — minimal, simple délégation vers le repository.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Crée un utilisateur sans mot de passe (héritage du Sprint 2, avant
     * l'authentification). Rôle par défaut : {@code USER}. Ce flux sera
     * probablement remplacé par {@code AuthService.register} en LL-3004,
     * qui gérera le hachage du mot de passe (LL-3003).
     */
    public User createUser(String username, String email) {
        User user = new User(null, username, email, null, Role.USER, LocalDateTime.now());
        return userRepository.save(user);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

}
