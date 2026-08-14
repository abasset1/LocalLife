package com.locallife.backend.collector.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.collector.domain.CollectedActivity;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Pipeline de normalisation (LL-5005) : {@code CollectedActivity} →
 * validation → {@code Activity}. Volontairement séparé d'
 * {@link com.locallife.backend.activity.application.ActivityService}
 * (critère d'acceptation LL-5005 : « aucune logique spécifique à un
 * collecteur dans ActivityService ») — ce service ne fait que convertir
 * en mémoire, il ne persiste rien (la persistance via les services
 * métier existants est le périmètre de LL-5008).
 *
 * Validation minimale, cohérente avec ce que le reste du projet impose
 * déjà à une {@code Activity} (bornes de {@code latitude}/{@code
 * longitude} identiques à celles d'
 * {@link com.locallife.backend.activity.application.ActivityService},
 * dupliquées ici plutôt que factorisées — refactoriser
 * {@code ActivityService} n'est pas demandé par ce ticket) :
 *
 * <ul>
 *   <li>{@code title} non vide ;</li>
 *   <li>{@code startDate} non nul ;</li>
 *   <li>{@code latitude} entre -90 et 90 ;</li>
 *   <li>{@code longitude} entre -180 et 180.</li>
 * </ul>
 *
 * {@code description}, {@code endDate} et {@code category} restent
 * optionnels, comme sur {@code Activity} elle-même.
 */
@Service
public class NormalizationService {

    /**
     * Convertit une donnée collectée en {@code Activity}, ou la rejette si
     * elle ne respecte pas les règles de validation minimales ci-dessus.
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
                // faute d'interface de modération dans ce sprint.
                "PUBLISHED");
        return Optional.of(activity);
    }

    private boolean isValid(CollectedActivity collected) {
        return hasText(collected.title())
                && collected.startDate() != null
                && isValidLatitude(collected.latitude())
                && isValidLongitude(collected.longitude());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isValidLatitude(double latitude) {
        return latitude >= -90 && latitude <= 90;
    }

    private boolean isValidLongitude(double longitude) {
        return longitude >= -180 && longitude <= 180;
    }

}
