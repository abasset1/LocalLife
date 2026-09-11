package com.locallife.backend.config;

import com.locallife.backend.metadata.infrastructure.JsonbToStringConverter;
import com.locallife.backend.metadata.infrastructure.StringToJsonbConverter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

/**
 * Enregistre les convertisseurs JSONB (LL-11009) auprès de Spring Data
 * JDBC — un bean {@link JdbcCustomConversions} défini par l'application
 * remplace celui, par défaut, de l'auto-configuration Spring Boot (voir
 * {@code docs/02_Architecture/ADR-0004-metadata-jsonb.md}). Seul point
 * de configuration JDBC personnalisé du projet à ce jour — les autres
 * colonnes PostgreSQL spécifiques (ex. {@code geography} pour les
 * coordonnées, LL-4002/LL-11001) sont alimentées par trigger SQL plutôt
 * que par un convertisseur Java, donc invisibles de Spring Data JDBC ;
 * {@code jsonb} ne pouvait pas suivre ce même principe ici, {@code
 * ActivityMetadata#data} devant rester une donnée applicative lisible/
 * écrivable directement.
 */
@Configuration
public class JdbcConfig {

    @Bean
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(new StringToJsonbConverter(), new JsonbToStringConverter()));
    }
}
