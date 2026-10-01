package com.locallife.backend.audience.application;

import com.locallife.backend.audience.api.AudienceResponse;
import com.locallife.backend.audience.domain.Audience;
import com.locallife.backend.audience.domain.AudienceTranslation;
import com.locallife.backend.audience.infrastructure.AudienceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Audience (CAT-003). Résout le label/la description de la locale
 * demandée — même logique de repli que {@code CategoryService}/
 * {@code ActivityTypeService} : locale demandée → {@code fr-FR} →
 * {@code code}.
 */
@Service
public class AudienceService {

    private static final String DEFAULT_LOCALE = "fr-FR";

    private final AudienceRepository audienceRepository;

    public AudienceService(AudienceRepository audienceRepository) {
        this.audienceRepository = audienceRepository;
    }

    public List<AudienceResponse> getAllAudiences(String locale) {
        return audienceRepository.findAll().stream()
                .map(audience -> toResponse(audience, locale))
                .toList();
    }

    public Optional<AudienceResponse> getAudienceByCode(String code, String locale) {
        return audienceRepository.findByCode(code)
                .map(audience -> toResponse(audience, locale));
    }

    private AudienceResponse toResponse(Audience audience, String locale) {
        AudienceTranslation translation = resolveTranslation(audience, locale);
        String label = translation != null ? translation.label() : audience.code();
        String description = translation != null ? translation.description() : null;
        return new AudienceResponse(audience.code(), label, description, audience.active());
    }

    private AudienceTranslation resolveTranslation(Audience audience, String locale) {
        AudienceTranslation exact = findByLocale(audience, locale);
        if (exact != null) {
            return exact;
        }
        return findByLocale(audience, DEFAULT_LOCALE);
    }

    private AudienceTranslation findByLocale(Audience audience, String locale) {
        return audience.translations().stream()
                .filter(t -> t.locale().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);
    }

}
