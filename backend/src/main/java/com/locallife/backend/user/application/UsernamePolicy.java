package com.locallife.backend.user.application;

import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Règles du nom d'utilisateur (LS-000), identifiant public d'un compte.
 *
 * <ul>
 *   <li>3 à 30 caractères parmi lettres ASCII, chiffres, « . », « _ » et
 *       « - », le premier étant une lettre ou un chiffre ;</li>
 *   <li>unique sans tenir compte de la casse ;</li>
 *   <li>certains noms sont réservés et ne peuvent pas être choisis.</li>
 * </ul>
 *
 * <p>La base garantit aussi le format et l'unicité (voir
 * {@code V57__unique_username.sql}) : ce composant donne des messages clairs
 * et couvre les noms réservés, que la base ne connaît pas.
 */
@Component
public class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$");
    private static final String UNIQUE_INDEX = "users_username_lower_key";
    private static final String FALLBACK_BASE = "membre";

    private static final Set<String> RESERVED = Set.of(
            "admin", "administrateur", "administrator", "root", "system", "systeme", "support", "help", "aide",
            "contact", "moderateur", "moderator", "modo", "staff", "equipe", "team", "locallife", "local-life",
            "api", "www", "mail", "email", "noreply", "no-reply", "security", "securite", "null", "undefined",
            "anonymous", "anonyme", "me", "moi", "settings", "parametres", "profil", "profile", "compte",
            "account", "login", "logout", "register", "inscription", "connexion");

    private final UserRepository userRepository;

    public UsernamePolicy(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Valide un nom choisi par un utilisateur (inscription ou profil).
     *
     * @param currentUserId compte qui modifie son nom, {@code null} à l'inscription : son nom
     *                      actuel ne compte pas comme déjà pris, et il peut garder un nom réservé
     *                      qu'il portait déjà (comptes antérieurs à LS-000)
     * @return le nom sans espaces autour
     * @throws IllegalArgumentException avec un message destiné à l'utilisateur
     */
    public String validate(String rawUsername, Long currentUserId) {
        String username = checkFormat(rawUsername);
        Optional<User> owner = userRepository.findByUsernameIgnoreCase(username);
        boolean ownName = owner.isPresent() && owner.get().id().equals(currentUserId);
        if (owner.isPresent() && !ownName) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà pris.");
        }
        if (!ownName && isReserved(username)) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est réservé.");
        }
        return username;
    }

    /**
     * Raison pour laquelle un nom ne peut pas être choisi à l'inscription, ou
     * vide s'il est libre (vérification en direct des formulaires).
     */
    public Optional<String> unavailabilityReason(String rawUsername) {
        try {
            validate(rawUsername, null);
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.of(exception.getMessage());
        }
    }

    /**
     * Valide le nom du premier administrateur, fourni par la configuration :
     * les noms réservés (« admin » par défaut) y sont permis.
     */
    public String validateForBootstrap(String rawUsername) {
        String username = checkFormat(rawUsername);
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà pris.");
        }
        return username;
    }

    /**
     * Construit un nom libre et valide à partir d'une suggestion (nom affiché
     * Google, partie locale de l'email…) : accents retirés, caractères
     * interdits remplacés par « - », puis suffixe « -2 », « -3 »… si le nom
     * est déjà pris ou réservé.
     */
    public String generateAvailable(String seed) {
        String base = sanitize(seed);
        String candidate = base;
        int suffix = 1;
        while (isReserved(candidate) || userRepository.findByUsernameIgnoreCase(candidate).isPresent()) {
            suffix++;
            String tail = "-" + suffix;
            candidate = base.substring(0, Math.min(base.length(), MAX_LENGTH - tail.length())) + tail;
        }
        return candidate;
    }

    /**
     * Exécute un enregistrement et traduit une violation de l'index d'unicité
     * (deux comptes qui prennent le même nom au même instant) en message
     * utilisateur.
     */
    public <T> T saveGuarded(Supplier<T> save) {
        try {
            return save.get();
        } catch (DataAccessException exception) {
            if (mentionsUniqueIndex(exception)) {
                throw new IllegalArgumentException("Ce nom d'utilisateur est déjà pris.", exception);
            }
            throw exception;
        }
    }

    private static boolean mentionsUniqueIndex(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(UNIQUE_INDEX)) {
                return true;
            }
        }
        return false;
    }

    private static String checkFormat(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur est requis.");
        }
        String username = rawUsername.trim();
        if (username.length() < MIN_LENGTH || username.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Le nom d'utilisateur doit contenir entre " + MIN_LENGTH + " et " + MAX_LENGTH + " caractères.");
        }
        if (!FORMAT.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Le nom d'utilisateur ne peut contenir que des lettres sans accent, des chiffres, "
                            + "« . », « _ » et « - », et doit commencer par une lettre ou un chiffre.");
        }
        return username;
    }

    private static boolean isReserved(String username) {
        return RESERVED.contains(username.toLowerCase(Locale.ROOT));
    }

    private static String sanitize(String seed) {
        String text = seed == null ? "" : Normalizer.normalize(seed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        text = text.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^[^A-Za-z0-9]+", "");
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }
        text = text.replaceAll("[._-]+$", "");
        return text.length() < MIN_LENGTH ? FALLBACK_BASE : text;
    }
}
