package com.locallife.backend.geocoding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.locallife.backend.geocoding.application.CitySuggestionService.CitySuggestion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Tests de CitySuggestionService contre un geo.api.gouv.fr simulé (MockRestServiceServer). */
class CitySuggestionServiceTest {

    private static final String LYON_JSON = """
            [{"nom":"Lyon","code":"69123","codesPostaux":["69003","69001","69002"],"codeDepartement":"69",
              "centre":{"type":"Point","coordinates":[4.8351,45.758]},"_score":0.9},
             {"nom":"Lyons-la-Forêt","codesPostaux":["27480"],"codeDepartement":"27",
              "centre":{"type":"Point","coordinates":[1.4766,49.3986]}},
             {"nom":"Sans centre","codesPostaux":["00000"],"codeDepartement":"00"}]
            """;

    private MockRestServiceServer mockServer;
    private CitySuggestionService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        service = new CitySuggestionService(builder);
    }

    @Test
    void suggest_ShouldSearchByName_MostPopulatedFirst_WithSortedPostalCodes() {
        mockServer.expect(once(), requestTo(allOf(
                        containsString("/communes?nom=lyo"), containsString("boost=population"),
                        containsString("fields=nom,codesPostaux,codeDepartement,centre"))))
                .andRespond(withSuccess(LYON_JSON, MediaType.APPLICATION_JSON));

        List<CitySuggestion> suggestions = service.suggest(" lyo ");

        // La commune sans centre est écartée (impossible d'y déplacer la carte).
        assertThat(suggestions).containsExactly(
                new CitySuggestion("Lyon", List.of("69001", "69002", "69003"), "69", 45.758, 4.8351),
                new CitySuggestion("Lyons-la-Forêt", List.of("27480"), "27", 49.3986, 1.4766));
        mockServer.verify();
    }

    @Test
    void suggest_ShouldSearchByPostalCode_WhenFiveDigits() {
        mockServer.expect(requestTo(containsString("/communes?codePostal=69003")))
                .andRespond(withSuccess(LYON_JSON, MediaType.APPLICATION_JSON));

        assertThat(service.suggest("69003")).first().extracting(CitySuggestion::name).isEqualTo("Lyon");
        mockServer.verify();
    }

    @Test
    void suggest_ShouldServeRepeatedQueriesFromCache_WhateverTheCase() {
        mockServer.expect(once(), requestTo(containsString("nom=Lyo")))
                .andRespond(withSuccess(LYON_JSON, MediaType.APPLICATION_JSON));

        service.suggest("Lyo");
        assertThat(service.suggest("LYO")).hasSize(2);
        mockServer.verify();
    }

    @Test
    void suggest_ShouldReturnNothing_ForTooShortQuery_WithoutCallingTheApi() {
        assertThat(service.suggest("l")).isEmpty();
        assertThat(service.suggest(null)).isEmpty();
        mockServer.verify();
    }

    @Test
    void suggest_ShouldReportUnavailability_WhenApiFails() {
        mockServer.expect(requestTo(containsString("/communes"))).andRespond(withServerError());

        assertThatThrownBy(() -> service.suggest("lyo")).isInstanceOf(GeocodingUnavailableException.class);
    }
}
