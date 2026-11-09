package com.locallife.backend.auth.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.auth.infrastructure.TokenRevocationRepository;
import com.locallife.backend.user.domain.AccountStatus;
import com.locallife.backend.user.infrastructure.AccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre Spring pour extraire et valider le JWT de l'en-tête Authorization.
 * Si le token est valide, remplit le SecurityContext avec les informations de l'utilisateur.
 *
 * <p><b>Droits relus en base à chaque requête</b> (sprint « Rôles et comptes », RBAC-01/ACC-02) : le jeton
 * n'identifie que le compte. Son rôle et son statut sont lus dans {@code users} : un rôle retiré, un compte
 * suspendu ou supprimé perd ses droits dès sa requête suivante, sans attendre l'expiration du jeton (24 h).
 * Le rôle inscrit dans le jeton n'est plus utilisé pour décider d'un accès. Un compte suspendu ou introuvable
 * reçoit 401 : les clients effacent alors le jeton et continuent en visiteur (la consultation reste publique).
 * Un jeton émis avant le dernier changement de mot de passe ({@code users.tokens_valid_after}, V70) aussi.
 */
public class JwtFilter extends OncePerRequestFilter {

    /** Écriture d'une réponse d'erreur JSON, fournie par {@code SecurityConfig}. */
    @FunctionalInterface
    public interface ErrorWriter {
        void write(HttpServletResponse response, int status, String message, String path) throws IOException;
    }

    private final String jwtSecret;
    private final AccountRepository accountRepository;
    private final TokenRevocationRepository revocationRepository;
    private final AuthCookie authCookie;
    private final ErrorWriter errorWriter;

    public JwtFilter(String jwtSecret, AccountRepository accountRepository,
            TokenRevocationRepository revocationRepository, AuthCookie authCookie, ErrorWriter errorWriter) {
        this.jwtSecret = jwtSecret;
        this.accountRepository = accountRepository;
        this.revocationRepository = revocationRepository;
        this.authCookie = authCookie;
        this.errorWriter = errorWriter;
    }

    /** Jeton trouvé dans la requête et sa provenance (le cookie dégrade en visiteur, l'en-tête renvoie 401). */
    private record TokenSource(String token, boolean fromCookie) {
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        TokenSource source = extractToken(request);

        if (source == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        Date issuedAt;
        String jti;
        try {
            Claims claims = parseToken(source.token());
            userId = claims.get("userId", Long.class);
            issuedAt = claims.getIssuedAt();
            jti = claims.getId();
        } catch (SignatureException | MalformedJwtException | ExpiredJwtException
                | UnsupportedJwtException | IllegalArgumentException e) {
            rejectOrContinueAnonymous(request, response, filterChain, source,
                    HttpServletResponse.SC_UNAUTHORIZED, "Token JWT invalide");
            return;
        }

        // Session fermée côté serveur à la déconnexion (jti révoqué, V90) : ce jeton précis ne vaut plus rien, même
        // s'il n'est pas encore expiré et que les autres sessions de l'utilisateur restent ouvertes.
        if (jti != null && revocationRepository.isRevoked(jti)) {
            rejectOrContinueAnonymous(request, response, filterChain, source,
                    HttpServletResponse.SC_UNAUTHORIZED, "Session fermée : reconnecte-toi.");
            return;
        }

        Optional<AccountRepository.Access> access = accountRepository.findAccess(userId);
        if (access.isEmpty()) {
            rejectOrContinueAnonymous(request, response, filterChain, source,
                    HttpServletResponse.SC_UNAUTHORIZED, "Compte introuvable : reconnecte-toi.");
            return;
        }
        if (access.get().status() != AccountStatus.ACTIVE) {
            rejectOrContinueAnonymous(request, response, filterChain, source, HttpServletResponse.SC_UNAUTHORIZED,
                    "Ce compte est suspendu. Contacte l'équipe LocalLife pour en savoir plus.");
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Ce compte est suspendu. Contacte l'équipe Neiri pour en savoir plus.",
                    request.getRequestURI());
            return;
        }
        // Mot de passe changé ou réinitialisé depuis l'émission du jeton : les autres sessions sont fermées.
        LocalDateTime validAfter = access.get().tokensValidAfter();
        if (validAfter != null && (issuedAt == null
                || LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneOffset.UTC).isBefore(validAfter))) {
            rejectOrContinueAnonymous(request, response, filterChain, source, HttpServletResponse.SC_UNAUTHORIZED,
                    "Session expirée après un changement de mot de passe : reconnecte-toi.");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthentication(userId, access.get().email(), access.get().role()));
        // Dernière activité, au jour près : base de la suppression des comptes inactifs (RGPD, 22/10/2026).
        accountRepository.touchActivity(userId);

        filterChain.doFilter(request, response);
    }

    /**
     * Jeton refusé : comportement selon la provenance.
     * <ul>
     *   <li><b>Cookie</b> (navigateur web) : on efface le cookie périmé et on continue en <em>visiteur</em> plutôt
     *       que renvoyer 401. Le navigateur envoie le cookie sur chaque requête, y compris les lectures publiques :
     *       une session expirée ou fermée ne doit pas casser la consultation publique. Les routes protégées
     *       répondront 401 par la règle d'autorisation (le client web redirige alors vers la connexion).</li>
     *   <li><b>En-tête {@code Authorization}</b> (mobile, API) : 401 immédiat, comme avant — ces clients
     *       n'envoient leur jeton que sur un appel qui l'exige et attendent ce 401 pour se reconnecter.</li>
     * </ul>
     */
    private void rejectOrContinueAnonymous(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain, TokenSource source, int status, String message)
            throws IOException, ServletException {
        if (source.fromCookie()) {
            SecurityContextHolder.clearContext();
            response.addHeader(HttpHeaders.SET_COOKIE, authCookie.expired().toString());
            filterChain.doFilter(request, response);
        } else {
            errorWriter.write(response, status, message, request.getRequestURI());
        }
    }

    /**
     * Jeton de la requête : en-tête {@code Authorization: Bearer} en priorité (application mobile, clients API,
     * tests), sinon cookie {@code LL_SESSION} (navigateur web, déposé par {@link AuthCookie}). {@code null} si
     * aucun des deux n'est présent — la requête continue en visiteur, la consultation publique reste ouverte.
     */
    private TokenSource extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return new TokenSource(authHeader.substring(7), false);
        }
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (AuthCookie.NAME.equals(cookie.getName()) && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return new TokenSource(cookie.getValue(), true);
                }
            }
        }
        return null;
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
