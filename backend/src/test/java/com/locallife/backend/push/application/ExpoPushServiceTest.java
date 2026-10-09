package com.locallife.backend.push.application;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Aucun appel réel à Expo : {@link MockRestServiceServer}. */
class ExpoPushServiceTest {

    private static final String URL = "https://exp.test/push/send";

    private MockRestServiceServer mockServer;

    private ExpoPushService service(String accessToken) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return new ExpoPushService(builder, URL, accessToken);
    }

    @Test
    void send_ShouldPostMessages_AndReturnUnregisteredDevices() {
        ExpoPushService service = service("secret");
        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(content().string(containsString("\"to\":\"ExponentPushToken[a]\"")))
                .andExpect(content().string(containsString("\"activityId\":7")))
                .andRespond(withSuccess(
                        "{\"data\":[{\"status\":\"ok\",\"id\":\"1\"},"
                                + "{\"status\":\"error\",\"message\":\"gone\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}",
                        MediaType.APPLICATION_JSON));

        List<String> unregistered = service.send(
                List.of("ExponentPushToken[a]", "ExponentPushToken[b]"), "Titre", "Texte", Map.of("activityId", 7));

        assertEquals(List.of("ExponentPushToken[b]"), unregistered);
        mockServer.verify();
    }

    @Test
    void send_ShouldSplitInBatchesOfHundred() {
        ExpoPushService service = service("");
        mockServer.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        service.send(IntStream.range(0, 150).mapToObj(i -> "ExponentPushToken[t" + i + "]").toList(),
                "Titre", "Texte", Map.of());

        mockServer.verify();
    }

    @Test
    void send_ShouldThrowPushUnavailable_WhenExpoFails() {
        ExpoPushService service = service("");
        mockServer.expect(requestTo(URL)).andRespond(withServerError());

        assertThrows(PushUnavailableException.class,
                () -> service.send(List.of("ExponentPushToken[a]"), "Titre", "Texte", Map.of()));
    }

    @Test
    void isExpoPushToken_ShouldValidateFormat() {
        assertTrue(ExpoPushService.isExpoPushToken("ExponentPushToken[xxxxAAAA_-1]"));
        assertTrue(ExpoPushService.isExpoPushToken("ExpoPushToken[abc]"));
        assertFalse(ExpoPushService.isExpoPushToken("abc"));
        assertFalse(ExpoPushService.isExpoPushToken(null));
    }

}
