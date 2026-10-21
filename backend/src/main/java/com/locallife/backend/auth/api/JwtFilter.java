package com.locallife.backend.auth.api;

import com.locallife.backend.auth.application.JwtAuthentication;
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
    private final ErrorWriter errorWriter;

    public JwtFilter(String jwtSecret, AccountRepository accountRepository, ErrorWriter errorWriter) {
        this.jwtSecret = jwtSecret;
        this.accountRepository = accountRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        Long userId;
        Date issuedAt;
        try {
            Claims claims = parseToken(token);
            userId = claims.get("userId", Long.class);
            issuedAt = claims.getIssuedAt();
        } catch (SignatureException | MalformedJwtException | ExpiredJwtException
                | UnsupportedJwtException | IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token JWT invalide");
            return;
        }

        Optional<AccountRepository.Access> access = accountRepository.findAccess(userId);
        if (access.isEmpty()) {
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Compte introuvable : reconnecte-toi.",
                    request.getRequestURI());
            return;
        }
        if (access.get().status() != AccountStatus.ACTIVE) {
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Ce compte est suspendu. Contacte l'équipe LocalLife pour en savoir plus.",
                    request.getRequestURI());
            return;
        }
        // Mot de passe changé ou réinitialisé depuis l'émission du jeton : les autres sessions sont fermées.
        LocalDateTime validAfter = access.get().tokensValidAfter();
        if (validAfter != null && (issuedAt == null
                || LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneOffset.UTC).isBefore(validAfter))) {
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Session expirée après un changement de mot de passe : reconnecte-toi.", request.getRequestURI());
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthentication(userId, access.get().email(), access.get().role()));

        filterChain.doFilter(request, response);
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
