package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

class OpenAgendaRetryTest {

    private final List<Duration> waits = new ArrayList<>();
    private final OpenAgendaRetry retry = new OpenAgendaRetry(3, Duration.ofSeconds(2), waits::add);

    @Test
    void call_ShouldRetryQuotaServerAndNetworkErrors_WithGrowingWaits() {
        AtomicInteger attempts = new AtomicInteger();

        String result = retry.call("contexte", () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "quota", null, null, null);
            }
            if (attempt == 2) {
                throw new ResourceAccessException("Read timed out https://api.openagenda.com/?key=secret");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)), waits);
    }

    @Test
    void call_ShouldHonourRetryAfter_CappedAtOneMinute() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "3600");
        AtomicInteger attempts = new AtomicInteger();

        retry.call("contexte", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "quota", headers, null, null);
            }
            return "ok";
        });

        assertEquals(List.of(OpenAgendaRetry.MAX_RETRY_AFTER), waits);
    }

    @Test
    void call_ShouldGiveUpAfterMaxAttempts_WithAnExplainedErrorWithoutTheKey() {
        AtomicInteger attempts = new AtomicInteger();

        CollectorException exception = assertThrows(CollectorException.class, () -> retry.call("Échec", () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("I/O error on GET https://api.openagenda.com/v2?key=cle-secrete");
        }));

        assertEquals(3, attempts.get());
        assertTrue(exception.getMessage().contains("OpenAgenda injoignable"));
        assertFalse(exception.getMessage().contains("cle-secrete"));
    }

    @Test
    void call_ShouldNotRetryARefusedKey() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(CollectorException.class, () -> retry.call("Échec", () -> {
            attempts.incrementAndGet();
            throw HttpClientErrorException.create(HttpStatus.FORBIDDEN, "refusée", null,
                    "{\"message\":\"no\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }));

        assertEquals(1, attempts.get());
        assertTrue(waits.isEmpty());
    }

    @Test
    void isRetryable_ShouldDistinguishTransientFromPermanentErrors() {
        assertTrue(OpenAgendaRetry.isRetryable(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "x", null, null, null)));
        assertTrue(OpenAgendaRetry.isRetryable(new RestClientException("réponse illisible")));
        assertFalse(OpenAgendaRetry.isRetryable(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "x", null, null, null)));
    }
}
