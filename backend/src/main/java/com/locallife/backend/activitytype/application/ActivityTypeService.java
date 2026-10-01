package com.locallife.backend.activitytype.application;

import com.locallife.backend.activitytype.api.ActivityTypeResponse;
import com.locallife.backend.activitytype.domain.ActivityType;
import com.locallife.backend.activitytype.domain.ActivityTypeTranslation;
import com.locallife.backend.activitytype.infrastructure.ActivityTypeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service ActivityType (CAT-002). Résout le label/la description de la
 * locale demandée à partir des {@link ActivityTypeTranslation} d'un type —
 * même logique de repli que {@code CategoryService} (CAT-001) : locale
 * demandée → {@code fr-FR} → {@code code} (chaîne complète décrite par
 * CAT-005, réduite à ses deux derniers maillons tant qu'une seule locale
 * existe).
 */
@Service
public class ActivityTypeService {

    private static final String DEFAULT_LOCALE = "fr-FR";

    private final ActivityTypeRepository activityTypeRepository;

    public ActivityTypeService(ActivityTypeRepository activityTypeRepository) {
        this.activityTypeRepository = activityTypeRepository;
    }

    public List<ActivityTypeResponse> getAllActivityTypes(String locale) {
        return activityTypeRepository.findAll().stream()
                .map(type -> toResponse(type, locale))
                .toList();
    }

    public Optional<ActivityTypeResponse> getActivityTypeByCode(String code, String locale) {
        return activityTypeRepository.findByCode(code)
                .map(type -> toResponse(type, locale));
    }

    private ActivityTypeResponse toResponse(ActivityType type, String locale) {
        ActivityTypeTranslation translation = resolveTranslation(type, locale);
        String label = translation != null ? translation.label() : type.code();
        String description = translation != null ? translation.description() : null;
        return new ActivityTypeResponse(type.code(), label, description, type.active());
    }

    private ActivityTypeTranslation resolveTranslation(ActivityType type, String locale) {
        ActivityTypeTranslation exact = findByLocale(type, locale);
        if (exact != null) {
            return exact;
        }
        return findByLocale(type, DEFAULT_LOCALE);
    }

    private ActivityTypeTranslation findByLocale(ActivityType type, String locale) {
        return type.translations().stream()
                .filter(t -> t.locale().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);
    }

}
