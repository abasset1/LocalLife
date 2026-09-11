package com.locallife.backend.link.domain;

import org.springframework.data.annotation.Id;

/**
 * Entité de domaine ActivityLink (LL-11008, Sprint 11, section 11) : un
 * lien ou contact permettant d'obtenir davantage d'informations sur une
 * activité (site officiel, billetterie, réservation, programme, réseau
 * social, téléphone, email — exemples du ticket, pas une liste fermée).
 *
 * Contrairement à {@code Media}/{@code ActivityMedia} (LL-11007), pas de
 * table de liaison séparée : un lien n'a de sens que rattaché à une
 * seule activité (jamais partagé entre plusieurs), un simple
 * {@code activityId} suffit — même convention par id que
 * {@code Schedule.activityId} (LL-11002).
 *
 * {@code type} : texte libre (même convention que {@code
 * Activity.category}/{@code status}, pas d'enum), mais {@code
 * ActivityLinkService#create} reconnaît {@code PHONE}/{@code EMAIL}
 * (voir ses constantes) pour appliquer une validation adaptée — critère
 * d'acceptation explicite « validation adaptée au type » — un type
 * autre que ces deux-là est validé comme une URL. {@code label} :
 * libellé lisible, nullable (une source peut ne fournir qu'un type et
 * une valeur, sans texte d'affichage dédié). {@code value} : le
 * contenu du lien lui-même (URL, numéro de téléphone, ou adresse
 * email selon {@code type}) — nommé génériquement plutôt que
 * {@code url}, qui prêterait à confusion pour un contact téléphone/
 * email.
 */
public record ActivityLink(
        @Id Long id,
        Long activityId,
        String type,
        String label,
        String value) {
}
