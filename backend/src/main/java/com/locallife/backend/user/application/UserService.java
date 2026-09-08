package com.locallife.backend.user.application;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Service User — minimal, simple délégation vers le repository.
 */
@Service
public class UserService {

    // Dupliqué depuis AuthService (aucune classe de validation partagée
    // n'existe dans ce projet pour l'instant) : même règle qu'à
    // l'inscription, voir AuthService#validateCredentials.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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

    /**
     * Modifie le profil d'un utilisateur (LL-EF-006, « informations
     * autorisées ») : uniquement {@code username}/{@code email} —
     * {@code role} et {@code passwordHash} ne sont jamais modifiables par
     * cet endpoint (changement de mot de passe hors périmètre de ce
     * ticket, voir sa section « Prévoir une structure pouvant accueillir
     * de futures fonctionnalités utilisateur »). {@code id} est fourni
     * par l'appelant ({@code UserController}, résolu depuis le JWT — un
     * utilisateur ne peut modifier que son propre profil, jamais un id
     * arbitraire) plutôt qu'accepté dans le corps de la requête.
     *
     * @throws IllegalArgumentException si les entrées sont invalides ou si l'email est déjà utilisé
     *         par un autre compte
     * @throws java.util.NoSuchElementException si {@code id} ne correspond à aucun utilisateur
     *         (ne devrait pas se produire pour un JWT valide, aucune suppression de compte
     *         n'existant encore — voir {@code UserController})
     */
    public User updateProfile(Long id, String username, String email) {
        User existing = userRepository.findById(id).orElseThrow();
        validateProfileUpdate(username, email, id);
        User updated = new User(
                existing.id(), username, email, existing.passwordHash(), existing.role(), existing.createdAt());
        return userRepository.save(updated);
    }

    private void validateProfileUpdate(String username, String email, Long currentUserId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur est requis.");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("L'adresse email n'est pas valide.");
        }
        Optional<User> ownerOfEmail = userRepository.findByEmail(email);
        if (ownerOfEmail.isPresent() && !ownerOfEmail.get().id().equals(currentUserId)) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }
    }

}
