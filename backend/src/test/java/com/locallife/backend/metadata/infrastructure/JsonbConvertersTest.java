package com.locallife.backend.metadata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.metadata.domain.JsonbData;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

/**
 * Couvre {@link StringToJsonbConverter}/{@link JsonbToStringConverter}
 * (LL-11009) en isolation, sans base réelle — le comportement contre une
 * vraie colonne {@code jsonb} est couvert par
 * {@code ActivityMetadataRepositoryIntegrationTest}.
 */
class JsonbConvertersTest {

    @Test
    void stringToJsonbConverter_ShouldProduceJsonbTypedPGobject() {
        PGobject result = new StringToJsonbConverter().convert(new JsonbData("{\"a\":1}"));

        assertThat(result.getType()).isEqualTo("jsonb");
        assertThat(result.getValue()).isEqualTo("{\"a\":1}");
    }

    @Test
    void jsonbToStringConverter_ShouldReturnUnderlyingValue() throws Exception {
        PGobject source = new PGobject();
        source.setType("jsonb");
        source.setValue("{\"a\":1}");

        JsonbData result = new JsonbToStringConverter().convert(source);

        assertThat(result.value()).isEqualTo("{\"a\":1}");
    }
}
