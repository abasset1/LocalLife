package com.locallife.backend.admin.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.admin.infrastructure.AdminActionLogRepository;
import com.locallife.backend.admin.infrastructure.AdminActionLogRepository.Entry;
import com.locallife.backend.admin.infrastructure.AdminActionLogRepository.Filter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Journal unique des actions d'administration (décision du 22/10/2026, {@code ROLES_ET_PERMISSIONS.md}
 * § « Administrateur ») : toute action d'un administrateur qui écrit (modération, suppression, restauration,
 * sources, catégories, imports, abonnements, comptes, lieux, food trucks) y laisse une ligne : auteur, action, cible,
 * état avant/après, motif. Écrit dans la transaction de l'action : une action annulée ne laisse pas de trace, une
 * action réussie en laisse toujours une. Les lectures ne sont pas journalisées.
 */
@Service
public class AdminActionLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminActionLog.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Posé sur la requête quand une action détaillée a été journalisée : le journal générique s'abstient alors. */
    public static final String RECORDED_ATTRIBUTE = AdminActionLog.class.getName() + ".recorded";

    private final AdminActionLogRepository repository;

    public AdminActionLog(AdminActionLogRepository repository) {
        this.repository = repository;
    }

    /** Page du journal, les plus récentes d'abord. */
    public record Page(List<Entry> items, long total, int page, int size) {
    }

    /**
     * Enregistre une action. {@code before}/{@code after} : cartes de valeurs simples (texte, nombres, booléens), sérialisées en
     * JSON, jamais de secret ; ou {@code null}.
     */
    public void record(Long actorId, String action, String targetType, Object targetId, String summary,
            Object before, Object after, String reason) {
        repository.append(actorId, action, targetType, targetId == null ? null : String.valueOf(targetId), summary,
                json(before), json(after), reason == null || reason.isBlank() ? null : reason.trim());
        LOGGER.info("Action d'administration {} sur {} {} par le compte {}", action, targetType, targetId, actorId);
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            request.setAttribute(RECORDED_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public Page find(Filter filter, int page, int size) {
        return new Page(repository.find(filter, page * size, size), repository.count(filter), page, size);
    }

    private static String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Valeur de journal non sérialisable", exception);
        }
    }
}
