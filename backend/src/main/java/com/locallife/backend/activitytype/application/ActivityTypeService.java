package com.locallife.backend.activitytype.application;

import com.locallife.backend.activitytype.api.ActivityTypeResponse;
import com.locallife.backend.activitytype.domain.ActivityType;
import com.locallife.backend.activitytype.domain.ActivityTypeTranslation;
import com.locallife.backend.activitytype.infrastructure.ActivityTypeRepository;
import com.locallife.backend.common.i18n.LocaleResolver;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service ActivityType (CAT-002). Résout le label/la description de la
 * locale demandée à partir des {@link ActivityTypeTranslation} d'un type,
 * en déléguant la chaîne de repli à
 * {@link com.locallife.backend.common.i18n.LocaleResolver} (CAT-005) :
 * locale demandée → langue de base correspondante → {@code fr-FR} →
 * {@code CODE} (ce dernier maillon géré ici, dans {@link #toResponse}).
 */
@Service
public class ActivityTypeService {

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
        return LocaleResolver.resolve(type.translations(), locale, ActivityTypeTranslation::locale);
    }

}
