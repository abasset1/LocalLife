package com.locallife.backend.audience.application;

import com.locallife.backend.audience.api.AudienceResponse;
import com.locallife.backend.audience.domain.Audience;
import com.locallife.backend.audience.domain.AudienceTranslation;
import com.locallife.backend.audience.infrastructure.AudienceRepository;
import com.locallife.backend.common.i18n.LocaleResolver;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Audience (CAT-003). Résout le label/la description de la locale
 * demandée à partir des {@link AudienceTranslation} d'un public, en
 * déléguant la chaîne de repli à
 * {@link com.locallife.backend.common.i18n.LocaleResolver} (CAT-005) :
 * locale demandée → langue de base correspondante → {@code fr-FR} →
 * {@code CODE} (ce dernier maillon géré ici, dans {@link #toResponse}).
 */
@Service
public class AudienceService {

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
        return LocaleResolver.resolve(audience.translations(), locale, AudienceTranslation::locale);
    }

}
