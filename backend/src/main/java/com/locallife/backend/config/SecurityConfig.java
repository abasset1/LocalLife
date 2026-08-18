package com.locallife.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.auth.api.JwtFilter;
import com.locallife.backend.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration de Spring Security pour activer le filtre JWT.
 * Désactive CSRF et les sessions pour une API REST stateless.
 *
 * Endpoints protégés (LL-3008) :
 * - POST /api/v1/activities : utilisateur connecté (JWT valide requis).
 * - POST /api/v1/users : réservé au rôle ADMIN (le flux public de création
 *   de compte passe désormais par POST /api/v1/auth/register, LL-3007).
 * Endpoint protégé (LL-6005, Sprint 6) :
 * - GET /api/v1/admin/activities : réservé au rôle ADMIN (consultation de
 *   la file de modération par statut, voir {@code AdminActivityController}).
 * Endpoints protégés (LL-6006, Sprint 6) :
 * - PATCH /api/v1/admin/activities/{id}/publish et .../reject : réservés
 *   au rôle ADMIN, même mécanisme que GET /api/v1/admin/activities
 *   ci-dessus (voir {@code AdminActivityController}).
 * GET /api/v1/sources et /api/v1/sources/{id} (LL-6007, Sprint 6) restent
 * volontairement non protégés (voir {@code SourceController}) : ajoutés
 * ici sans modification de cette classe, aucune règle dédiée n'était
 * nécessaire.
 * Endpoint protégé (LL-6009, Sprint 6) :
 * - POST /api/v1/foodtrucks : utilisateur connecté (JWT valide requis),
 *   même posture que POST /api/v1/activities ci-dessus (voir
 *   {@code FoodTruckController}). GET /api/v1/foodtrucks reste public,
 *   comme GET /api/v1/activities.
 * Endpoint protégé (LL-7002, Sprint 7) :
 * - POST /api/v1/admin/import : réservé au rôle ADMIN, même mécanisme
 *   que les autres endpoints d'administration ci-dessus (voir
 *   {@code AdminImportController}). Déclenchement manuel du pipeline
 *   d'import existant (Sprint 5) — aucune planification automatique.
 * Tous les autres endpoints restent accessibles sans JWT (consultation
 * publique des activités/catégories, inscription/connexion).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/activities").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/activities").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/activities/*/publish").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/activities/*/reject").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/foodtrucks").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/import").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(this::handleUnauthenticated)
                        .accessDeniedHandler(this::handleAccessDenied)
                );

        return http.build();
    }

    /**
     * Déclenché quand aucun JWT valide n'est fourni pour un endpoint protégé.
     */
    private void handleUnauthenticated(
            HttpServletRequest request, HttpServletResponse response, Exception exception) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, "Authentification requise (JWT manquant ou invalide)",
                request.getRequestURI());
    }

    /**
     * Déclenché quand un JWT valide est fourni mais que le rôle est insuffisant.
     */
    private void handleAccessDenied(
            HttpServletRequest request, HttpServletResponse response, Exception exception) throws IOException {
        writeError(response, HttpStatus.FORBIDDEN, "Accès refusé : rôle insuffisant",
                request.getRequestURI());
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, path);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
