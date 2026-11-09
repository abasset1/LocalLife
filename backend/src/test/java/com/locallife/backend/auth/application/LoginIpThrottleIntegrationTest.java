package com.locallife.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.auth.api.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Limitation du login par IP (correctif sécurité du 06/11/2026) : contre le « credential stuffing », une même IP
 * est bloquée après {@link LoginThrottle#MAX_IP_FAILURES} échecs, même répartis sur des adresses toutes
 * différentes (la limite par e-mail, elle, ne se déclencherait jamais). Serveur et base réels.
 *
 * <p>L'IP cliente est lue dans {@code X-Forwarded-For} grâce à {@code server.forward-headers-strategy=framework}
 * (configuration de production derrière le proxy inverse) : les tests simulent différentes IP par cet en-tête.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginIpThrottleIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Une tentative de connexion avec un mot de passe faux ; renvoie le code HTTP. {@code forwardedIp} null = local. */
    private int attempt(String forwardedIp, String email) {
        RestTestClient.RequestBodySpec spec = client().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON);
        if (forwardedIp != null) {
            spec = spec.header("X-Forwarded-For", forwardedIp);
        }
        return spec.body(new LoginRequest(email, "mauvais-mot-de-passe"))
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value();
    }

    @Test
    void manyFailuresFromOneIp_AcrossDistinctEmails_AreBlocked() {
        String ip = "203.0.113.7";
        // MAX_IP_FAILURES échecs, chacun sur une adresse DIFFÉRENTE : aucune n'atteint sa propre limite (401).
        for (int i = 0; i < LoginThrottle.MAX_IP_FAILURES; i++) {
            assertThat(attempt(ip, "stuffing-" + i + "@example.com")).isEqualTo(401);
        }
        // La limite par IP est atteinte : la tentative suivante (encore une autre adresse) est refusée (429).
        assertThat(attempt(ip, "stuffing-final@example.com")).isEqualTo(429);
    }

    @Test
    void whenOneIpIsBlocked_AnotherIpStaysAllowed() {
        String blocked = "203.0.113.20";
        for (int i = 0; i < LoginThrottle.MAX_IP_FAILURES; i++) {
            attempt(blocked, "fill-" + i + "@example.com");
        }
        assertThat(attempt(blocked, "blocked@example.com")).isEqualTo(429);

        // Une IP différente n'est pas affectée par le blocage de la première.
        assertThat(attempt("203.0.113.21", "fresh@example.com")).isEqualTo(401);
    }

    @Test
    void loopbackIsExemptFromIpThrottle() {
        // Sans X-Forwarded-For, l'IP est la boucle locale (exemptée) : même au-delà du seuil, jamais de 429 par IP.
        for (int i = 0; i <= LoginThrottle.MAX_IP_FAILURES; i++) {
            assertThat(attempt(null, "local-" + i + "@example.com")).isEqualTo(401);
        }
    }
}
