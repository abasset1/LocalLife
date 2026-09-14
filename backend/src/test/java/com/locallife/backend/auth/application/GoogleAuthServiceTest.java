package com.locallife.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * {@link GoogleAuthService} construit avec le constructeur package-private
 * (voir sa javadoc) : un {@link JwtDecoder} simulé remplace le
 * {@code NimbusJwtDecoder} réel, qui contacterait Google. La vérification
 * de signature/émetteur/audience elle-même (déléguée à Spring Security,
 * voir ADR-0002) n'est donc pas testée ici — seule la logique propre à ce
 * service l'est : gestion des échecs de {@link JwtDecoder#decode}, garde
 * {@code email_verified}, garde email absent, mapping vers
 * {@link GoogleAuthService.GoogleIdentity}.
 */
class GoogleAuthServiceTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final GoogleAuthService googleAuthService = new GoogleAuthService(jwtDecoder);

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        return new Jwt(
                "raw-token-value", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    void verify_ShouldReturnGoogleIdentity_WhenTokenIsValidAndEmailVerified() {
        // Given
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "108234567890123456789",
                "email", "jean.dupont@gmail.com",
                "email_verified", true,
                "name", "Jean Dupont"));
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        // When
        GoogleAuthService.GoogleIdentity identity = googleAuthService.verify("valid-token");

        // Then
        assertThat(identity.sub()).isEqualTo("108234567890123456789");
        assertThat(identity.email()).isEqualTo("jean.dupont@gmail.com");
        assertThat(identity.name()).isEqualTo("Jean Dupont");
    }

    @Test
    void verify_ShouldReturnIdentityWithNullName_WhenNameClaimAbsent() {
        // Given : le claim "name" n'est pas systématiquement présent (scope profile
        // non accordé, ou compte Google sans nom affiché) — ne doit pas faire échouer
        // la vérification, seulement laisser name() à null (voir AuthService, repli
        // sur la partie locale de l'email dans ce cas).
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "108234567890123456789",
                "email", "jean.dupont@gmail.com",
                "email_verified", true));
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        // When
        GoogleAuthService.GoogleIdentity identity = googleAuthService.verify("valid-token");

        // Then
        assertThat(identity.name()).isNull();
    }

    @Test
    void verify_ShouldThrow_WhenJwtDecoderRejectsToken() {
        // Given : signature invalide, expiré, mauvaise audience/émetteur — toutes ces
        // vérifications sont déléguées au JwtDecoder réel (voir ADR-0002), qui lève
        // JwtException dans tous ces cas.
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("Invalid signature"));

        // When / Then
        assertThatThrownBy(() -> googleAuthService.verify("bad-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_ShouldThrow_WhenEmailNotVerified() {
        // Given : un email non vérifié par Google ne doit jamais servir à créer ou
        // associer un compte (voir la javadoc de #verify).
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "108234567890123456789",
                "email", "jean.dupont@gmail.com",
                "email_verified", false));
        when(jwtDecoder.decode("token")).thenReturn(jwt);

        // When / Then
        assertThatThrownBy(() -> googleAuthService.verify("token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vérifiée");
    }

    @Test
    void verify_ShouldThrow_WhenEmailVerifiedClaimAbsent() {
        // Given : absence du claim traitée comme "non vérifié", pas comme "vérifié"
        // par défaut — repli le plus prudent des deux.
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "108234567890123456789",
                "email", "jean.dupont@gmail.com"));
        when(jwtDecoder.decode("token")).thenReturn(jwt);

        // When / Then
        assertThatThrownBy(() -> googleAuthService.verify("token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_ShouldThrow_WhenEmailClaimAbsent() {
        // Given : cas limite improbable (Google ne devrait jamais omettre l'email avec
        // le scope "email" demandé), mais gardé explicitement plutôt que de propager un
        // NullPointerException plus loin dans AuthService.
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "108234567890123456789",
                "email_verified", true));
        when(jwtDecoder.decode("token")).thenReturn(jwt);

        // When / Then
        assertThatThrownBy(() -> googleAuthService.verify("token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
