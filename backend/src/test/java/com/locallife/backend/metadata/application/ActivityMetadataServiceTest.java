package com.locallife.backend.metadata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.metadata.domain.ActivityMetadata;
import com.locallife.backend.metadata.infrastructure.ActivityMetadataRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la validation JSON de {@link ActivityMetadataService#create}
 * (LL-11009) et la délégation des autres méthodes.
 */
@ExtendWith(MockitoExtension.class)
class ActivityMetadataServiceTest {

    @Mock
    private ActivityMetadataRepository activityMetadataRepository;

    private ActivityMetadataService activityMetadataService;

    @BeforeEach
    void setUp() {
        activityMetadataService = new ActivityMetadataService(activityMetadataRepository);
    }

    @Test
    void create_ShouldSave_WhenDataIsValidJson() {
        ActivityMetadata toCreate = new ActivityMetadata(null, 1L, "{\"sourceField\": \"valeur\"}");
        ActivityMetadata saved = new ActivityMetadata(1L, 1L, "{\"sourceField\": \"valeur\"}");
        when(activityMetadataRepository.save(toCreate)).thenReturn(saved);

        ActivityMetadata result = activityMetadataService.create(toCreate);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void create_ShouldSave_WhenDataIsAJsonArray() {
        // « données spécifiques » : pas nécessairement un objet, un tableau JSON reste valide.
        ActivityMetadata toCreate = new ActivityMetadata(null, 1L, "[1, 2, 3]");
        when(activityMetadataRepository.save(toCreate)).thenReturn(toCreate);

        ActivityMetadata result = activityMetadataService.create(toCreate);

        assertThat(result).isEqualTo(toCreate);
    }

    @Test
    void create_ShouldThrow_WhenDataIsNotValidJson() {
        ActivityMetadata invalid = new ActivityMetadata(null, 1L, "pas du json");

        assertThatThrownBy(() -> activityMetadataService.create(invalid))
                .isInstanceOf(IllegalArgumentException.class);

        verify(activityMetadataRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrow_WhenDataIsAbsent() {
        ActivityMetadata withoutData = new ActivityMetadata(null, 1L, null);

        assertThatThrownBy(() -> activityMetadataService.create(withoutData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findByActivityId_ShouldDelegateToRepository() {
        ActivityMetadata found = new ActivityMetadata(1L, 42L, "{}");
        when(activityMetadataRepository.findByActivityId(42L)).thenReturn(Optional.of(found));

        Optional<ActivityMetadata> result = activityMetadataService.findByActivityId(42L);

        assertThat(result).contains(found);
    }
}
