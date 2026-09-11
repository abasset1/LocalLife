package com.locallife.backend.accessibility.application;

import com.locallife.backend.accessibility.domain.Accessibility;
import com.locallife.backend.accessibility.infrastructure.AccessibilityRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Accessibility (LL-11009). Délégation simple vers le
 * repository — l'intégrité référentielle et l'unicité (une seule fiche
 * d'accessibilité par activité) sont garanties par les contraintes
 * {@code FOREIGN KEY}/{@code UNIQUE} de
 * {@code V24__create_accessibility_tag_metadata_tables.sql}.
 */
@Service
public class AccessibilityService {

    private final AccessibilityRepository accessibilityRepository;

    public AccessibilityService(AccessibilityRepository accessibilityRepository) {
        this.accessibilityRepository = accessibilityRepository;
    }

    public Optional<Accessibility> findByActivityId(Long activityId) {
        return accessibilityRepository.findByActivityId(activityId);
    }

    public Accessibility create(Accessibility accessibility) {
        return accessibilityRepository.save(accessibility);
    }
}
