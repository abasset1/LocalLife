package com.locallife.backend.source.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Source — CRUD complet depuis LL-EF-005 (gestion des agendas
 * depuis l'interface d'administration). Dépend de
 * {@link ActivityRepository}, alors que ce service appartient au module
 * {@code source} : dépendance assumée, justifiée par
 * {@link #deleteSource(Long)} ci-dessous (détachement des activités liées
 * à une source supprimée), sans introduire de cycle (le module
 * {@code activity} ne dépend pas de {@code source.application}).
 */
@Service
public class SourceService {

    /**
     * Type de la source réservée aux contributions manuelles (LL-5001,
     * migration {@code V8__create_source_table.sql}) — jamais supprimable
     * ({@link #deleteSource(Long)}), et cible de repli pour les activités
     * détachées d'une source supprimée.
     */
    private static final String RESERVED_MANUAL_TYPE = "MANUAL";

    private final SourceRepository sourceRepository;
    private final ActivityRepository activityRepository;

    public SourceService(SourceRepository sourceRepository, ActivityRepository activityRepository) {
        this.sourceRepository = sourceRepository;
        this.activityRepository = activityRepository;
    }

    /**
     * Crée une source active. {@code lastSyncAt} démarre à {@code null}
     * (aucun import n'a encore eu lieu), conformément à
     * {@code SOURCE_CONTRACT.md}. {@code agendaUid}/{@code regionFilter}
     * (LL-EF-005) ne sont significatifs que pour une source de type
     * {@code API} destinée à la collecte OpenAgenda — {@code null}
     * accepté pour les autres types.
     */
    public Source createSource(String name, String type, String url, String agendaUid, String regionFilter) {
        validate(name, type);
        Source source = new Source(null, name, type, url, "ACTIVE", null, agendaUid, regionFilter);
        return sourceRepository.save(source);
    }

    public List<Source> getAllSources() {
        return sourceRepository.findAll();
    }

    public Optional<Source> getSourceById(Long id) {
        return sourceRepository.findById(id);
    }

    /**
     * Recherche la source réservée par type (LL-5008) — utilisée pour
     * retrouver la source {@code MANUAL} sans dépendre de son libellé
     * exact (voir {@code SourceRepository#findByType}).
     */
    public Optional<Source> findByType(String type) {
        return sourceRepository.findByType(type);
    }

    /**
     * Met à jour une source existante (LL-EF-005). {@code lastSyncAt}
     * n'est pas modifiable depuis l'interface d'administration : c'est le
     * pipeline d'import ({@code ImportService}) qui le renseigne, pas une
     * saisie manuelle — la valeur existante est donc préservée.
     *
     * @return {@link Optional#empty()} si aucune source ne correspond à
     *     {@code id} (404 côté contrôleur).
     */
    public Optional<Source> updateSource(
            Long id, String name, String type, String url, String status, String agendaUid, String regionFilter) {
        validate(name, type);
        return sourceRepository.findById(id).map(existing -> {
            Source updated = new Source(id, name, type, url, status, existing.lastSyncAt(), agendaUid, regionFilter);
            return sourceRepository.save(updated);
        });
    }

    /**
     * Supprime une source (LL-EF-005, décision Alex : suppression
     * autorisée même si des activités y sont encore rattachées, plutôt
     * que de la bloquer — voir {@code SPRINT_EVOL_FIX.md}).
     *
     * <p>Deux garanties : (1) la source réservée {@code MANUAL} ne peut
     * jamais être supprimée (invariant du système, pas une règle du
     * ticket — la retirer casserait la création manuelle d'activités et
     * le détachement décrit au point 2) ; (2) avant la suppression
     * effective, toute activité encore rattachée à cette source est
     * détachée en réassignant son {@code sourceId} vers la source
     * réservée {@code MANUAL} — aucune activité ne se retrouve avec une
     * référence à une source qui n'existe plus.</p>
     *
     * <p>⚠️ Limite connue, acceptée pour ce ticket : si un agenda est
     * supprimé puis qu'une source équivalente est recréée plus tard avec
     * le même {@code agendaUid}, les activités déjà détachées vers
     * {@code MANUAL} ne seront pas rattachées à la nouvelle source — un
     * nouvel import les recréera en doublon plutôt que de les mettre à
     * jour ({@code ImportService} cherche par {@code sourceId} +
     * {@code importKey}, et le {@code sourceId} de la nouvelle source
     * diffère de l'ancienne). Cas limite jugé acceptable : la suppression
     * d'un agenda n'est pas censée être suivie d'une recréation à
     * l'identique.</p>
     *
     * @return {@link Optional#empty()} si aucune source ne correspond à
     *     {@code id} (404 côté contrôleur) ; lève
     *     {@link IllegalArgumentException} (400 côté contrôleur) si
     *     {@code id} désigne la source réservée {@code MANUAL}.
     */
    public Optional<Source> deleteSource(Long id) {
        return sourceRepository.findById(id).map(source -> {
            if (RESERVED_MANUAL_TYPE.equals(source.type())) {
                throw new IllegalArgumentException(
                        "La source réservée 'MANUAL' ne peut pas être supprimée.");
            }
            detachActivitiesToManualSource(id);
            sourceRepository.deleteById(id);
            return source;
        });
    }

    private void detachActivitiesToManualSource(Long deletedSourceId) {
        Source manualSource = sourceRepository.findByType(RESERVED_MANUAL_TYPE)
                .orElseThrow(() -> new IllegalStateException(
                        "Source réservée 'MANUAL' introuvable (voir V8__create_source_table.sql)."));
        for (Activity activity : activityRepository.findBySourceId(deletedSourceId)) {
            activityRepository.save(withSourceId(activity, manualSource.id()));
        }
    }

    private Activity withSourceId(Activity activity, Long sourceId) {
        return new Activity(
                activity.id(),
                activity.title(),
                activity.description(),
                activity.category(),
                activity.latitude(),
                activity.longitude(),
                activity.startDate(),
                activity.endDate(),
                activity.status(),
                sourceId,
                activity.importKey(),
                activity.url(),
                activity.address(),
                activity.city(),
                activity.postalCode());
    }

    private void validate(String name, String type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la source est obligatoire.");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Le type de la source est obligatoire.");
        }
    }

}
