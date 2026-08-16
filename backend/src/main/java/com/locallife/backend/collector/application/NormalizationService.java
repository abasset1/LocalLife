package com.locallife.backend.collector.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.collector.domain.CollectedActivity;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Pipeline de normalisation (LL-5005, validation renforcée en LL-6002) :
 * {@code CollectedActivity} → validation → {@code Activity}.
 * Volontairement séparé d'
 * {@link com.locallife.backend.activity.application.ActivityService}
 * (critère d'acceptation LL-5005 : « aucune logique spécifique à un
 * collecteur dans ActivityService ») — ce service ne fait que convertir
 * en mémoire, il ne persiste rien (la persistance via les services
 * métier existants est le périmètre de LL-5008).
 *
 * Validation cohérente avec ce que le reste du projet impose déjà à une
 * {@code Activity} (bornes de {@code latitude}/{@code longitude}
 * identiques à celles d'
 * {@link com.locallife.backend.activity.application.ActivityService},
 * dupliquées ici plutôt que factorisées — refactoriser
 * {@code ActivityService} n'est pas demandé par ce ticket) :
 *
 * <ul>
 *   <li>{@code title} non vide, longueur ≤ 255 caractères (alignée sur
 *       la colonne {@code activity.title}) ;</li>
 *   <li>{@code startDate} non nul ;</li>
 *   <li>{@code latitude} entre -90 et 90 ;</li>
 *   <li>{@code longitude} entre -180 et 180 ;</li>
 *   <li>{@code endDate}, si renseignée, non antérieure à
 *       {@code startDate} (LL-6002, problème n°3 de
 *       {@code DATA_QUALITY_AUDIT.md}) ;</li>
 *   <li>{@code category}, si renseignée (non nulle), non vide/blanche
 *       après {@code trim()} — {@code SPRINT_6.md} demande une
 *       « catégorie valide si renseignée » ; en l'absence de liste de
 *       référence ({@code category} reste un champ libre, voir
 *       LL-4004/{@code DATA_QUALITY_AUDIT.md}), c'est l'interprétation
 *       minimale retenue ici, à confirmer avec Alex si une notion plus
 *       stricte de validité est souhaitée ;</li>
 *   <li>{@code sourceUrl}, si renseignée, doit être une URL absolue
 *       {@code http}/{@code https} syntaxiquement valide (LL-6002,
 *       problème n°8 de {@code DATA_QUALITY_AUDIT.md} : le champ
 *       existe désormais sur {@code Activity} et n'est plus perdu à la
 *       normalisation).</li>
 * </ul>
 *
 * {@code description} et {@code endDate} restent optionnels, comme sur
 * {@code Activity} elle-même.
 */
@Service
public class NormalizationService {

    /** Alignée sur la colonne {@code activity.title} (V2__create_activity_table.sql). */
    private static final int MAX_TITLE_LENGTH = 255;

    /**
     * Convertit une donnée collectée en {@code Activity}, ou la rejette si
     * elle ne respecte pas les règles de validation ci-dessus.
     */
    public Optional<Activity> normalize(CollectedActivity collected) {
        if (!isValid(collected)) {
            return Optional.empty();
        }
        Activity activity = new Activity(
                null,
                collected.title(),
                collected.description(),
                collected.category(),
                collected.latitude(),
                collected.longitude(),
                collected.startDate(),
                collected.endDate(),
                // ⚠️ Décision à valider : PUBLISHED plutôt que PENDING (statut
                // par défaut des contributions manuelles, voir
                // ActivityService#createActivity). Une source réelle est
                // choisie pour sa fiabilité (critères LL-5006 : stabilité,
                // qualité des données) et n'a pas besoin de la même
                // modération qu'une contribution anonyme ; sans quoi les
                // activités importées n'apparaîtraient jamais sur la carte,
                // faute d'interface de modération dans ce sprint. Valeur
                // désormais l'une des trois formalisées en LL-6003 (voir
                // Activity#status et V11__enforce_activity_status.sql).
                "PUBLISHED",
                // sourceId/importKey laissés à null ici : ce service ne connaît
                // pas la persistance (ni la Source résolue, ni la clé de
                // déduplication). Remplis par ImportService (LL-5008) juste
                // avant la sauvegarde — voir sa javadoc.
                null,
                null,
                // url (LL-6002) : reprise directe de sourceUrl, désormais
                // conservée jusqu'en base au lieu d'être perdue ici.
                collected.sourceUrl());
        return Optional.of(activity);
    }

    private boolean isValid(CollectedActivity collected) {
        return hasValidTitle(collected.title())
                && collected.startDate() != null
                && isValidLatitude(collected.latitude())
                && isValidLongitude(collected.longitude())
                && isValidDateRange(collected.startDate(), collected.endDate())
                && isValidCategory(collected.category())
                && isValidUrl(collected.sourceUrl());
    }

    private boolean hasValidTitle(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_TITLE_LENGTH;
    }

    private boolean isValidLatitude(double latitude) {
        return latitude >= -90 && latitude <= 90;
    }

    private boolean isValidLongitude(double longitude) {
        return longitude >= -180 && longitude <= 180;
    }

    private boolean isValidDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return endDate == null || !endDate.isBefore(startDate);
    }

    private boolean isValidCategory(String category) {
        return category == null || !category.isBlank();
    }

    private boolean isValidUrl(String url) {
        if (url == null) {
            return true;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

}
