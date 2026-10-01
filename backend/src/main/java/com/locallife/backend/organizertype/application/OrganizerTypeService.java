package com.locallife.backend.organizertype.application;

import com.locallife.backend.organizertype.api.OrganizerTypeResponse;
import com.locallife.backend.organizertype.domain.OrganizerType;
import com.locallife.backend.organizertype.domain.OrganizerTypeTranslation;
import com.locallife.backend.organizertype.infrastructure.OrganizerTypeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service OrganizerType (CAT-004). Résout le label/la description de la
 * locale demandée — même logique de repli que les services des trois
 * référentiels précédents : locale demandée → {@code fr-FR} → {@code code}.
 *
 * Respecte le critère propre à ce ticket (« aucune logique métier ne
 * dépend du label ») : toute comparaison ici porte sur {@code code}
 * ({@link OrganizerTypeRepository#findByCode}), jamais sur un label.
 */
@Service
public class OrganizerTypeService {

    private static final String DEFAULT_LOCALE = "fr-FR";

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
        OrganizerTypeTranslation exact = findByLocale(type, locale);
        if (exact != null) {
            return exact;
        }
        return findByLocale(type, DEFAULT_LOCALE);
    }

    private OrganizerTypeTranslation findByLocale(OrganizerType type, String locale) {
        return type.translations().stream()
                .filter(t -> t.locale().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);
    }

}
