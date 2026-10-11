package com.locallife.backend.collector.infrastructure;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.locallife.backend.collector.domain.DiscoveredAgenda;
import java.util.List;
import org.junit.jupiter.api.Test;
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
    void listOfficialAgendas_ShouldAskForOfficialAgendas_MostActiveFirst() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        // Forme de réponse de la documentation OpenAgenda (champs non lus ignorés).
        mockServer.expect(requestTo(allOf(
                        containsString("/v2/agendas"),
                        containsString("official=1"),
                        containsString("sort=recentlyAddedEvents.desc"),
                        containsString("size=2"),
                        containsString("key=key"))))
                .andRespond(withSuccess("""
                        {
                          "after": [1, 125325],
                          "agendas": [
                            {"uid": 62695, "image": "https://cdn.openagenda.com/main/agenda62695.jpg",
                             "description": "Patrimoine", "official": true,
                             "title": "Patrimoine Grand Châtellerault", "slug": "patrimoine-grand-chatellerault"},
                            {"uid": 125325, "official": true, "title": "La Fabrique de patrimoines",
                             "slug": "fabrique"}
                          ],
                          "total": 3886
                        }
                        """, MediaType.APPLICATION_JSON));

        List<DiscoveredAgenda> agendas = directory.listOfficialAgendas(2);

        assertEquals(List.of(
                new DiscoveredAgenda("62695", "Patrimoine Grand Châtellerault", "patrimoine-grand-chatellerault"),
                new DiscoveredAgenda("125325", "La Fabrique de patrimoines", "fabrique")), agendas);
        mockServer.verify();
    }

    @Test
    void listOfficialAgendas_ShouldFollowAfterCursor_UntilLimitReached() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        StringBuilder firstPage = new StringBuilder();
        for (int uid = 1; uid <= 100; uid++) {
            firstPage.append(uid == 1 ? "" : ",").append("{\"uid\": ").append(uid).append(", \"title\": \"A")
                    .append(uid).append("\", \"slug\": \"a").append(uid).append("\"}");
        }
        mockServer.expect(requestTo(containsString("size=100")))
                .andRespond(withSuccess(
                        "{\"after\": [7, 100], \"agendas\": [" + firstPage + "]}", MediaType.APPLICATION_JSON));
        // Seconde page : seulement ce qui manque pour atteindre la limite, avec le curseur de la première.
        mockServer.expect(requestTo(allOf(
                        containsString("size=20"),
                        containsString("after%5B%5D=7"),
                        containsString("after%5B%5D=100"))))
                .andRespond(withSuccess(
                        "{\"after\": [3, 101], \"agendas\": [{\"uid\": 101, \"title\": \"A101\", \"slug\": \"a101\"}]}",
                        MediaType.APPLICATION_JSON));

        List<DiscoveredAgenda> agendas = directory.listOfficialAgendas(120);

        assertEquals(101, agendas.size());
        assertEquals("101", agendas.get(100).uid());
        mockServer.verify();
    }

    @Test
    void listOfficialAgendas_ShouldContinueBeyondFiveHundredAgendas() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        for (int page = 0; page < 5; page++) {
            int firstUid = page * 100 + 1;
            int lastUid = firstUid + 99;
            StringBuilder agendas = new StringBuilder();
            for (int uid = firstUid; uid <= lastUid; uid++) {
                agendas.append(uid == firstUid ? "" : ",")
                        .append("{\"uid\":").append(uid).append(",\"title\":\"A")
                        .append(uid).append("\"}");
            }
            String after = page == 4 ? "[1,500]" : "[1," + lastUid + "]";
            mockServer.expect(requestTo(containsString("size=100")))
                    .andRespond(withSuccess(
                            "{\"after\":" + after + ",\"agendas\":[" + agendas + "]}",
                            MediaType.APPLICATION_JSON));
        }
        mockServer.expect(requestTo(allOf(containsString("size=1"), containsString("after%5B%5D=500"))))
                .andRespond(withSuccess(
                        "{\"after\":[1,501],\"agendas\":[{\"uid\":501,\"title\":\"A501\"}]}",
                        MediaType.APPLICATION_JSON));

        List<DiscoveredAgenda> agendas = directory.listOfficialAgendas(501);

        assertEquals(501, agendas.size());
        assertEquals("501", agendas.get(500).uid());
        mockServer.verify();
    }

    @Test
    void listOfficialAgendas_ShouldFailWhenApiRepeatsCursor() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        mockServer.expect(requestTo(containsString("size=100")))
                .andRespond(withSuccess(
                        "{\"after\":[1,100],\"agendas\":[{\"uid\":1}]}",
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("after%5B%5D=100")))
                .andRespond(withSuccess(
                        "{\"after\":[1,100],\"agendas\":[{\"uid\":2}]}",
                        MediaType.APPLICATION_JSON));

        assertThrows(CollectorException.class, () -> directory.listOfficialAgendas(1000));
        mockServer.verify();
    }

    @Test
    void listOfficialAgendas_ShouldReturnEmptyList_WhenNoAgenda() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        mockServer.expect(requestTo(containsString("/v2/agendas")))
                .andRespond(withSuccess("{\"agendas\": [], \"total\": 0}", MediaType.APPLICATION_JSON));

        assertTrue(directory.listOfficialAgendas(50).isEmpty());
    }

    @Test
    void listOfficialAgendas_ShouldThrowCollectorException_WhenApiKeyMissing() {
        OpenAgendaAgendaDirectory directory = newDirectory("");

        assertThrows(CollectorException.class, () -> directory.listOfficialAgendas(50));
    }

    @Test
    void listOfficialAgendas_ShouldThrowCollectorException_WhenApiFails() {
        OpenAgendaAgendaDirectory directory = newDirectory("key");
        mockServer.expect(requestTo(containsString("/v2/agendas"))).andRespond(withServerError());

        assertThrows(CollectorException.class, () -> directory.listOfficialAgendas(50));
    }

}
