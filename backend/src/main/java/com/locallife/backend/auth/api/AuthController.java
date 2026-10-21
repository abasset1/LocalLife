package com.locallife.backend.auth.api;

import com.locallife.backend.auth.application.AuthService;
import com.locallife.backend.auth.application.AccountSuspendedException;
import com.locallife.backend.auth.application.EmailNotVerifiedException;
import com.locallife.backend.auth.application.EmailVerificationService;
import com.locallife.backend.auth.application.PasswordResetService;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.user.application.UsernamePolicy;
import com.locallife.backend.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour les endpoints d'authentification.
 * Fournit POST /api/v1/auth/register (inscription), POST /api/v1/auth/login
 * (connexion), POST /api/v1/auth/google (LL-EF-011, connexion/inscription
 * via Google) et, depuis LL-EF-007, POST /api/v1/auth/forgot-password et
 * POST /api/v1/auth/reset-password (réinitialisation de mot de passe), puis
 * POST /api/v1/auth/verify-email et POST /api/v1/auth/resend-verification
 * (confirmation de l'adresse email après inscription), et GET
 * /api/v1/auth/username-availability (LS-000, nom d'utilisateur libre ?).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String GENERIC_FORGOT_PASSWORD_MESSAGE =
            "Si un compte existe pour cette adresse, un email de réinitialisation vient d'être envoyé.";

    private final AuthService authService;
    private static final String GENERIC_RESEND_VERIFICATION_MESSAGE =
            "Si un compte non confirmé existe pour cette adresse, un nouvel email de confirmation vient d'être envoyé.";

    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final UsernamePolicy usernamePolicy;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            EmailVerificationService emailVerificationService,
            UsernamePolicy usernamePolicy) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
        this.usernamePolicy = usernamePolicy;
    }

    /**
     * Indique si un nom d'utilisateur peut être choisi (LS-000), pour que les
     * formulaires d'inscription et de profil le signalent pendant la saisie.
     * Public : le nom d'utilisateur est un identifiant public, savoir s'il est
     * pris ne révèle rien de plus que la recherche d'utilisateurs.
     * L'inscription et la modification du profil revérifient toujours.
     */
    @GetMapping("/username-availability")
    public UsernameAvailabilityResponse usernameAvailability(@RequestParam("username") String username) {
        return usernamePolicy.unavailabilityReason(username)
                .map(reason -> new UsernameAvailabilityResponse(false, reason))
                .orElseGet(() -> new UsernameAvailabilityResponse(true, null));
    }

    /**
     * Réponse de {@link #usernameAvailability} : {@code message} explique un
     * refus, {@code null} quand le nom est libre.
     */
    public record UsernameAvailabilityResponse(boolean available, String message) {
    }

    /**
     * Endpoint pour l'inscription d'un nouvel utilisateur.
     *
     * <p>Le compte est créé avec une adresse non vérifiée et un email de
     * confirmation est envoyé : la connexion reste refusée (403) tant que le
     * lien n'a pas été suivi.
     *
     * @param request la requête d'inscription (username, email, password)
     * @return l'utilisateur créé (sans passwordHash) si l'inscription réussit
     */
    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            User user = authService.register(request.username(), request.email(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception, httpRequest);
        }
    }

    /**
     * Endpoint pour la connexion d'un utilisateur.
     *
     * @param request la requête de login contenant email et password
     * @return une réponse contenant le token JWT si l'authentification réussit ;
     *         401 si les identifiants sont incorrects ; 403 si les identifiants
     *         sont corrects mais que l'adresse email n'est pas encore confirmée
     */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException exception) {
            return unauthorized(exception, httpRequest);
        } catch (EmailNotVerifiedException exception) {
            HttpStatus status = HttpStatus.FORBIDDEN;
            ErrorResponse body = new ErrorResponse(
                    Instant.now(), status.value(), status.getReasonPhrase(),
                    exception.getMessage(), httpRequest.getRequestURI());
            return ResponseEntity.status(status).body(body);
        } catch (AccountSuspendedException exception) {
            return suspended(exception, httpRequest);
        }
    }

    /** ACC-02 : compte suspendu, identifiants pourtant corrects (423, distinct du 403 « adresse non confirmée »). */
    private ResponseEntity<Object> suspended(AccountSuspendedException exception, HttpServletRequest httpRequest) {
        HttpStatus status = HttpStatus.LOCKED;
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), exception.getMessage(), httpRequest.getRequestURI()));
    }

    /**
     * Endpoint pour la connexion ou l'inscription via Google (LL-EF-011).
     *
     * <p>Le corps de la requête ne contient que le token brut retourné par
     * Google côté frontend ({@code idToken}) — jamais un email ou un nom
     * fourni séparément : ces informations ne sont lues qu'une fois le
     * token vérifié côté serveur (voir {@code GoogleAuthService}), jamais
     * transmises telles quelles par le frontend (exigence explicite du
     * ticket : « ne jamais considérer une simple adresse e-mail fournie
     * par le frontend comme une preuve d'identité »).
     *
     * <p>401 plutôt que 400 en cas d'échec, même posture que
     * {@link #login} ci-dessus : un token Google invalide/expiré est
     * fonctionnellement un échec d'authentification, pas une erreur de
     * saisie.
     *
     * @param request la requête contenant l'ID token Google
     * @return une réponse contenant le token JWT LocalLife si l'authentification réussit
     */
    @PostMapping("/google")
    public ResponseEntity<Object> loginWithGoogle(
            @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        try {
            LoginResponse response = authService.loginOrRegisterWithGoogle(request.idToken());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException exception) {
            return unauthorized(exception, httpRequest);
        } catch (AccountSuspendedException exception) {
            return suspended(exception, httpRequest);
        }
    }

    /**
     * Endpoint pour demander une réinitialisation de mot de passe.
     *
     * <p>Renvoie systématiquement 200 avec le même message générique, que
     * l'email corresponde ou non à un compte existant (LL-EF-007 : pas
     * d'énumération des comptes). Seul un email malformé/absent renvoie
     * 400, ce qui ne constitue pas une fuite d'existence de compte.
     *
     * @param request la requête contenant l'email
     * @return un message générique de confirmation
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Object> forgotPassword(
            @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            passwordResetService.requestReset(request.email());
            return ResponseEntity.ok(new MessageResponse(GENERIC_FORGOT_PASSWORD_MESSAGE));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception, httpRequest);
        }
    }

    /**
     * Endpoint pour définir un nouveau mot de passe à partir d'un token de
     * réinitialisation reçu par email.
     *
     * @param request la requête contenant le token et le nouveau mot de passe
     * @return un message de confirmation si la réinitialisation réussit
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Object> resetPassword(
            @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            passwordResetService.resetPassword(request.token(), request.newPassword());
            return ResponseEntity.ok(new MessageResponse("Mot de passe mis à jour avec succès."));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception, httpRequest);
        }
    }

    /**
     * Endpoint pour confirmer l'adresse email à partir du token reçu par email.
     *
     * @param request la requête contenant le token
     * @return un message de confirmation, ou 400 si le token est invalide, expiré ou déjà utilisé
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Object> verifyEmail(
            @RequestBody VerifyEmailRequest request, HttpServletRequest httpRequest) {
        try {
            emailVerificationService.verify(request.token());
            return ResponseEntity.ok(new MessageResponse("Adresse email confirmée. Vous pouvez vous connecter."));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception, httpRequest);
        }
    }

    /**
     * Endpoint pour renvoyer le lien de confirmation d'adresse email.
     *
     * <p>Renvoie systématiquement 200 avec le même message générique, que
     * l'email corresponde ou non à un compte non confirmé (pas d'énumération
     * des comptes, même posture que {@link #forgotPassword}).
     *
     * @param request la requête contenant l'email
     * @return un message générique de confirmation
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Object> resendVerification(
            @RequestBody ResendVerificationRequest request, HttpServletRequest httpRequest) {
        try {
            emailVerificationService.resend(request.email());
            return ResponseEntity.ok(new MessageResponse(GENERIC_RESEND_VERIFICATION_MESSAGE));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception, httpRequest);
        }
    }

    private ResponseEntity<Object> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(errorBody(exception, status, request));
    }

    private ResponseEntity<Object> unauthorized(IllegalArgumentException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(errorBody(exception, status, request));
    }

    private ErrorResponse errorBody(IllegalArgumentException exception, HttpStatus status, HttpServletRequest request) {
        return new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(),
                exception.getMessage(), request.getRequestURI());
    }
}
