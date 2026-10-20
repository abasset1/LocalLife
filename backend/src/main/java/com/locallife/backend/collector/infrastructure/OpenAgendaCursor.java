package com.locallife.backend.collector.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.util.UriBuilder;

/** Curseur de pagination OpenAgenda ({@code after}) : renvoyé tel que reçu, en paramètres {@code after[]}. */
final class OpenAgendaCursor {

    private OpenAgendaCursor() {
    }

    /**
     * Ajoute le curseur à la requête. {@code null} devient la chaîne {@code "null"} (exigé par la
     * documentation). Un nombre décimal est écrit en notation simple : {@code String.valueOf} d'un grand
     * {@code Double} donnerait {@code 1.7E12}, que l'API ne reconnaîtrait pas comme la valeur de tri reçue.
     */
    static void append(UriBuilder uriBuilder, List<Object> after) {
        if (after == null) {
            return;
        }
        for (Object value : after) {
            uriBuilder.queryParam("after[]", render(value));
        }
    }

    static String render(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double || value instanceof Float) {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }
}
