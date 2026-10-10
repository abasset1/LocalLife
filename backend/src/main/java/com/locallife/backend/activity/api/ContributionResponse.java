package com.locallife.backend.activity.api;

import com.locallife.backend.activity.api.ActivityController.RecurrenceRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contribution vue par son auteur (ou un administrateur) : de quoi l'afficher dans « Mes contributions »
 * avec son statut de validation, et pré-remplir le formulaire de modification — mêmes champs que
 * {@link ActivityController.CreateActivityRequest}.
 *
 * @param recurrence récurrence hebdomadaire, {@code null} pour une activité ponctuelle ou sur une période
 */
public record ContributionResponse(
        Long id,
        String title,
        String description,
        String status,
        String address,
        String city,
        LocalDateTime startDate,
        LocalDateTime endDate,
        List<String> categoryCodes,
        String url,
        String phone,
        String email,
        RecurrenceRequest recurrence) {
}
