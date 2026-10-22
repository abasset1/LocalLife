package com.locallife.backend.user.application;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UsernamePolicy usernamePolicy;

    public UserService(UserRepository userRepository, UsernamePolicy usernamePolicy) {
        this.userRepository = userRepository;
        this.usernamePolicy = usernamePolicy;
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Modifie le profil d'un utilisateur (LL-EF-006, « informations
     * autorisées ») : uniquement {@code username}/{@code email} — le nom
     * suit les règles de {@link UsernamePolicy} (LS-000) —
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
        String validUsername = validateProfileUpdate(username, email, id);
        User updated = new User(
                existing.id(), validUsername, email, existing.passwordHash(), existing.role(), existing.createdAt(),
                existing.emailVerified());
        return usernamePolicy.saveGuarded(() -> userRepository.save(updated));
    }

    /**
     * Attribue ou retire le rôle professionnel (ADR-0012, décision D1) : seules les bascules {@code USER} ↔
     * {@code PRO} sont permises. Un compte {@code ADMIN} n'est jamais modifié, et aucun compte n'est promu
     * {@code ADMIN} par cette voie (même règle que le bootstrap administrateur).
     *
     * @throws IllegalArgumentException rôle cible autre que {@code USER}/{@code PRO}, ou compte administrateur
     * @throws java.util.NoSuchElementException si {@code id} ne correspond à aucun utilisateur
     */
    public User changeRole(Long id, Role role) {
        if (role != Role.USER && role != Role.PRO) {
            throw new IllegalArgumentException("Seuls les rôles USER et PRO peuvent être attribués.");
        }
        User existing = userRepository.findById(id).orElseThrow();
        if (existing.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Le rôle d'un administrateur ne se modifie pas ici.");
        }
        if (existing.role() == role) {
            return existing;
        }
        User updated = new User(
                existing.id(), existing.username(), existing.email(), existing.passwordHash(), role,
                existing.createdAt(), existing.emailVerified());
        LOGGER.info("Rôle de l'utilisateur {} : {} → {}", id, existing.role(), role);
        return userRepository.save(updated);
    }

    public Optional<User> findByEmail(String email) {
        return email == null ? Optional.empty() : userRepository.findByEmail(email.trim());
    }

    private String validateProfileUpdate(String username, String email, Long currentUserId) {
        String validUsername = usernamePolicy.validate(username, currentUserId);
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("L'adresse email n'est pas valide.");
        }
        Optional<User> ownerOfEmail = userRepository.findByEmail(email);
        if (ownerOfEmail.isPresent() && !ownerOfEmail.get().id().equals(currentUserId)) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }
        return validUsername;
    }

}
