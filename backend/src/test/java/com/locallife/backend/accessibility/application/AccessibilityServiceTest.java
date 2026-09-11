package com.locallife.backend.accessibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.accessibility.domain.Accessibility;
import com.locallife.backend.accessibility.infrastructure.AccessibilityRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la délégation de {@link AccessibilityService} vers
 * {@link AccessibilityRepository} (LL-11009).
 */
@ExtendWith(MockitoExtension.class)
class AccessibilityServiceTest {

    @Mock
    private AccessibilityRepository accessibilityRepository;

    private AccessibilityService accessibilityService;

    @BeforeEach
    void setUp() {
        accessibilityService = new AccessibilityService(accessibilityRepository);
    }

    @Test
    void create_ShouldDelegateToRepository() {
        Accessibility toCreate = new Accessibility(null, 1L, true, false, null, null, null);
        Accessibility saved = new Accessibility(1L, 1L, true, false, null, null, null);
        when(accessibilityRepository.save(toCreate)).thenReturn(saved);

        Accessibility result = accessibilityService.create(toCreate);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void findByActivityId_ShouldDelegateToRepository() {
        Accessibility found = new Accessibility(1L, 42L, true, null, null, null, null);
        when(accessibilityRepository.findByActivityId(42L)).thenReturn(Optional.of(found));

        Optional<Accessibility> result = accessibilityService.findByActivityId(42L);

        assertThat(result).contains(found);
    }
}
