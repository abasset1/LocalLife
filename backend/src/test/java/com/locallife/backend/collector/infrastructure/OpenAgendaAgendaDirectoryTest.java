package com.locallife.backend.collector.infrastructure;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.locallife.backend.collector.domain.DiscoveredAgenda;
import com.locallife.backend.collector.domain.DiscoveryScope;
import com.locallife.backend.collector.infrastructure.OpenAgendaAgendaDirectory.AgendaPage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Tests avec un MockRestServiceServer, comme {@code OpenAgendaCollectorTest}. */
class OpenAgendaAgendaDirectoryTest {

    private MockRestServiceServer mockServer;

    private OpenAgendaAgendaDirectory newDirectory(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return new OpenAgendaAgendaDirectory(builder, apiKey);
    }

    @Test
    void fetchPage_ShouldAskForOfficialAgendas_WhenScopeIsOfficial() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        // Forme de réponse de la documentation OpenAgenda (champs non lus ignorés).
        mockServer.expect(requestTo(allOf(
                        containsString("/v2/agendas"),
                        containsString("official=1"),
                        containsString("size=100"),
                        containsString("key=key"))))
                .andExpect(requestTo(not(containsString("sort="))))
                .andRespond(withSuccess("""
                        {
                          "after": [1, 125325],
                          "agendas": [
                            {"uid": 62695, "image": "https://cdn.openagenda.com/main/agenda62695.jpg",
                             "description": "Patrimoine", "official": true,
                             "title": "Patrimoine Grand Châtellerault", "slug": "patrimoine-grand-chatellerault"},
                            {"uid": 125325, "title": "La Fabrique de patrimoines", "slug": "fabrique"}
                          ],
                          "total": 3886
                        }
                        """, MediaType.APPLICATION_JSON));

        AgendaPage page = directory.fetchPage(DiscoveryScope.OFFICIAL, null);

        assertEquals(List.of(
                new DiscoveredAgenda("62695", "Patrimoine Grand Châtellerault", "patrimoine-grand-chatellerault",
                        true),
                // Périmètre officiel : un agenda renvoyé sans le champ est officiel par construction.
                new DiscoveredAgenda("125325", "La Fabrique de patrimoines", "fabrique", true)), page.agendas());
        assertEquals(List.of(1, 125325), page.after());
        assertEquals(3886, page.total());
        mockServer.verify();
    }

    @Test
    void fetchPage_ShouldNotFilterOnOfficial_WhenScopeIsAll() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        mockServer.expect(requestTo(allOf(containsString("/v2/agendas"), not(containsString("official")))))
                .andRespond(withSuccess(
                        "{\"agendas\": [{\"uid\": 7, \"title\": \"Asso\", \"official\": false}]}",
                        MediaType.APPLICATION_JSON));

        AgendaPage page = directory.fetchPage(DiscoveryScope.ALL, null);

        assertEquals(List.of(new DiscoveredAgenda("7", "Asso", null, false)), page.agendas());
        assertNull(page.after());
        assertNull(page.total());
        mockServer.verify();
    }

    @Test
    void fetchPage_ShouldPassTheCursorBackAsAfterParameters() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        mockServer.expect(requestTo(allOf(
                        containsString("after%5B%5D=7"),
                        containsString("after%5B%5D=null"),
                        containsString("after%5B%5D=1700000000000"))))
                .andRespond(withSuccess("{\"agendas\": []}", MediaType.APPLICATION_JSON));

        directory.fetchPage(DiscoveryScope.ALL, java.util.Arrays.asList(7, null, 1.7E12));

        mockServer.verify();
    }

    @Test
    void fetchPage_ShouldKeepAgendasWithoutUid_SoTheCallerCanCountThem() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        mockServer.expect(requestTo(containsString("/v2/agendas")))
                .andRespond(withSuccess("{\"agendas\": [{\"title\": \"Sans uid\"}]}", MediaType.APPLICATION_JSON));

        AgendaPage page = directory.fetchPage(DiscoveryScope.ALL, null);

        assertEquals(1, page.agendas().size());
        assertNull(page.agendas().get(0).uid());
    }

    @Test
    void fetchPage_ShouldSignalAnInconsistentResponse_WithNullAgendas() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        mockServer.expect(requestTo(containsString("/v2/agendas")))
                .andRespond(withSuccess("{\"total\": 12}", MediaType.APPLICATION_JSON));

        assertNull(directory.fetchPage(DiscoveryScope.ALL, null).agendas());
    }

    @Test
    void fetchPage_ShouldRetryAQuotaError_ThenSucceed() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        OpenAgendaAgendaDirectory directory =
                new OpenAgendaAgendaDirectory(builder, "key", OpenAgendaRetry.immediate(3));
        mockServer.expect(requestTo(containsString("/v2/agendas"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        mockServer.expect(requestTo(containsString("/v2/agendas"))).andRespond(withServerError());
        mockServer.expect(requestTo(containsString("/v2/agendas")))
                .andRespond(withSuccess("{\"agendas\": [{\"uid\": 1}]}", MediaType.APPLICATION_JSON));

        assertEquals(1, directory.fetchPage(DiscoveryScope.ALL, null).agendas().size());
        mockServer.verify();
    }

    @Test
    void fetchPage_ShouldThrowCollectorException_WhenApiKeyMissing() {
        OpenAgendaAgendaDirectory directory = newDirectory("");

        assertThrows(CollectorException.class, () -> directory.fetchPage(DiscoveryScope.ALL, null));
    }

    @Test
    void fetchPage_ShouldThrowCollectorException_WhenApiFails() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        mockServer.expect(requestTo(containsString("/v2/agendas"))).andRespond(withServerError());

        assertThrows(CollectorException.class, () -> directory.fetchPage(DiscoveryScope.ALL, null));
    }

    @Test
    void fetchPage_ShouldExplainARefusedKey_WithoutRevealingIt() {
        OpenAgendaAgendaDirectory directory = newDirectory("cle-secrete");
        mockServer.expect(requestTo(containsString("/v2/agendas"))).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"could not find user or agenda matching key\"}"));

        CollectorException exception =
                assertThrows(CollectorException.class, () -> directory.fetchPage(DiscoveryScope.ALL, null));
        assertTrue(exception.getMessage().contains("clé API refusée par OpenAgenda (HTTP 403)"));
        assertTrue(exception.getMessage().contains("could not find user or agenda matching key"));
        assertTrue(exception.getMessage().contains("OPENAGENDA_API_KEY"));
        assertFalse(exception.getMessage().contains("cle-secrete"));
    }

    @Test
    void fetchPage_ShouldExplainANetworkFailure_WithoutKeepingTheUrl() {
        OpenAgendaAgendaDirectory directory = newDirectory("cle-secrete");
        mockServer.expect(requestTo(containsString("/v2/agendas")))
                .andRespond(withException(new java.net.SocketTimeoutException("Read timed out")));

        CollectorException exception =
                assertThrows(CollectorException.class, () -> directory.fetchPage(DiscoveryScope.ALL, null));
        assertTrue(exception.getMessage().contains("OpenAgenda injoignable"));
        // La cause réseau porte l'URL, donc la clé : elle n'est pas conservée.
        assertNull(exception.getCause());
    }

}
