package com.locallife.backend.auth.application;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

/**
 * Vérifie les ID tokens Google (LL-EF-011, OpenID Connect) et en extrait
 * l'identité (sub/email/nom). Utilisé exclusivement par
 * {@code AuthService#loginOrRegisterWithGoogle} : ce service ne connaît
 * rien de {@code User}/{@code UserIdentity}, seulement du protocole
 * Google — cohérent avec la demande explicite du ticket de ne pas faire
 * dépendre l'authentification classique du fonctionnement spécifique de
 * Google (voir {@code V28__create_user_identities_table.sql}).
 *
 * <b>Vérification, pas simple décodage</b> : {@code jwtDecoder.decode}
 * valide la signature RS256 (clé publique Google, récupérée et mise en
 * cache via JWKS — {@code NimbusJwtDecoder} gère la rotation de clé),
 * l'émetteur ({@code https://accounts.google.com}), l'expiration, et
 * l'audience (notre {@code google.client-id} — sans cette vérification,
 * un token émis par Google pour une application tierce quelconque serait
 * accepté ici, une faille classique de l'intégration OIDC). C'est cette
 * étape, et non le seul appel réseau à Google, qui constitue la
 * « vérification correcte de l'identité retournée par Google côté
 * backend » exigée par le ticket (section « Sécurité ») — jamais un
 * champ du corps de la requête n'est utilisé comme preuve d'identité, y
 * compris l'email : il n'est lu que depuis le token une fois sa signature
 * validée, jamais transmis séparément par le frontend (voir
 * {@code GoogleLoginRequest}, un seul champ : le token brut).
 *
 * @throws IllegalArgumentException si le token n'a pas pu être vérifié
 *         (signature invalide, expiré, mauvaise audience/émetteur) ou si
 *         l'email retourné par Google n'est pas marqué comme vérifié par
 *         Google lui-même ({@code email_verified}) — un email non vérifié
 *         ne doit jamais servir à créer ou associer un compte (une
 *         personne pourrait enregistrer n'importe quelle adresse chez
 *         certains fournisseurs OAuth sans jamais la posséder). Ce
 *         service lève volontairement la même exception que le reste du
 *         module {@code auth} ({@code AuthService#login}) plutôt qu'un
 *         type dédié : {@code AuthController} n'a ainsi qu'un seul type
 *         d'échec à traiter pour tous les flux d'authentification.
 */
@Service
public class GoogleAuthService {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final JwtDecoder jwtDecoder;

    @Autowired
    public GoogleAuthService(@Value("${google.client-id}") String googleClientId) {
        this(buildJwtDecoder(googleClientId));
    }

    /**
     * Constructeur package-private, réservé aux tests
     * ({@code GoogleAuthServiceTest}) : permet d'injecter un
     * {@link JwtDecoder} simulé plutôt qu'un {@link NimbusJwtDecoder} réel,
     * qui contacterait Google au premier {@link #verify}. La logique
     * testée (claims, {@code email_verified}, mapping vers
     * {@link GoogleIdentity}) est indépendante du mécanisme de
     * vérification de signature lui-même.
     */
    GoogleAuthService(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    private static JwtDecoder buildJwtDecoder(String googleClientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER);
        OAuth2TokenValidator<Jwt> withAudience = jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(googleClientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Ce token Google n'est pas destiné à cette application.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException exception) {
            throw new IllegalArgumentException("Token Google invalide ou expiré.", exception);
        }

        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        if (emailVerified == null || !emailVerified) {
            throw new IllegalArgumentException("L'adresse email associée à ce compte Google n'est pas vérifiée.");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google n'a retourné aucune adresse email pour ce compte.");
        }

        return new GoogleIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }

    /**
     * Identité extraite d'un ID token Google vérifié.
     *
     * @param sub identifiant stable du compte Google (claim {@code sub}) —
     *            jamais l'email, voir {@code UserIdentity#providerUserId}.
     * @param email adresse email vérifiée par Google.
     * @param name nom affiché côté Google, {@code null} si absent du
     *             token — utilisé uniquement comme repli pour le nom
     *             d'utilisateur LocalLife lors d'une première connexion
     *             (voir {@code AuthService#loginOrRegisterWithGoogle}).
     */
    public record GoogleIdentity(String sub, String email, String name) {
    }
}
