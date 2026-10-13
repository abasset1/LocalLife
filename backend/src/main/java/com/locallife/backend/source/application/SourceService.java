package com.locallife.backend.source.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
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

    /**
     * Source collectée depuis l'export CSV DATAtourisme de data.gouv.fr ({@code DataTourismeCollector}).
     * Sa configuration est validée ({@link #validateDataTourismeConfiguration}) et elle n'est jamais active
     * sans vérification : créée {@code INACTIVE}, elle ne passe à {@code ACTIVE} que par
     * {@link #activateAfterVerification}, après un essai à blanc réussi ({@code SourceActivationService},
     * module {@code collector}).
     */
    public static final String DATATOURISME_TYPE = "DATATOURISME";

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INACTIVE_STATUS = "INACTIVE";

    /** URL stable d'une ressource data.gouv.fr, qui redirige vers le fichier du jour. */
    private static final Pattern DATA_GOUV_RESOURCE_URL = Pattern.compile(
            "https://www\\.data\\.gouv\\.fr/api/1/datasets/r/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Département, sous forme de préfixe de code postal : 01 à 95 (Corse : 20), 971 à 976. */
    private static final Pattern DEPARTMENT_POSTAL_PREFIX = Pattern.compile("0[1-9]|[1-8][0-9]|9[0-5]|97[1-6]");

    private final SourceRepository sourceRepository;
    private final ActivityRepository activityRepository;

    public SourceService(SourceRepository sourceRepository, ActivityRepository activityRepository) {
        this.sourceRepository = sourceRepository;
        this.activityRepository = activityRepository;
    }

    /**
     * Crée une source active (sauf {@code DATATOURISME}, créée inactive :
     * voir {@link #DATATOURISME_TYPE}). {@code lastSyncAt} démarre à {@code null}
     * (aucun import n'a encore eu lieu), conformément à
     * {@code SOURCE_CONTRACT.md}. {@code agendaUid}/{@code regionFilter}
     * (LL-EF-005) ne sont significatifs que pour une source de type
     * {@code API} destinée à la collecte OpenAgenda — {@code null}
     * accepté pour les autres types.
     */
    public Source createSource(String name, String type, String url, String agendaUid, String regionFilter) {
        validate(name, type);
        boolean dataTourisme = DATATOURISME_TYPE.equals(type);
        if (dataTourisme) {
            validateDataTourismeConfiguration(url, agendaUid, regionFilter);
        }
        // Une source DATATOURISME n'est jamais créée active : voir DATATOURISME_TYPE.
        String status = dataTourisme ? INACTIVE_STATUS : ACTIVE_STATUS;
        Source source = new Source(null, name, type, url, status, null, agendaUid, regionFilter);
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
            if (DATATOURISME_TYPE.equals(type)) {
                validateDataTourismeConfiguration(url, agendaUid, regionFilter);
                requireVerifiedActivation(existing, type, url, status, regionFilter);
            }
            Source updated = new Source(id, name, type, url, status, existing.lastSyncAt(), agendaUid, regionFilter);
            return sourceRepository.save(updated);
        });
    }

    /**
     * Configuration d'une source {@code DATATOURISME} : URL stable d'une ressource data.gouv.fr
     * ({@code https://www.data.gouv.fr/api/1/datasets/r/{identifiant}}), filtre vide ou liste de départements
     * séparés par des virgules (préfixes de code postal : {@code 84}, {@code 13}, {@code 20} pour la Corse,
     * {@code 971}…), pas d'identifiant d'agenda.
     *
     * @throws IllegalArgumentException avec la raison, si la configuration est invalide
     */
    public static void validateDataTourismeConfiguration(String url, String agendaUid, String regionFilter) {
        if (url == null || !DATA_GOUV_RESOURCE_URL.matcher(url.trim()).matches()) {
            throw new IllegalArgumentException("URL DATAtourisme invalide : attendu "
                    + "https://www.data.gouv.fr/api/1/datasets/r/{identifiant de ressource}.");
        }
        if (agendaUid != null && !agendaUid.isBlank()) {
            throw new IllegalArgumentException("Une source DATATOURISME n'a pas d'identifiant d'agenda OpenAgenda.");
        }
        if (regionFilter != null && !regionFilter.isBlank()) {
            for (String department : regionFilter.split(",")) {
                if (!DEPARTMENT_POSTAL_PREFIX.matcher(department.trim()).matches()) {
                    throw new IllegalArgumentException("Filtre DATAtourisme invalide : '" + department.trim()
                            + "' n'est pas un département (ex. 84, 13, 20 pour la Corse, 971).");
                }
            }
        }
    }

    /**
     * Une modification ne peut pas rendre active une source {@code DATATOURISME} non vérifiée : ni la passer de
     * inactive à active, ni changer son URL, son type ou son filtre en la laissant active (la configuration
     * vérifiée ne serait plus celle collectée). L'activation passe par {@link #activateAfterVerification}.
     */
    private static void requireVerifiedActivation(
            Source existing, String type, String url, String status, String regionFilter) {
        if (!ACTIVE_STATUS.equals(status)) {
            return;
        }
        boolean alreadyActive = ACTIVE_STATUS.equals(existing.status());
        boolean sameConfiguration = type.equals(existing.type())
                && sameText(url, existing.url())
                && sameText(regionFilter, existing.regionFilter());
        if (!alreadyActive || !sameConfiguration) {
            throw new IllegalArgumentException("Une source DATATOURISME s'active après un essai à blanc réussi "
                    + "(activation vérifiée depuis l'administration des imports). Enregistrez-la inactive.");
        }
    }

    private static boolean sameText(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    /**
     * Passe une source à {@code ACTIVE} sans autre contrôle. Réservé à {@code SourceActivationService}
     * (module {@code collector}), qui ne l'appelle qu'après un essai à blanc réussi : c'est le seul chemin
     * d'activation d'une source {@code DATATOURISME}.
     *
     * @return {@link Optional#empty()} si aucune source ne correspond à {@code id}
     */
    public Optional<Source> activateAfterVerification(Long id) {
        return sourceRepository.findById(id).map(existing -> sourceRepository.save(new Source(
                id, existing.name(), existing.type(), existing.url(), ACTIVE_STATUS, existing.lastSyncAt(),
                existing.agendaUid(), existing.regionFilter())));
    }

    /**
     * Enregistre la date/heure du dernier import réussi (bug corrigé :
     * {@code lastSyncAt} restait toujours {@code null} en base, alors que
     * {@code SOURCE_CONTRACT.md} et la javadoc de {@link #updateSource}
     * le décrivaient déjà comme renseigné par {@code ImportService} — ce
     * dernier calculait bien {@code endedAt} pour chaque import mais ne
     * le reportait jamais sur la {@code Source}). Appelée uniquement par
     * {@code ImportService}, après un import qui a effectivement pu
     * contacter la source (voir sa javadoc pour le cas d'un échec total
     * de collecte, qui n'appelle pas cette méthode).
     *
     * <p>Ignore silencieusement un {@code sourceId} inconnu plutôt que de
     * lever une exception : ne devrait pas se produire en usage normal
     * (la source vient d'être lue par {@code ImportService} au moment de
     * l'appel), et une source supprimée entre-temps ne doit pas faire
     * échouer tout l'import pour une raison accessoire.</p>
     */
    public void recordSync(Long sourceId, LocalDateTime syncedAt) {
        sourceRepository.findById(sourceId).ifPresent(existing -> {
            Source updated = new Source(
                    sourceId, existing.name(), existing.type(), existing.url(), existing.status(), syncedAt,
                    existing.agendaUid(), existing.regionFilter());
            sourceRepository.save(updated);
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
                activity.postalCode(),
                activity.longDescription(),
                activity.conditions(),
                activity.ageMin(),
                activity.ageMax(),
                activity.createdBy());
    }

    /**
     * @throws IllegalArgumentException si {@code name}/{@code type} est
     *     absent, ou si {@code type} vaut {@code MANUAL} — réservé à la
     *     source unique créée par {@code V8__create_source_table.sql},
     *     jamais attribuable via cette API (LL-11016 : cette validation
     *     manquait, ce qui permettait de créer/transformer n'importe
     *     quelle source en une seconde source {@code MANUAL}, cassant
     *     l'hypothèse d'unicité que {@code SourceRepository#findByType}
     *     fait partout ailleurs dans le projet — {@code
     *     IncorrectResultSizeDataAccessException} dès qu'une deuxième
     *     ligne apparaît. Voir {@code V30__enforce_single_manual_source.sql}
     *     pour la contrainte correspondante côté base, en défense
     *     supplémentaire).
     */
    private void validate(String name, String type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la source est obligatoire.");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Le type de la source est obligatoire.");
        }
        if (RESERVED_MANUAL_TYPE.equalsIgnoreCase(type)) {
            throw new IllegalArgumentException(
                    "Le type 'MANUAL' est réservé à la source de saisie manuelle et ne peut pas être attribué "
                            + "via cette API.");
        }
    }

}
