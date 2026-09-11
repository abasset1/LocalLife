package com.locallife.backend.tag.domain;

import org.springframework.data.annotation.Id;

/**
 * Entité de domaine ActivityTag (LL-11009, Sprint 11, section 12) : un
 * tag associé à une activité — critère d'acceptation « tags conservés »
 * / « permettre plusieurs tags par activité ». Une ligne par tag
 * (contrainte {@code UNIQUE(activity_id, tag)}, voir
 * {@code V24__create_accessibility_tag_metadata_tables.sql}) plutôt
 * qu'un tableau/texte concaténé sur {@code Activity} : permet
 * d'interroger simplement (ex. « toutes les activités avec le tag X »)
 * sans parsing applicatif.
 *
 * Pas d'entité {@code Tag} séparée avec table de liaison (contrairement
 * à {@code Media}/{@code ActivityMedia}, LL-11007) : un tag est un
 * simple texte, réutilisé par valeur d'une activité à l'autre sans
 * bénéfice à en faire une entité partagée avec sa propre identité — pas
 * de besoin exprimé par le ticket d'un vocabulaire de tags canonique ou
 * de métadonnées propres au tag lui-même.
 */
public record ActivityTag(
        @Id Long id,
        Long activityId,
        String tag) {
}
