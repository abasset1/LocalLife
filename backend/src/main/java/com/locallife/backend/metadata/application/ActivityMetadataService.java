package com.locallife.backend.metadata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.metadata.domain.ActivityMetadata;
import com.locallife.backend.metadata.infrastructure.ActivityMetadataRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service ActivityMetadata (LL-11009). {@link #create} valide que
 * {@code data} est un JSON syntaxiquement correct avant persistance —
 * sinon {@code StringToJsonbConverter} échouerait de toute façon côté
 * base (colonne {@code jsonb}), mais avec une erreur SQL peu lisible
 * plutôt qu'un message applicatif clair. Utilise {@link ObjectMapper}
 * (déjà présent via {@code spring-boot-starter-web}, aucune dépendance
 * supplémentaire) uniquement pour valider la syntaxe — {@code data}
 * reste stocké tel quel en texte, jamais désérialisé vers un type Java
 * métier (voir la javadoc de {@link ActivityMetadata}).
 */
@Service
public class ActivityMetadataService {

    private final ActivityMetadataRepository activityMetadataRepository;
    private final ObjectMapper objectMapper;

    public ActivityMetadataService(ActivityMetadataRepository activityMetadataRepository) {
        this.activityMetadataRepository = activityMetadataRepository;
        this.objectMapper = new ObjectMapper();
    }

    public Optional<ActivityMetadata> findByActivityId(Long activityId) {
        return activityMetadataRepository.findByActivityId(activityId);
    }

    /**
     * @throws IllegalArgumentException si {@code activityMetadata.data()} n'est pas un JSON valide
     */
    public ActivityMetadata create(ActivityMetadata activityMetadata) {
        validateJson(activityMetadata.data());
        return activityMetadataRepository.save(activityMetadata);
    }

    private void validateJson(String data) {
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("La donnée metadata ne peut pas être vide.");
        }
        try {
            objectMapper.readTree(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Metadata n'est pas un JSON valide : " + data, exception);
        }
    }
}
