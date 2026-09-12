package com.locallife.backend.metadata.domain;

import org.springframework.data.annotation.Id;

/**
 * Entité de domaine ActivityMetadata (LL-11009, Sprint 11, section 12) :
 * données spécifiques ou évolutives issues d'une source, qui ne
 * constituent pas encore des données métier de premier niveau (donc pas
 * de colonne/table dédiée par champ — critère d'acceptation explicite
 * « aucune table créée inutilement pour chaque champ d'une source »,
 * contrairement à LL-11006 où {@code longDescription}/{@code
 * conditions}/{@code age} avaient justement été jugées suffisamment
 * importantes pour devenir des colonnes de {@code Activity}).
 *
 * Table séparée de {@code activity} (pas une colonne JSONB directement
 * sur {@code Activity}) : évite d'alourdir le constructeur de {@code
 * Activity} (déjà 19 champs après LL-11006) pour une relation qui reste
 * au fond une extension optionnelle, un-à-un, de l'activité —
 * {@code activityId} unique en base (voir
 * {@code V24__create_activity_metadata_table.sql}).
 *
 * {@code data} : texte JSON brut (pas de structure Java dédiée — c'est
 * précisément le point, une structure Java par source reviendrait à
 * recréer le problème que ce ticket cherche à éviter), mappé vers une
 * colonne JSONB par {@code StringToJsonbConverter}/{@code
 * JsonbToStringConverter} — voir
 * {@code docs/02_Architecture/ADR-0004-metadata-jsonb.md} pour le
 * choix technique.
 */
public record ActivityMetadata(
        @Id Long id,
        Long activityId,
        JsonbData data) {

    public ActivityMetadata(Long id, Long activityId, String data) {
        this(id, activityId, new JsonbData(data));
    }
}
