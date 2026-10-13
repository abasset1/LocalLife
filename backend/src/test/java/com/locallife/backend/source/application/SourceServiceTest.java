package com.locallife.backend.source.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private SourceService sourceService;

    @Test
    void createSource_ShouldSaveAndReturnSource_WithActiveStatusAndNoLastSync() {
        // Given
        Source saved = new Source(
                1L, "OpenAgenda Marseille", "API", "https://api.openagenda.com", "ACTIVE", null,
                "agenda-uid", "PACA");
        when(sourceRepository.save(any(Source.class))).thenReturn(saved);

        // When
        Source result = sourceService.createSource(
                "OpenAgenda Marseille", "API", "https://api.openagenda.com", "agenda-uid", "PACA");

        // Then
        assertEquals(1L, result.id());
        assertEquals("OpenAgenda Marseille", result.name());
        assertEquals("API", result.type());
        assertEquals("ACTIVE", result.status());
        verify(sourceRepository).save(argThat(source ->
                source.id() == null && "ACTIVE".equals(source.status()) && source.lastSyncAt() == null));
    }

    @Test
    void createSource_ShouldThrow_WhenNameIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("  ", "API", null, null, null));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void createSource_ShouldThrow_WhenTypeIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("Nom", "", null, null, null));
        verify(sourceRepository, never()).save(any());
    }

    /**
     * LL-11016 : bug corrigé — cette validation manquait, ce qui
     * permettait de créer une seconde source de type {@code MANUAL} via
     * l'API, cassant l'hypothèse d'unicité que {@code
     * SourceRepository#findByType("MANUAL")} fait partout ailleurs dans
     * le projet (voir aussi {@code V30__enforce_single_manual_source.sql}
     * pour la contrainte correspondante côté base).
     */
    @Test
    void createSource_ShouldThrow_WhenTypeIsManual() {
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("Nom", "MANUAL", null, null, null));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void updateSource_ShouldThrow_WhenTypeIsManual() {
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.updateSource(1L, "Nom", "MANUAL", null, "ACTIVE", null, null));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void getAllSources_ShouldReturnAllSourcesFromRepository() {
        // Given
        List<Source> sources = List.of(
                new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null),
                new Source(2L, "OpenAgenda Marseille", "API", "https://api.openagenda.com", "ACTIVE", null,
                        "agenda-uid", null));
        when(sourceRepository.findAll()).thenReturn(sources);

        // When
        List<Source> result = sourceService.getAllSources();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void getSourceById_ShouldReturnSource_WhenFound() {
        // Given
        Source source = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(source));

        // When
        Optional<Source> result = sourceService.getSourceById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Saisie manuelle", result.get().name());
    }

    @Test
    void getSourceById_ShouldReturnEmpty_WhenNotFound() {
        // Given
        when(sourceRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Source> result = sourceService.getSourceById(999L);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findByType_ShouldDelegateToRepository() {
        // Given
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        when(sourceRepository.findByType("MANUAL")).thenReturn(Optional.of(manualSource));

        // When
        Optional<Source> result = sourceService.findByType("MANUAL");

        // Then
        assertTrue(result.isPresent());
        assertEquals("Saisie manuelle", result.get().name());
    }

    @Test
    void updateSource_ShouldSaveAndReturnUpdatedSource_PreservingLastSyncAt() {
        // Given
        LocalDateTime lastSyncAt = LocalDateTime.of(2026, 9, 1, 8, 0);
        Source existing = new Source(1L, "Ancien nom", "API", null, "ACTIVE", lastSyncAt, "old-uid", null);
        Source saved = new Source(1L, "Nouveau nom", "API", "https://x", "INACTIVE", lastSyncAt, "new-uid", "PACA");
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sourceRepository.save(any(Source.class))).thenReturn(saved);

        // When
        Optional<Source> result = sourceService.updateSource(
                1L, "Nouveau nom", "API", "https://x", "INACTIVE", "new-uid", "PACA");

        // Then
        assertTrue(result.isPresent());
        assertEquals("Nouveau nom", result.get().name());
        verify(sourceRepository).save(argThat(source -> lastSyncAt.equals(source.lastSyncAt())));
    }

    @Test
    void updateSource_ShouldReturnEmpty_WhenSourceDoesNotExist() {
        // Given
        when(sourceRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        Optional<Source> result = sourceService.updateSource(99L, "Nom", "API", null, "ACTIVE", null, null);

        // Then
        assertFalse(result.isPresent());
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void updateSource_ShouldThrow_WhenNameIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.updateSource(1L, "", "API", null, "ACTIVE", null, null));
        verify(sourceRepository, never()).findById(any());
    }

    @Test
    void recordSync_ShouldSaveSourceWithUpdatedLastSyncAt_WhenSourceExists() {
        // Given : bug corrigé — lastSyncAt restait toujours null (voir ImportService).
        LocalDateTime syncedAt = LocalDateTime.of(2026, 9, 9, 10, 30);
        Source existing = new Source(
                2L, "OpenAgenda Marseille", "API", "https://x", "ACTIVE", null, "agenda-uid", "PACA");
        when(sourceRepository.findById(2L)).thenReturn(Optional.of(existing));

        // When
        sourceService.recordSync(2L, syncedAt);

        // Then : tous les autres champs préservés, seul lastSyncAt change.
        verify(sourceRepository).save(new Source(
                2L, "OpenAgenda Marseille", "API", "https://x", "ACTIVE", syncedAt, "agenda-uid", "PACA"));
    }

    @Test
    void recordSync_ShouldDoNothing_WhenSourceDoesNotExist() {
        // Given
        when(sourceRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        sourceService.recordSync(99L, LocalDateTime.now());

        // Then
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void deleteSource_ShouldDeleteAndReturnSource_WhenNotReservedManual() {
        // Given
        Source source = new Source(2L, "OpenAgenda Marseille", "API", null, "ACTIVE", null, "agenda-uid", null);
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        when(sourceRepository.findById(2L)).thenReturn(Optional.of(source));
        when(sourceRepository.findByType("MANUAL")).thenReturn(Optional.of(manualSource));
        when(activityRepository.findBySourceId(2L)).thenReturn(List.of());

        // When
        Optional<Source> result = sourceService.deleteSource(2L);

        // Then
        assertTrue(result.isPresent());
        verify(sourceRepository).deleteById(2L);
    }

    @Test
    void deleteSource_ShouldDetachLinkedActivities_ToReservedManualSource() {
        // Given : décision Alex (LL-EF-005) : suppression autorisée, activités détachées
        // vers la source réservée MANUAL plutôt que la suppression bloquée.
        Source source = new Source(2L, "OpenAgenda Marseille", "API", null, "ACTIVE", null, "agenda-uid", null);
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        Activity linkedActivity = new Activity(
                42L, "Marché de Noël", "description", "marché", 43.2965, 5.3698,
                LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED", 2L, "external:key", null,
                null, null, null, null, null, null, null, null);
        when(sourceRepository.findById(2L)).thenReturn(Optional.of(source));
        when(sourceRepository.findByType("MANUAL")).thenReturn(Optional.of(manualSource));
        when(activityRepository.findBySourceId(2L)).thenReturn(List.of(linkedActivity));

        // When
        sourceService.deleteSource(2L);

        // Then
        verify(activityRepository).save(argThat(activity ->
                activity.id().equals(42L) && activity.sourceId().equals(1L)));
        verify(sourceRepository).deleteById(2L);
    }

    @Test
    void deleteSource_ShouldThrow_WhenSourceIsReservedManual() {
        // Given
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(manualSource));

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> sourceService.deleteSource(1L));
        verify(sourceRepository, never()).deleteById(any());
        verify(activityRepository, never()).findBySourceId(any());
    }

    @Test
    void deleteSource_ShouldReturnEmpty_WhenSourceDoesNotExist() {
        // Given
        when(sourceRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        Optional<Source> result = sourceService.deleteSource(99L);

        // Then
        assertFalse(result.isPresent());
        verify(sourceRepository, never()).deleteById(any());
    }


    private static final String DATATOURISME_URL =
            "https://www.data.gouv.fr/api/1/datasets/r/83a1f131-9e23-4c3b-b1c6-e58f33fe7b80";

    private static Source dataTourisme(String status, String url, String regionFilter) {
        return new Source(5L, "DATAtourisme PACA", "DATATOURISME", url, status, null, null, regionFilter);
    }

    @Test
    void createSource_ShouldCreateDataTourismeSourceInactive() {
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Source created = sourceService.createSource("DATAtourisme PACA", "DATATOURISME", DATATOURISME_URL, null,
                "84, 13");

        assertEquals("INACTIVE", created.status());
    }

    @Test
    void createSource_ShouldRejectInvalidDataTourismeConfiguration() {
        // URL qui n'est pas l'URL stable d'une ressource data.gouv.fr (ex. lien direct au fichier du jour).
        assertThrows(IllegalArgumentException.class, () -> sourceService.createSource("DT", "DATATOURISME",
                "https://static.data.gouv.fr/resources/x/20261013-030448/datatourisme-reg-pac.csv", null, "84"));
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("DT", "DATATOURISME", null, null, null));
        // Filtre : un département s'écrit comme un préfixe de code postal.
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("DT", "DATATOURISME", DATATOURISME_URL, null, "Vaucluse"));
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("DT", "DATATOURISME", DATATOURISME_URL, null, "84,2A"));
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("DT", "DATATOURISME", DATATOURISME_URL, null, "96"));
        // Pas d'identifiant d'agenda OpenAgenda sur une source DATAtourisme.
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("DT", "DATATOURISME", DATATOURISME_URL, "79839448", null));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void validateDataTourismeConfiguration_ShouldAcceptDepartmentPostalPrefixes() {
        SourceService.validateDataTourismeConfiguration(DATATOURISME_URL, null, null);
        SourceService.validateDataTourismeConfiguration(DATATOURISME_URL, "", "84,13, 20 ,971");
    }

    @Test
    void updateSource_ShouldRefuseToActivateDataTourismeSourceDirectly() {
        when(sourceRepository.findById(5L)).thenReturn(Optional.of(dataTourisme("INACTIVE", DATATOURISME_URL, "84")));

        assertThrows(IllegalArgumentException.class, () -> sourceService.updateSource(
                5L, "DATAtourisme PACA", "DATATOURISME", DATATOURISME_URL, "ACTIVE", null, "84"));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void updateSource_ShouldRefuseToChangeConfigurationOfActiveDataTourismeSource() {
        when(sourceRepository.findById(5L)).thenReturn(Optional.of(dataTourisme("ACTIVE", DATATOURISME_URL, "84")));

        // Le filtre vérifié était « 84 » : l'élargir sans nouvel essai reviendrait à collecter sans vérification.
        assertThrows(IllegalArgumentException.class, () -> sourceService.updateSource(
                5L, "DATAtourisme PACA", "DATATOURISME", DATATOURISME_URL, "ACTIVE", null, "84,13"));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void updateSource_ShouldAllowRenamingOrDeactivatingDataTourismeSource() {
        when(sourceRepository.findById(5L)).thenReturn(Optional.of(dataTourisme("ACTIVE", DATATOURISME_URL, "84")));
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Source renamed = sourceService.updateSource(
                5L, "DATAtourisme Vaucluse", "DATATOURISME", DATATOURISME_URL, "ACTIVE", null, " 84 ").orElseThrow();
        Source deactivated = sourceService.updateSource(
                5L, "DATAtourisme PACA", "DATATOURISME", DATATOURISME_URL, "INACTIVE", null, "84,13").orElseThrow();

        assertEquals("ACTIVE", renamed.status());
        assertEquals("INACTIVE", deactivated.status());
    }

    @Test
    void activateAfterVerification_ShouldSetActiveAndKeepConfiguration() {
        when(sourceRepository.findById(5L)).thenReturn(Optional.of(dataTourisme("INACTIVE", DATATOURISME_URL, "84")));
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Source activated = sourceService.activateAfterVerification(5L).orElseThrow();

        assertEquals("ACTIVE", activated.status());
        assertEquals(DATATOURISME_URL, activated.url());
        assertEquals("84", activated.regionFilter());
    }
}
