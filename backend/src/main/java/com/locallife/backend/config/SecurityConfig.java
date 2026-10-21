package com.locallife.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.auth.api.JwtFilter;
import com.locallife.backend.user.infrastructure.AccountRepository;
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
 * - GET /api/v1/admin/activities/deletion-preview, DELETE
 *   /api/v1/admin/activities et DELETE /api/v1/admin/activities/{id} :
 *   suppression d'activités, réservée au rôle ADMIN. Tout autre chemin
 *   sous /api/v1/admin/ est aussi réservé au rôle ADMIN (filet de sécurité).
 * GET /api/v1/sources et /api/v1/sources/{id} (LL-6007, Sprint 6) étaient
 * publics ; réservés au rôle ADMIN depuis le 21/10/2026 (voir plus bas,
 * « Visiteur, fermé par défaut »).
 * Endpoints protégés (LL-EF-005) :
 * - POST/PUT/DELETE /api/v1/sources[/{id}] : réservés au rôle ADMIN
 *   (gestion des agendas depuis l'interface d'administration, voir
 *   {@code SourceController}) — même posture que les endpoints
 *   d'administration ci-dessus. GET : ADMIN aussi depuis le 21/10/2026.
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
 * Endpoints protégés (LL-EF-006) :
 * - GET/PATCH /api/v1/users/me : utilisateur connecté (JWT valide
 *   requis) — consultation/modification de son propre profil, voir
 *   {@code UserController}. Déclarés AVANT la règle générale
 *   ci-dessous : Spring Security applique la première règle qui
 *   correspond, donc l'ordre est significatif ici.
 * - GET /api/v1/users/{id} : jusqu'ici {@code permitAll()} par défaut
 *   (aucune règle dédiée n'existait), ce qui permettait à quiconque de
 *   consulter le profil de n'importe quel utilisateur par id — écart
 *   trouvé en traitant ce ticket (critère d'acceptation « un
 *   utilisateur non connecté ne peut pas accéder aux données d'un
 *   autre utilisateur »). Restreint au rôle {@code ADMIN}, même posture
 *   que {@code POST /api/v1/users} ci-dessus : cet endpoint n'est
 *   consommé par aucun client (frontend ni mobile), un usage
 *   d'administration/support est la seule justification restante.
 * Endpoint protégé (LL-EF-010) :
 * - PATCH /api/v1/activities/{id}/cancel : utilisateur connecté (JWT
 *   valide requis) — même posture que POST /api/v1/activities ci-dessus,
 *   voir {@code ActivityController}. Pas de restriction de rôle statique
 *   ici (contrairement aux endpoints d'administration ci-dessus) :
 *   l'autorisation (créateur de l'activité ou administrateur) dépend de
 *   la donnée et est vérifiée dans {@code ActivityService#cancel}, pas
 *   exprimable par une règle {@code hasRole(...)} statique.
 * Endpoints protégés (LS-002) :
 * - POST/DELETE /api/v1/activities/{id}/like : utilisateur connecté (JWT
 *   valide requis), même posture que POST /api/v1/activities ci-dessus —
 *   voir {@code ActivityController}. Contrairement à PATCH .../cancel
 *   ci-dessus, aucune règle métier dépendante des données ici (un
 *   utilisateur connecté peut liker n'importe quelle activité), une
 *   règle statique {@code authenticated()} suffit entièrement.
 * - GET /api/v1/users/me/likes : utilisateur connecté, même principe que
 *   GET /api/v1/users/me (LL-EF-006) — voir {@code LikeController}.
 * - GET/PUT /api/v1/users/me/preferences et GET .../preferences/city :
 *   utilisateur connecté, même principe — voir {@code UserPreferencesController}.
 * - GET/POST /api/v1/users/me/alerts, PATCH/DELETE .../alerts/{id} et
 *   PUT/DELETE /api/v1/users/me/push-tokens : utilisateur
 *   connecté, même principe — voir {@code ActivityAlertController}.
 *   POST /api/v1/alerts/unsubscribe reste public (lien de l'email, le jeton
 *   suffit).
 * Endpoints protégés (ADR-0012, PRO-01) :
 * - /api/v1/pro/** : utilisateur connecté ; le droit professionnel et la
 *   gestion de chaque lieu dépendent des données et sont vérifiés dans
 *   {@code PlaceClaimService}/{@code ProPlaceService}, comme
 *   {@code ActivityService#cancel} ci-dessus.
 * - /api/v1/admin/users et /api/v1/admin/place-claims : rôle ADMIN, par la
 *   règle générale /api/v1/admin/**.
 * <b>Visiteur, fermé par défaut</b> (décision du 21/10/2026, voir
 * {@code ROLES_ET_PERMISSIONS.md} § « Visiteur ») : seules les routes
 * listées dans {@link #PUBLIC_GET} / {@link #PUBLIC_POST} sont ouvertes sans
 * JWT (consultation, inscription et connexion, désabonnement par jeton) ;
 * toute autre route exige au moins une connexion, y compris une route
 * nouvelle qu'on aurait oublié de déclarer ici. {@code GET /api/v1/sources/**}
 * (liste interne des sources, utilisée par l'administration seulement) est
 * réservé au rôle {@code ADMIN}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Consultation publique, sans compte et gratuite : carte, liste, fiches, référentiels. */
    static final String[] PUBLIC_GET = {
        "/api/v1/activities",
        "/api/v1/activities/search",
        "/api/v1/activities/nearby",
        "/api/v1/activities/within-bounds",
        "/api/v1/activities/at-place/*",
        "/api/v1/activities/*",
        "/api/v1/places/within-bounds",
        "/api/v1/places/*",
        "/api/v1/places/*/foodtrucks",
        "/api/v1/foodtrucks",
        "/api/v1/foodtrucks/*",
        "/api/v1/categories",
        "/api/v1/activitytypes",
        "/api/v1/audiences",
        "/api/v1/organizertypes",
        "/api/v1/geocoding/city",
        "/api/v1/geocoding/city-suggestions",
        "/api/v1/auth/username-availability",
        // Documentation de l'API et sonde de santé (inchangées).
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/actuator/health",
    };

    /** Accès au compte sans être connecté, et désabonnement d'une alerte par le jeton du lien de l'e-mail. */
    static final String[] PUBLIC_POST = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/google",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/resend-verification",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/alerts/unsubscribe",
    };

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccountRepository accountRepository)
            throws Exception {
        JwtFilter jwtFilter = new JwtFilter(jwtSecret, accountRepository, (response, status, message, path) ->
                writeError(response, HttpStatus.valueOf(status), message, path));
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/activities").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/activities/*/cancel").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/*/contribution").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/activities/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/activities/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/activities/*/like").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/activities/*/like").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/likes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/preferences").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/alerts").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/me/alerts").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me/alerts/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me/alerts/*").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/push-tokens").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me/push-tokens").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/preferences/city").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/preferences").authenticated()
                        // LL-EF-006 : règles /me déclarées avant la règle générale
                        // GET /api/v1/users/{id} ci-dessous (voir la javadoc de
                        // cette classe) — l'ordre conditionne quelle règle
                        // s'applique.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/activities").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/activities/*/publish").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/activities/*/reject").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/activities/deletion-preview").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/activities").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/activities/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/foodtrucks").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/import").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/import/discover-agendas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/import/discover-agendas/latest")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/import/discovered-agendas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/import/discovered-agendas/sources")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/import/discovery-settings").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/import/sync-states").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/import/sources/*/dry-run").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/import/sources/*/activate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/sources").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/sources/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/sources/*/default-categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sources/*").hasRole("ADMIN")
                        // Filet de sécurité : tout endpoint d'administration oublié ci-dessus reste réservé.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Espace professionnel (ADR-0012) : connexion exigée ici ; le droit professionnel est relu
                        // en base par ProAccessPolicy (un rôle PRO attribué prend effet sans nouvelle connexion).
                        .requestMatchers("/api/v1/pro/**").authenticated()
                        // Abonnement de l'utilisateur connecté (BILLING-01, ADR-0014).
                        .requestMatchers("/api/v1/users/me/subscription", "/api/v1/users/me/subscription/**")
                        .authenticated()
                        // Liste interne des sources : administration seulement (21/10/2026).
                        .requestMatchers(HttpMethod.GET, "/api/v1/sources", "/api/v1/sources/**").hasRole("ADMIN")
                        // Visiteur : uniquement ce qui est explicitement public ; le reste exige une connexion.
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        // Pages d'erreur internes (réponse 401 du filtre JWT) et requêtes CORS de pré-vérification.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
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
