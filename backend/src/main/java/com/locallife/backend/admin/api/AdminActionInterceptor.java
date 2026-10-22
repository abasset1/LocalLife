package com.locallife.backend.admin.api;

import com.locallife.backend.admin.application.AdminActionLog;
import com.locallife.backend.auth.application.JwtAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Filet du journal des actions d'administration (décision du 22/10/2026) : toute requête d'écriture réussie sur
 * l'administration ({@code /api/v1/admin/**}, {@code /api/v1/sources/**}) laisse une ligne, même sans
 * journalisation détaillée dans son service (route, identifiants de la cible, motif éventuel). Les actions déjà
 * journalisées en détail ({@link AdminActionLog#RECORDED_ATTRIBUTE}) ne sont pas doublées. Jamais de corps de
 * requête ni d'en-tête : ni mot de passe ni jeton ne peuvent y finir.
 */
@Component
public class AdminActionInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminActionInterceptor.class);
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AdminActionLog actionLog;

    public AdminActionInterceptor(AdminActionLog actionLog) {
        this.actionLog = actionLog;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        if (READ_METHODS.contains(request.getMethod()) || response.getStatus() >= 300 || exception != null
                || Boolean.TRUE.equals(request.getAttribute(AdminActionLog.RECORDED_ATTRIBUTE))) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthentication admin)) {
            return;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern == null ? request.getRequestURI() : pattern.toString();
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String target = variables == null || variables.isEmpty() ? null : String.join(",", variables.values());
        try {
            actionLog.record(admin.getUserId(), request.getMethod() + " " + route, "ROUTE", target,
                    request.getMethod() + " " + request.getRequestURI(), null, null, request.getParameter("reason"));
        } catch (RuntimeException failure) {
            LOGGER.error("Action d'administration non journalisée ({} {}) : {}", request.getMethod(),
                    request.getRequestURI(), failure.getMessage());
        }
    }
}
