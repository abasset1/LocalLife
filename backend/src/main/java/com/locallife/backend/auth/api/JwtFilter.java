package com.locallife.backend.auth.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre Spring pour extraire et valider le JWT de l'en-tête Authorization.
 * Si le token est valide, remplit le SecurityContext avec les informations de l'utilisateur.
 */
public class JwtFilter extends OncePerRequestFilter {

    private final String jwtSecret;

    public JwtFilter(String jwtSecret) {
        this.jwtSecret = jwtSecret;
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

        try {
            Claims claims = parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String email = claims.get("email", String.class);
            String roleString = claims.get("role", String.class);
            Role role = Role.valueOf(roleString);

            JwtAuthentication authentication = new JwtAuthentication(userId, email, role);
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (SignatureException | MalformedJwtException | ExpiredJwtException | IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token JWT invalide");
            return;
        }

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
