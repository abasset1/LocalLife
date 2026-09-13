package com.locallife.backend.collector.domain;

import com.locallife.backend.metadata.domain.JsonbData;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine ExternalActivity (LL-11010, Sprint 11, section 13) :
 * conserve la richesse d'un élément collecté (voir {@link
 * CollectedActivity}) au-delà de ce que la normalisation
 * ({@code NormalizationService}) extrait vers {@code Activity} —
 * critère d'acceptation implicite du ticket (« sans la perdre lors de
 * la normalisation »).
 *
 * « Le JSON source sert de donnée de provenance et de récupération, pas
 * de modèle métier » (règle explicite du ticket) : {@code rawPayload}
 * n'est jamais parsé ni exposé tel quel par une API (critère
 * d'acceptation « aucun accès direct au JSON brut depuis le frontend »
 * — ce ticket ne crée d'ailleurs aucun endpoint, voir
 * {@code ExternalActivityService}) ; le domaine LocalLife reste
 * indépendant d'OpenAgenda (même principe que LL-11006/LL-11009).
 *
 * Champs repris tels quels de la section « Modèle cible » du ticket
 * (aucun {@code activityId} : le lien vers l'{@code Activity} normalisée
 * correspondante n'est pas demandé par ce ticket, voir LL-11011 pour le
 * branchement réel dans le pipeline d'import).
 *
 * {@code sourceId}/{@code externalId} identifient l'élément source de
 * façon stable dans le temps (unique en base, voir
 * {@code V25__create_external_activity_table.sql}) — plusieurs
 * collectes successives du même élément mettent à jour la même ligne
 * ({@code lastSeenAt}/{@code payloadHash}/{@code rawPayload}) plutôt que
 * d'en créer une nouvelle, voir {@code ExternalActivityService#upsert}.
 *
 * {@code sourceUpdatedAt} nullable (« conservée lorsqu'elle existe »,
 * critère d'acceptation explicite — une source ne fournit pas toujours
 * de date de modification). {@code payloadHash} sert le critère
 * d'acceptation « détection de modification possible » : comparer deux
 * empreintes est trivial et bon marché, pas besoin de parser/comparer
 * {@code rawPayload} lui-même pour savoir s'il a changé depuis la
 * dernière collecte — voir {@code ExternalActivityService#computeHash}.
 *
 * {@code rawPayload} typé {@link JsonbData} depuis le commit « Fix:
 * Scope JSONB JDBC conversions » (post-LL-11011) : un convertisseur JDBC
 * {@code String -> PGobject} enregistré globalement pour toute colonne
 * {@code jsonb} interférait avec les autres colonnes texte de
 * l'application (voir la javadoc de {@link JsonbData}) — {@code
 * raw_payload} est lui aussi une colonne {@code jsonb} (voir {@code
 * V25__create_external_activity_table.sql}), donc concerné au même
 * titre qu'{@code ActivityMetadata#data}. Constructeur de commodité
 * acceptant un {@code String} conservé (même principe que sur {@code
 * ActivityMetadata}), pour ne pas obliger chaque appelant à écrire
 * {@code new JsonbData(...)} explicitement.
 */
public record ExternalActivity(
        @Id Long id,
        Long sourceId,
        String externalId,
        String externalUrl,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime lastSeenAt,
        String payloadHash,
        JsonbData rawPayload) {

    public ExternalActivity(
            Long id, Long sourceId, String externalId, String externalUrl, LocalDateTime sourceUpdatedAt,
            LocalDateTime lastSeenAt, String payloadHash, String rawPayload) {
        this(id, sourceId, externalId, externalUrl, sourceUpdatedAt, lastSeenAt, payloadHash,
                new JsonbData(rawPayload));
    }
}
