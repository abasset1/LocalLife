package com.locallife.backend.user.api;

import com.locallife.backend.auth.api.UserResponse;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.User;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la gestion des utilisateurs.
 * Création et consultation par id.
 *
 * Correctif (fuite signalée en LL-3010) : les réponses exposaient
 * auparavant l'entité {@code User} complète, y compris {@code
 * passwordHash} (le hash BCrypt, jamais du texte en clair, mais qui ne
 * doit jamais transiter dans une réponse API). Les deux endpoints
 * utilisent désormais {@link UserResponse}, la même projection sûre déjà
 * utilisée par {@code POST /api/v1/auth/register} (LL-3007).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.username(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        Optional<User> user = userService.getUserById(id);
        return user.map(u -> ResponseEntity.ok(UserResponse.from(u)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Corps de requête pour la création d'un utilisateur : uniquement les
     * champs fournis par le client (id et createdAt sont générés côté
     * serveur).
     */
    public record CreateUserRequest(String username, String email) {
    }

}
