package com.locallife.backend.organizertype.application;

import com.locallife.backend.common.i18n.LocaleResolver;
import com.locallife.backend.organizertype.api.OrganizerTypeResponse;
import com.locallife.backend.organizertype.domain.OrganizerType;
import com.locallife.backend.organizertype.domain.OrganizerTypeTranslation;
import com.locallife.backend.organizertype.infrastructure.OrganizerTypeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service OrganizerType (CAT-004). Résout le label/la description de la
 * locale demandée à partir des {@link OrganizerTypeTranslation} d'un type,
 * en déléguant la chaîne de repli à
 * {@link com.locallife.backend.common.i18n.LocaleResolver} (CAT-005) :
 * locale demandée → langue de base correspondante → {@code fr-FR} →
 * {@code CODE} (ce dernier maillon géré ici, dans {@link #toResponse}).
 *
 * Respecte le critère propre à CAT-004 (« aucune logique métier ne dépend
 * du label ») : toute comparaison ici porte sur {@code code}
 * ({@link OrganizerTypeRepository#findByCode}), jamais sur un label.
 */
@Service
public class OrganizerTypeService {

    private final OrganizerTypeRepository organizerTypeRepository;

    public OrganizerTypeService(OrganizerTypeRepository organizerTypeRepository) {
        this.organizerTypeRepository = organizerTypeRepository;
    }

    public List<OrganizerTypeResponse> getAllOrganizerTypes(String locale) {
        return organizerTypeRepository.findAll().stream()
                .map(type -> toResponse(type, locale))
                .toList();
    }

    public Optional<OrganizerTypeResponse> getOrganizerTypeByCode(String code, String locale) {
        return organizerTypeRepository.findByCode(code)
                .map(type -> toResponse(type, locale));
    }

    private OrganizerTypeResponse toResponse(OrganizerType type, String locale) {
        OrganizerTypeTranslation translation = resolveTranslation(type, locale);
        String label = translation != null ? translation.label() : type.code();
        String description = translation != null ? translation.description() : null;
        return new OrganizerTypeResponse(type.code(), label, description, type.active());
    }

    private OrganizerTypeTranslation resolveTranslation(OrganizerType type, String locale) {
        return LocaleResolver.resolve(type.translations(), locale, OrganizerTypeTranslation::locale);
    }

}
