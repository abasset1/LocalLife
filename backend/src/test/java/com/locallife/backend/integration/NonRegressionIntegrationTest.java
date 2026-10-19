package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.api.ActivityController.CreateActivityRequest;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.common.IntegrationTestCleaner;
import com.locallife.backend.foodtruck.api.FoodTruckController;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Suite de non-régression (LL-6010, Sprint 6, priorité Haute). Contrairement
 * aux autres tests d'intégration du projet (chacun dédié à un ticket
 * précis), cette classe a un rôle différent et délibérément redondant par
 * endroits : faire correspondre, un par un, chacun des neuf points listés
 * dans {@code SPRINT_6.md} sous « ### Tester » à une méthode de test
 * explicite et facilement repérable — plutôt que de se fier à la
 * dispersion de la couverture déjà existante entre
 * {@code ActivityControllerIntegrationTest},
 * {@code AdminActivityControllerIntegrationTest},
 * {@code ImportedActivityVisibilityIntegrationTest} et
 * {@code FoodTruckControllerIntegrationTest}. Un peu de recouvrement avec
 * ces suites est assumé : c'est précisément le rôle d'une suite de
 * non-régression que de re-vérifier explicitement, en un seul endroit,
 * des comportements déjà couverts ailleurs mais critiques pour le produit
 * (visibilité publique, contrôle d'accès administrateur) — voir
 * {@code PROJECT_STATUS.md} pour l'audit complet de couverture ayant
 * précédé l'écriture de cette classe.
 *
 * Même approche que {@code AdminActivityControllerIntegrationTest}
 * (serveur embarqué, base réelle, géocodage mocké, token ADMIN construit
 * directement — voir sa javadoc pour le détail de cette dernière
 * technique).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NonRegressionIntegrationTest {

    private static final String PASSWORD = "motDePasse123";
    private static final double LATITUDE = 43.29;
    private static final double LONGITUDE = 5.37;
    private static final String ADDRESS = "1 rue de la Paix, Marseille";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private GeocodingService geocodingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Simule le clic sur le lien de confirmation reçu par email (voir EmailVerificationService). */
    private void markEmailVerified(String email) {
        jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE email = ?", email);
    }

    @Autowired
    private IntegrationTestCleaner cleaner;

    /** Test non transactionnel (serveur embarqué) : ce qu'il crée est supprimé après chaque test. */
    @BeforeEach
    void startCleaner() {
        cleaner.start();
    }

    @AfterEach
    void cleanUpTestData() {
        cleaner.cleanUp();
    }

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String uniqueEmail() {
        return cleaner.trackUser("test-" + UUID.randomUUID() + "@example.com");
    }

    /** Même technique qu'{@code AdminActivityControllerIntegrationTest#adminToken} — voir sa javadoc. */
    private String adminToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        long oneHourMillis = 3_600_000L;
        return Jwts.builder()
                .claim("userId", 1L)
                .claim("email", "admin-test@example.com")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + oneHourMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Nom d'utilisateur unique (LS-000) déduit de l'email unique "test-<uuid>@example.com".
    private static String usernameFor(String email) {
        return "u-" + email.substring("test-".length(), "test-".length() + 12);
    }

    private String registerAndLoginAsUser(String email) {
        restTestClient().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(usernameFor(email), email, PASSWORD))
                .exchange()
                .expectStatus().isCreated();
        markEmailVerified(email);

        LoginResponse login = restTestClient().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(login).isNotNull();
        return login.token();
    }

    private Activity createPendingActivity(String title, String userToken) {
        when(geocodingService.geocode(ADDRESS)).thenReturn(new Coordinates(LATITUDE, LONGITUDE, null, null));

        return restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(title, "description", "loisir", ADDRESS, null, null, null, null, null,
                "CULTURE"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Activity.class)
                .returnResult()
                .getResponseBody();
    }

    private boolean appearsInNearbySearch(String title) {
        String body = restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=" + LATITUDE + "&longitude=" + LONGITUDE + "&radius=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return body != null && body.contains(title);
    }

    private boolean appearsInBoundingBoxSearch(String title) {
        String body = restTestClient().get()
                .uri("/api/v1/activities/within-bounds?swLatitude=43.20&swLongitude=5.30"
                        + "&neLatitude=43.40&neLongitude=5.50")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return body != null && body.contains(title);
    }

    // --- 1. activité publiée visible / 6. publication ---

    @Test
    void publishedActivity_ShouldBeVisibleInPublicSearch() {
        // Given : une activité PENDING créée puis publiée par un administrateur.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Concert " + UUID.randomUUID();
        Activity created = createPendingActivity(title, userToken);

        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PUBLISHED");

        // Then : visible sur les deux endpoints de recherche géographique publics.
        assertThat(appearsInNearbySearch(title)).isTrue();
        assertThat(appearsInBoundingBoxSearch(title)).isTrue();
    }

    // --- 2. activité en attente invisible publiquement ---

    @Test
    void pendingActivity_ShouldNotBeVisibleInPublicSearch() {
        // Given : une activité PENDING, jamais publiée ni rejetée.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Vide-grenier " + UUID.randomUUID();
        createPendingActivity(title, userToken);

        // Then
        assertThat(appearsInNearbySearch(title)).isFalse();
        assertThat(appearsInBoundingBoxSearch(title)).isFalse();
    }

    // --- 3. activité rejetée invisible publiquement / 7. rejet ---

    @Test
    void rejectedActivity_ShouldNotBeVisibleInPublicSearch() {
        // Given : une activité PENDING créée puis rejetée par un administrateur.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Brocante " + UUID.randomUUID();
        Activity created = createPendingActivity(title, userToken);

        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED");

        // Then : toujours invisible publiquement, comme une activité PENDING.
        assertThat(appearsInNearbySearch(title)).isFalse();
        assertThat(appearsInBoundingBoxSearch(title)).isFalse();
    }

    // --- 4. accès administrateur ---

    @Test
    void adminUser_ShouldAccessAdminEndpoint() {
        restTestClient().get().uri("/api/v1/admin/activities?status=PENDING")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();
    }

    // --- 5. accès utilisateur standard ---

    @Test
    void standardUser_ShouldBeDeniedAdminEndpoint() {
        String userToken = registerAndLoginAsUser(uniqueEmail());

        restTestClient().get().uri("/api/v1/admin/activities?status=PENDING")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    // --- 8. food truck visible sur la carte ---

    @Test
    void foodTruck_ShouldBeVisibleOnPublicMap() {
        // Given : un food truck créé par un compte professionnel (LL-6009 ; réservé aux pros depuis PRO-01 lot C).
        String email = uniqueEmail();
        String userToken = registerAndLoginAsUser(email);
        jdbcTemplate.update("UPDATE users SET role = 'PRO' WHERE email = ?", email);
        String name = cleaner.trackFoodTruck("Le Camion qui Fume " + UUID.randomUUID());

        restTestClient().post().uri("/api/v1/foodtrucks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FoodTruckController.CreateFoodTruckRequest(
                        name, "desc", LATITUDE, LONGITUDE, "burger", null))
                .exchange()
                .expectStatus().isCreated();

        // Then : visible immédiatement sur la consultation publique (pas de modération food truck).
        String body = restTestClient().get().uri("/api/v1/foodtrucks")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).contains(name);
    }

    // --- 9. recherche géographique inchangée ---

    @Test
    void geographicSearch_ShouldRemainFunctional_ForPublishedActivity() {
        // Vérification de non-régression sur les contrats LL-4001-LL-4014 (recherche par rayon
        // et par zone, filtres catégorie/date), après tous les changements de statut/visibilité
        // introduits en Sprint 6 (LL-6003/LL-6004/LL-6005/LL-6006) — une activité publiée reste
        // trouvable exactement comme avant, y compris avec les filtres combinés.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Festival " + UUID.randomUUID();
        Activity created = createPendingActivity(title, userToken);

        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();

        // Rayon large (LL-4001/LL-4003).
        assertThat(appearsInNearbySearch(title)).isTrue();
        // Zone rectangulaire (LL-4006/LL-4007).
        assertThat(appearsInBoundingBoxSearch(title)).isTrue();
        // Filtre catégorie correspondant (LL-4004) — "loisir", catégorie utilisée par
        // createPendingActivity.
        String filteredBody = restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=" + LATITUDE + "&longitude=" + LONGITUDE
                        + "&radius=5&category=loisir")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(filteredBody).contains(title);
    }

    // --- 10. créateur affiché sur une activité créée manuellement (LL-EF-010) ---

    @Test
    void manuallyCreatedActivity_ShouldExposeCreatorUsername_InPublicSearch() {
        // Given : une activité créée par un utilisateur, publiée par un administrateur.
        String email = uniqueEmail();
        String userToken = registerAndLoginAsUser(email);
        String title = "Atelier peinture " + UUID.randomUUID();
        Activity created = createPendingActivity(title, userToken);

        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();

        // Then : son nom d'utilisateur (voir usernameFor) est exposé dans la réponse publique.
        String body = restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=" + LATITUDE + "&longitude=" + LONGITUDE + "&radius=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).contains(title).contains("\"createdByUsername\":\"" + usernameFor(email) + "\"");
    }

    // --- 11. annulation par le créateur (LL-EF-010) ---

    @Test
    void creator_ShouldBeAbleToCancelTheirOwnPublishedActivity() {
        // Given : une activité créée par "alice", publiée, donc visible publiquement.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Randonnée " + UUID.randomUUID();
        Activity created = createPendingActivity(title, userToken);
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();
        assertThat(appearsInNearbySearch(title)).isTrue();

        // When : le créateur annule son activité.
        restTestClient().patch().uri("/api/v1/activities/" + created.id() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");

        // Then : n'apparaît plus dans la recherche publique (statut CANCELLED, même traitement
        // que PENDING/REJECTED — seul PUBLISHED est retourné, voir LL-6004).
        assertThat(appearsInNearbySearch(title)).isFalse();
    }

    // --- 12. annulation refusée à un tiers (LL-EF-010) ---

    @Test
    void otherUser_ShouldBeDeniedCancellingSomeoneElsesActivity() {
        // Given : une activité créée par "alice" (userToken), publiée.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Marché aux fleurs " + UUID.randomUUID();
        Activity created = createPendingActivity(title, userToken);
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();

        // When : un second utilisateur, ni créateur ni administrateur, tente d'annuler.
        String otherUserToken = registerAndLoginAsUser(uniqueEmail());
        restTestClient().patch().uri("/api/v1/activities/" + created.id() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken)
                .exchange()
                .expectStatus().isForbidden();

        // Then : reste visible publiquement, inchangée.
        assertThat(appearsInNearbySearch(title)).isTrue();
    }

    // --- 13. annulation par un administrateur, même sans être le créateur (LL-EF-010) ---

    @Test
    void admin_ShouldBeAbleToCancelAnyonesActivity() {
        // Given : une activité créée par "alice", publiée.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Salon du livre " + UUID.randomUUID();
        Activity created = createPendingActivity(title, userToken);
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();

        // When : un administrateur (pas le créateur) annule.
        restTestClient().patch().uri("/api/v1/activities/" + created.id() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");

        // Then
        assertThat(appearsInNearbySearch(title)).isFalse();
    }

    // --- 14. liker/déliker une activité (LS-002) ---

    @Test
    void user_ShouldBeAbleToLikeAndUnlikeAPublishedActivity() {
        // Given : une activité publiée, visible publiquement.
        String creatorToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Exposition photo " + UUID.randomUUID();
        Activity created = createPendingActivity(title, creatorToken);
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();

        // When : un second utilisateur like l'activité.
        String likerToken = registerAndLoginAsUser(uniqueEmail());
        restTestClient().post().uri("/api/v1/activities/" + created.id() + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + likerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.liked").isEqualTo(true);

        // Then : apparaît dans ses favoris.
        String favoritesBody = restTestClient().get().uri("/api/v1/users/me/likes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + likerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(favoritesBody).contains(title).contains("\"likedByCurrentUser\":true");

        // When : il retire son like.
        restTestClient().delete().uri("/api/v1/activities/" + created.id() + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + likerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.liked").isEqualTo(false);

        // Then : ne figure plus dans ses favoris.
        String favoritesAfterUnlike = restTestClient().get().uri("/api/v1/users/me/likes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + likerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(favoritesAfterUnlike).doesNotContain(title);
    }

    @Test
    void like_ShouldReturnNotFound_WhenActivityDoesNotExist() {
        String userToken = registerAndLoginAsUser(uniqueEmail());

        restTestClient().post().uri("/api/v1/activities/999999999/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void like_ShouldReturnUnauthorized_WhenNotAuthenticated() {
        restTestClient().post().uri("/api/v1/activities/1/like")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void like_ShouldBeIdempotent_WhenCalledTwice() {
        // Given
        String creatorToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Brocante " + UUID.randomUUID();
        Activity created = createPendingActivity(title, creatorToken);
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();
        String likerToken = registerAndLoginAsUser(uniqueEmail());

        // When : deux appels successifs.
        restTestClient().post().uri("/api/v1/activities/" + created.id() + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + likerToken)
                .exchange()
                .expectStatus().isOk();
        restTestClient().post().uri("/api/v1/activities/" + created.id() + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + likerToken)
                .exchange()
                .expectStatus().isOk();

        // Then : un seul favori, pas d'erreur ni de doublon.
        String favoritesBody = restTestClient().get().uri("/api/v1/users/me/likes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + likerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        long occurrences = favoritesBody.split(java.util.regex.Pattern.quote(title), -1).length - 1;
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void likedActivity_ShouldDisappearFromFavorites_WhenCancelledByCreator() {
        // Given : "alice" like sa propre activité, qu'elle annule ensuite (LL-EF-010).
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String title = "Vide-grenier " + UUID.randomUUID();
        Activity created = createPendingActivity(title, userToken);
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();
        restTestClient().post().uri("/api/v1/activities/" + created.id() + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk();

        // When : l'activité est annulée.
        restTestClient().patch().uri("/api/v1/activities/" + created.id() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk();

        // Then : ne figure plus dans les favoris (critère d'acceptation explicite du
        // ticket), bien que le like lui-même reste en base (voir
        // ActivityRepository#findLikedByUserId).
        String favoritesBody = restTestClient().get().uri("/api/v1/users/me/likes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(favoritesBody).doesNotContain(title);
    }

}
