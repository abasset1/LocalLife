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
 *
 * {@code agendaUid} et {@code regionFilter} (LL-EF-005, migration
 * {@code V14__add_agenda_fields_to_source.sql}) ne sont significatifs que
 * pour une source de type {@code API} destinée à être collectée via
 * OpenAgenda ({@link com.locallife.backend.collector.infrastructure.OpenAgendaCollectorFactory}) —
 * {@code null} pour {@code RSS}/{@code MANUAL}. Remplacent la
 * configuration par propriétés ({@code OpenAgendaSourcesConfig}, supprimée
 * par ce ticket) : une source {@code API} avec un {@code agendaUid} non
 * vide et un statut {@code ACTIVE} est désormais collectée automatiquement
 * par {@code ImportService}, sans redémarrage de l'application. La clé API
 * OpenAgenda elle-même reste hors base (secret partagé entre agendas,
 * toujours lu depuis {@code openagenda.api-key}) : seul l'identifiant
 * d'agenda (non sensible) est géré ici.
 */
public record Source(
        @Id Long id,
        String name,
        String type,
        String url,
        String status,
        LocalDateTime lastSyncAt,
        String agendaUid,
        String regionFilter) {
}
