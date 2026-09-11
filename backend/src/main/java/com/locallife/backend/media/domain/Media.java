package com.locallife.backend.media.domain;

import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Media (LL-11007, Sprint 11, section 10) : un média
 * (image pour l'instant, voir {@code type}) réutilisable, indépendant de
 * toute activité — la relation avec {@code Activity} passe par
 * {@link ActivityMedia}, pas par un champ ici, pour permettre à terme
 * (hors périmètre de ce ticket, non traité) la même relation avec
 * {@code Location} sans dupliquer le modèle {@code Media} lui-même.
 *
 * Champs repris tels quels du modèle du ticket. {@code url} est la seule
 * donnée réellement indispensable (sans elle, rien à afficher) — les
 * autres nullables : {@code width}/{@code height} (une source ne les
 * fournit pas toujours), {@code credit}/{@code altText} (texte libre,
 * absent selon la source).
 *
 * Aucune dépendance à un stockage particulier (critère d'acceptation
 * explicite du ticket) : {@code url} est un simple texte, jamais un
 * chemin de fichier local ni une référence à un service de stockage
 * (S3, CDN...) — ce modèle ne sait rien de la manière dont les médias
 * sont physiquement hébergés, seulement où les trouver.
 */
public record Media(
        @Id Long id,
        String url,
        String type,
        Integer width,
        Integer height,
        String credit,
        String altText) {
}
