package com.locallife.backend.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.link.infrastructure.ActivityLinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre le critère d'acceptation « validation adaptée au type » de
 * {@link ActivityLinkService#create} (LL-11008) pour chaque type
 * reconnu (téléphone, email, URL par défaut), et la délégation des
 * autres méthodes.
 */
@ExtendWith(MockitoExtension.class)
class ActivityLinkServiceTest {

    @Mock
    private ActivityLinkRepository activityLinkRepository;

    private ActivityLinkService activityLinkService;

    @BeforeEach
    void setUp() {
        activityLinkService = new ActivityLinkService(activityLinkRepository);
    }

    private ActivityLink link(String type, String value) {
        return new ActivityLink(null, 1L, type, "Libellé", value);
    }

    @Test
    void create_ShouldSave_WhenTypeIsWebsiteAndValueIsValidUrl() {
        ActivityLink toCreate = link("WEBSITE", "https://example.com/marche-de-noel");
        ActivityLink saved = new ActivityLink(1L, 1L, "WEBSITE", "Libellé", "https://example.com/marche-de-noel");
        when(activityLinkRepository.save(toCreate)).thenReturn(saved);

        ActivityLink result = activityLinkService.create(toCreate);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void create_ShouldThrow_WhenTypeIsWebsiteAndValueIsNotAUrl() {
        ActivityLink invalid = link("WEBSITE", "pas une url");

        assertThatThrownBy(() -> activityLinkService.create(invalid)).isInstanceOf(IllegalArgumentException.class);

        verify(activityLinkRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrow_WhenTypeIsTicketingAndValueHasNoScheme() {
        // « validation adaptée au type » : TICKETING n'est ni PHONE ni EMAIL, donc validé comme
        // une URL au même titre que WEBSITE (tout type non reconnu tombe dans ce cas par défaut).
        ActivityLink invalid = link("TICKETING", "example.com/billetterie");

        assertThatThrownBy(() -> activityLinkService.create(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_ShouldSave_WhenTypeIsPhoneAndValueIsValidPhoneNumber() {
        ActivityLink toCreate = link(ActivityLinkService.TYPE_PHONE, "+33 4 90 00 00 00");
        when(activityLinkRepository.save(toCreate)).thenReturn(toCreate);

        ActivityLink result = activityLinkService.create(toCreate);

        assertThat(result).isEqualTo(toCreate);
    }

    @Test
    void create_ShouldThrow_WhenTypeIsPhoneAndValueIsNotAPhoneNumber() {
        ActivityLink invalid = link(ActivityLinkService.TYPE_PHONE, "appelez-nous");

        assertThatThrownBy(() -> activityLinkService.create(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_ShouldSave_WhenTypeIsEmailAndValueIsValidEmail() {
        ActivityLink toCreate = link(ActivityLinkService.TYPE_EMAIL, "contact@example.com");
        when(activityLinkRepository.save(toCreate)).thenReturn(toCreate);

        ActivityLink result = activityLinkService.create(toCreate);

        assertThat(result).isEqualTo(toCreate);
    }

    @Test
    void create_ShouldThrow_WhenTypeIsEmailAndValueIsNotAnEmail() {
        ActivityLink invalid = link(ActivityLinkService.TYPE_EMAIL, "pas-un-email");

        assertThatThrownBy(() -> activityLinkService.create(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_ShouldThrow_WhenValueIsAbsent() {
        ActivityLink withoutValue = link("WEBSITE", null);

        assertThatThrownBy(() -> activityLinkService.create(withoutValue))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findByActivityId_ShouldReturnAllLinksForActivity() {
        // Critère d'acceptation « plusieurs liens possibles ».
        ActivityLink first = new ActivityLink(1L, 42L, "WEBSITE", "Site", "https://example.com");
        ActivityLink second = new ActivityLink(2L, 42L, ActivityLinkService.TYPE_EMAIL, "Contact",
                "contact@example.com");
        when(activityLinkRepository.findByActivityId(42L)).thenReturn(List.of(first, second));

        List<ActivityLink> result = activityLinkService.findByActivityId(42L);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void findById_ShouldDelegateToRepository() {
        ActivityLink found = new ActivityLink(1L, 1L, "WEBSITE", "Site", "https://example.com");
        when(activityLinkRepository.findById(1L)).thenReturn(Optional.of(found));

        Optional<ActivityLink> result = activityLinkService.findById(1L);

        assertThat(result).contains(found);
    }
}
