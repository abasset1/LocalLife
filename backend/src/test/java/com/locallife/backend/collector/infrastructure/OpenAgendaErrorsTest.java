package com.locallife.backend.collector.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAgendaErrorsTest {

    @Test
    void describe_ShouldExplainCommonStatuses() {
        assertThat(OpenAgendaErrors.describe(401, "")).startsWith("clé API refusée par OpenAgenda (HTTP 401)");
        assertThat(OpenAgendaErrors.describe(404, "{\"message\":\"agenda not found\"}"))
                .contains("HTTP 404").contains("« agenda not found »").contains("identifiant d'agenda");
        assertThat(OpenAgendaErrors.describe(429, null)).contains("HTTP 429").contains("Réessaie plus tard");
        assertThat(OpenAgendaErrors.describe(503, "<html>")).isEqualTo(
                "OpenAgenda est en erreur (HTTP 503). Réessaie plus tard.");
        assertThat(OpenAgendaErrors.describe(400, "{\"message\":\"invalid sort\"}"))
                .isEqualTo("requête refusée par OpenAgenda (HTTP 400) (OpenAgenda : « invalid sort »).");
    }
}
