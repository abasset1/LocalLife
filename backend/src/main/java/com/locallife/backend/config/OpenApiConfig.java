package com.locallife.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Déclare le schéma d'authentification JWT auprès de Swagger UI, pour
 * faire apparaître le bouton « Authorize » (absent jusqu'ici, aucun
 * {@code SecurityScheme} n'était déclaré). Sans cette configuration,
 * Swagger UI ne sait pas qu'un mécanisme d'authentification existe et ne
 * peut donc pas proposer de saisir un JWT pour les requêtes envoyées
 * depuis l'interface — les endpoints protégés restent inchangés
 * (protection effective toujours assurée par {@link SecurityConfig} et le
 * filtre JWT, pas ici).
 *
 * Pas de {@code @SecurityRequirement} posé sur des contrôleurs
 * individuels : la déclaration globale ci-dessous suffit à faire
 * apparaître le cadenas sur chaque opération dans Swagger UI, sans
 * modifier les contrôleurs existants.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
