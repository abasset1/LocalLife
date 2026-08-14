package com.locallife.backend.source.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Source (LL-5001). Identifie l'origine d'une activité,
 * qu'elle soit importée depuis une source externe ou créée manuellement.
 *
 * {@code type} et {@code status} sont des chaînes libres plutôt que des
 * enums Java, comme {@link com.locallife.backend.activity.domain.Activity}
 * pour {@code status} — voir {@code SOURCE_CONTRACT.md} pour les valeurs
 * actuellement supportées ({@code API}, {@code RSS}, {@code MANUAL} pour
 * {@code type} ; {@code ACTIVE}, {@code INACTIVE}, {@code ERROR} pour
 * {@code status}).
 *
 * Une source réservée de type {@code MANUAL} (créée par la migration
 * {@code V8__create_source_table.sql}) permet aux activités créées
 * manuellement de rester compatibles avec ce modèle sans introduire de
 * source nulle (décision documentée dans {@code SOURCE_CONTRACT.md}).
 */
public record Source(
        @Id Long id,
        String name,
        String type,
        String url,
        String status,
        LocalDateTime lastSyncAt) {
}
