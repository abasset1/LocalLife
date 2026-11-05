package com.locallife.backend.config;

import com.locallife.backend.admin.api.AdminActionInterceptor;
import com.locallife.backend.launch.Feature;
import com.locallife.backend.launch.LaunchFeatures;
import com.locallife.backend.launch.api.FeatureGateInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Intercepteurs MVC : journal des actions d'administration ({@link AdminActionInterceptor}) et fermeture des
 * fonctions pas encore ouvertes ({@link FeatureGateInterceptor}). L'administration ({@code /api/v1/admin/**}) n'est
 * jamais fermée : l'équipe prépare lieux et food trucks avant leur ouverture.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminActionInterceptor adminActionInterceptor;
    private final LaunchFeatures launchFeatures;

    public WebConfig(AdminActionInterceptor adminActionInterceptor, LaunchFeatures launchFeatures) {
        this.adminActionInterceptor = adminActionInterceptor;
        this.launchFeatures = launchFeatures;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminActionInterceptor).addPathPatterns("/api/v1/admin/**", "/api/v1/sources/**");
        gate(registry, Feature.PLACES, "/api/v1/places/**", "/api/v1/activities/at-place/**",
                "/api/v1/pro/places/**", "/api/v1/pro/claims/**", "/api/v1/pro/place-types",
                "/api/v1/pro/place-changes/**");
        gate(registry, Feature.FOOD_TRUCKS, "/api/v1/foodtrucks/**", "/api/v1/places/*/foodtrucks",
                "/api/v1/pro/foodtrucks/**", "/api/v1/pro/presences/**");
        gate(registry, Feature.PRO, "/api/v1/pro/**");
        gate(registry, Feature.PREMIUM, "/api/v1/users/me/follows", "/api/v1/activities/*/follow",
                "/api/v1/places/*/follow");
    }

    private void gate(InterceptorRegistry registry, Feature feature, String... paths) {
        registry.addInterceptor(new FeatureGateInterceptor(feature, launchFeatures)).addPathPatterns(paths);
    }
}
