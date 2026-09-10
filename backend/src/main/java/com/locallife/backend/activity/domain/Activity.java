package com.locallife.backend.activity.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Activity.
 *
 * {@code sourceId} et {@code importKey} ajoutés en LL-5008 : lien vers la
 * {@code Source} d'origine (voir {@code source} module), obligatoire pour
 * toute activité — y compris les créations manuelles, rattachées à la
 * source réservée {@code MANUAL} (voir {@code SOURCE_CONTRACT.md} et
 * {@code ActivityService#createActivity}). {@code importKey} est la clé
 * de déduplication calculée par {@code DeduplicationService} (LL-5007) ;
 * {@code null} pour les activités créées manuellement (aucune donnée
 * collectée à déduplicer), non nul pour les activités importées, unique
 * par {@code sourceId} (voir {@code V9__link_activity_to_source.sql}).
 *
 * {@code url} ajouté en LL-6002 (Sprint 6, audit LL-6001) : reprend
 * {@code CollectedActivity#sourceUrl}, jusqu'ici collecté par
 * {@code OpenAgendaCollector} puis perdu à la normalisation faute de
 * champ pour le porter — voir {@code DATA_QUALITY_AUDIT.md}. Toujours
 * {@code null} pour une activité créée manuellement (le formulaire de
 * contribution ne demande pas d'URL, hors périmètre de ce ticket).
 *
 * {@code status} formalisé en LL-6003 (Sprint 6) : trois valeurs MVP,
 * imposées depuis la migration {@code V11__enforce_activity_status.sql}
 * par une contrainte {@code CHECK} en base (défaut applicatif déjà en
 * place avant cette contrainte, voir {@code ActivityService#createActivity}
 * et {@code NormalizationService}) —
 * <ul>
 *   <li>{@code PENDING} : valeur par défaut d'une contribution manuelle
 *       (en attente de modération) ;</li>
 *   <li>{@code PUBLISHED} : valeur par défaut d'une activité importée
 *       (source jugée fiable, voir {@code NormalizationService}), et
 *       seule valeur qu'une activité {@code PENDING} peut atteindre après
 *       validation par un administrateur ;</li>
 *   <li>{@code REJECTED} : atteinte depuis {@code PENDING} après rejet
 *       par un administrateur.</li>
 * </ul>
 * Transitions volontairement minimales (pas de machine à états
 * complexe, critère d'acceptation explicite de LL-6003) : seules
 * {@code PENDING → PUBLISHED} et {@code PENDING → REJECTED} sont
 * prévues ; aucun retour en arrière, aucune transition depuis
 * {@code PUBLISHED} ou {@code REJECTED}. Aucun endpoint ne permet
 * encore de déclencher ces transitions à ce stade (prévu en LL-6006) ;
 * ce ticket ne fait que définir et persister les valeurs possibles.
 * Rien ne change encore côté visibilité publique : l'exclusion des
 * activités non {@code PUBLISHED} des recherches publiques est le
 * périmètre de LL-6004, pas de celui-ci.
 *
 * {@code address}/{@code city}/{@code postalCode} ajoutés en LL-EF-008
 * (voir {@code docs/02_Architecture/ADR-0001-adresse-structuree.md}) :
 * jusqu'ici seules {@code latitude}/{@code longitude} étaient stockées,
 * ce qui ne permettait ni d'afficher une adresse lisible, ni de regrouper
 * les activités par ville (besoin de la vue liste LL-EF-008). Résolus une
 * seule fois, à l'écriture :
 * <ul>
 *   <li>contribution manuelle ({@code ActivityService#createActivity}) :
 *       {@code address} est le texte saisi par le contributeur (déjà
 *       validé — il a servi au géocodage LL-3012) ; {@code city}/
 *       {@code postalCode} proviennent de la même réponse Nominatim
 *       (champ {@code addressdetails}), sans appel réseau
 *       supplémentaire ;</li>
 *   <li>import ({@code NormalizationService}) : les trois champs
 *       proviennent directement de {@code CollectedActivity}, elle-même
 *       alimentée par l'objet {@code location} déjà renvoyé par l'API du
 *       collecteur (ex. OpenAgenda) — même principe que {@code url}
 *       (LL-6002) : une donnée déjà présente dans la réponse de la
 *       source, reprise plutôt que perdue.</li>
 * </ul>
 * Les trois champs sont nullables (activités existantes non re-géocodées/
 * ré-importées, ou source ne fournissant pas cette donnée) : aucune
 * activité ne doit être rejetée pour leur absence, voir
 * {@code NormalizationService#isValid}, qui ne les valide pas.
 *
 * {@code longDescription}/{@code conditions}/{@code ageMin}/{@code ageMax}
 * ajoutés en LL-11006 (Sprint 11, section 9 — « fiche événementielle
 * riche ») : permettent de construire une fiche complète, au-delà du
 * strict nécessaire pour l'affichage sur la carte/liste (déjà couvert par
 * {@code description}, LL-8006/LL-EF-008). Conception volontairement
 * générique (texte libre, sans structure ni notion de langue) plutôt que
 * calquée sur le schéma OpenAgenda — critère d'acceptation explicite
 * « aucune dépendance au modèle OpenAgenda dans le domaine » : OpenAgenda
 * expose par exemple {@code longDescription}/{@code conditions} comme des
 * champs multilingues ({@code {fr: ..., en: ...}}) et {@code age} comme un
 * objet {@code {min, max}} (voir {@code OpenAgendaCollector}) — c'est au
 * collecteur de réduire cela à la forme générique attendue ici (une seule
 * langue retenue, comme {@code description}/{@code title} le font déjà),
 * pas au domaine de connaître cette structure.
 * <ul>
 *   <li>{@code longDescription} : description détaillée, distincte de
 *       {@code description} (qui reste le résumé court) — même
 *       distinction que sur OpenAgenda ({@code description} ≤ ~200
 *       caractères, {@code longDescription} jusqu'à 10000) ;</li>
 *   <li>{@code conditions} : conditions de participation (tarifs,
 *       gratuité, inscription requise...) ;</li>
 *   <li>{@code ageMin}/{@code ageMax} : tranche d'âge ciblée, chacun
 *       indépendamment nullable (une activité peut n'avoir qu'un
 *       minimum, ex. « interdit aux moins de 18 ans » sans maximum, voir
 *       la documentation OpenAgenda citée dans {@code OpenAgendaCollector}).</li>
 * </ul>
 * Les quatre champs nullables (contribution manuelle : jamais renseignés,
 * le formulaire ne les demande pas encore ; import : présents seulement
 * si la source les fournit) — critère d'acceptation « conservées lorsqu'
 * elles existent », pas « toujours requises ».
 */
public record Activity(
        @Id Long id,
        String title,
        String description,
        String category,
        double latitude,
        double longitude,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status,
        Long sourceId,
        String importKey,
        String url,
        String address,
        String city,
        String postalCode,
        String longDescription,
        String conditions,
        Integer ageMin,
        Integer ageMax) {
}
