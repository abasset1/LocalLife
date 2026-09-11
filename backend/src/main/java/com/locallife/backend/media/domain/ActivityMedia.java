package com.locallife.backend.media.domain;

import org.springframework.data.annotation.Id;

/**
 * Association entre une activité et un média, avec position d'affichage
 * (LL-11007). Référence {@code Activity}/{@link Media} par id uniquement
 * — même convention que {@code Schedule.activityId} (LL-11002), voir
 * {@code ARCHITECTURE.md}.
 *
 * {@code position} sert directement le critère d'acceptation « ordre
 * conservé » : entier explicite plutôt que l'ordre d'insertion en base
 * (non garanti côté lecture sans {@code ORDER BY} explicite) — voir
 * {@code ActivityMediaRepository#findByActivityIdOrderByPosition}.
 *
 * Table de liaison séparée de {@link Media} (plutôt qu'un champ
 * {@code activityId} directement sur {@code Media}) : un même média
 * pourra à terme être associé à plusieurs entités (activité, et plus
 * tard lieu — hors périmètre de ce ticket) sans dupliquer la ligne
 * {@code Media} elle-même.
 */
public record ActivityMedia(
        @Id Long id,
        Long activityId,
        Long mediaId,
        int position) {
}
