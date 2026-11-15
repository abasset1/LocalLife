package com.locallife.backend.activity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.media.infrastructure.ActivityPhotoRepository;
import com.locallife.backend.media.infrastructure.ActivityPhotoRepository.Photo;
import com.locallife.backend.user.domain.Role;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityPhotoServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @Mock
    private ActivityPhotoRepository photoRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ContributionService contributionService;

    @InjectMocks
    private ActivityPhotoService service;

    private static Activity activity(String status, Long createdBy) {
        return new Activity(5L, "Concert", null, null, 43.3, 5.4, null, null, status, 1L, null, null,
                "1 rue", "Marseille", "13001", null, null, null, null, createdBy);
    }

    @Test
    void save_ByContributor_StoresImageAndRequiresRevalidation() {
        when(contributionService.findManageable(5L, 7L, false)).thenReturn(Optional.of(activity("PUBLISHED", 7L)));

        assertTrue(service.save(5L, 7L, false, "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG)));

        verify(photoRepository).save(eq(5L), eq("image/jpeg"), any());
        verify(photoRepository).requireRevalidation(5L);
    }

    @Test
    void save_ByAdmin_KeepsStatus() {
        when(contributionService.findManageable(5L, 1L, true)).thenReturn(Optional.of(activity("PUBLISHED", 7L)));

        service.save(5L, 1L, true, Base64.getEncoder().encodeToString(JPEG));

        verify(photoRepository, never()).requireRevalidation(anyLong());
    }

    @Test
    void save_UnknownActivity_ReturnsFalse() {
        when(contributionService.findManageable(5L, 7L, false)).thenReturn(Optional.empty());
        assertFalse(service.save(5L, 7L, false, "x"));
    }

    @Test
    void save_RejectsCancelledActivityAndUnsupportedFormat() {
        when(contributionService.findManageable(5L, 7L, false)).thenReturn(Optional.of(activity("CANCELLED", 7L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(5L, 7L, false, Base64.getEncoder().encodeToString(JPEG)));

        assertThrows(IllegalArgumentException.class, () -> ActivityPhotoService.contentType(new byte[] {1, 2, 3, 4}));
        assertThrows(IllegalArgumentException.class, () -> ActivityPhotoService.decode(" "));
        assertThrows(IllegalArgumentException.class, () -> ActivityPhotoService.decode("%%%"));
    }

    @Test
    void save_RejectsImageOverThreeMegabytes() {
        byte[] big = new byte[ActivityPhotoService.MAX_PHOTO_BYTES + 10];
        String encoded = Base64.getEncoder().encodeToString(big);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ActivityPhotoService.decode(encoded));
        assertEquals("L'image ne doit pas dépasser 3 Mo.", error.getMessage());
    }

    @Test
    void findVisible_HidesPendingImageFromOthers() {
        Photo photo = new Photo("image/jpeg", JPEG, OffsetDateTime.now());
        when(activityRepository.findById(5L)).thenReturn(Optional.of(activity("PENDING", 7L)));
        when(photoRepository.find(5L)).thenReturn(Optional.of(photo));

        assertTrue(service.findVisible(5L, null, null).isEmpty());
        assertTrue(service.findVisible(5L, 8L, Role.USER).isEmpty());
        assertTrue(service.findVisible(5L, 7L, Role.USER).isPresent());
        assertTrue(service.findVisible(5L, 9L, Role.LOCALLIFE).isPresent());
    }

    @Test
    void findVisible_ServesPublishedImageToEveryone() {
        Photo photo = new Photo("image/jpeg", JPEG, OffsetDateTime.now());
        when(activityRepository.findById(5L)).thenReturn(Optional.of(activity("PUBLISHED", 7L)));
        when(photoRepository.find(5L)).thenReturn(Optional.of(photo));

        assertTrue(service.findVisible(5L, null, null).isPresent());
    }
}
