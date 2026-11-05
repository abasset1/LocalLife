package com.locallife.backend.launch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.launch.Feature;
import com.locallife.backend.launch.LaunchFeatures;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Ferme les routes d'une fonction pas encore ouverte ({@link LaunchFeatures}) : 404 avec un message explicite, comme
 * si la route n'existait pas encore. Passe après Spring Security : une route qui exige une connexion répond toujours
 * 401 sans JWT. Les chemins de chaque fonction sont déclarés dans {@code WebConfig}.
 */
public class FeatureGateInterceptor implements HandlerInterceptor {

    static final String MESSAGE = "Cette fonctionnalité n'est pas encore disponible.";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final Feature feature;
    private final LaunchFeatures launchFeatures;

    public FeatureGateInterceptor(Feature feature, LaunchFeatures launchFeatures) {
        this.feature = feature;
        this.launchFeatures = launchFeatures;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // Pré-vérification CORS : laissée à Spring, la vraie requête sera filtrée ensuite.
        if (CorsUtils.isPreFlightRequest(request)
                || launchFeatures.isAvailable(feature, SecurityContextHolder.getContext().getAuthentication())) {
            return true;
        }
        HttpStatus status = HttpStatus.NOT_FOUND;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), MESSAGE, request.getRequestURI())));
        return false;
    }
}
