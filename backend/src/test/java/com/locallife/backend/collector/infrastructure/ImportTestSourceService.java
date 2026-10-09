package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SourceService} des tests du pipeline d'import ({@link SingleMockCollectorConfig}) : restreint
 * {@link #getAllSources()} aux sources créées par le test lui-même, via
 * {@link #createCollectibleTestSource(String)}.
 *
 * <p>Sans cette restriction, {@code ImportService#importAll()} parcourait <b>toutes</b> les sources
 * collectibles de la base — y compris les agendas réels (seedés par {@code V14} ou ajoutés depuis
 * l'administration) — avec le collecteur mocké du test : les éléments factices du test y étaient importés
 * et toutes leurs vraies activités, absentes de cette « collecte », étaient archivées. Dans un test non
 * transactionnel ({@code AdminImportControllerIntegrationTest}), ces écritures étaient réellement validées
 * dans la base de développement.
 *
 * <p>Limite assumée : dans les contextes qui importent {@link SingleMockCollectorConfig},
 * {@code getAllSources()} ne reflète donc plus le contenu complet de la table {@code source} pour les
 * autres appelants ({@code GET /api/v1/sources}, noms de source des listes d'activités) — sans objet pour
 * ces tests, consacrés au pipeline d'import.
 */
public class ImportTestSourceService extends SourceService {

    private final SourceRepository sourceRepository;

    /** Noms uniques (UUID) : un nom resté ici après la fin d'un test ne correspond plus à aucune source. */
    private final Set<String> testSourceNames = ConcurrentHashMap.newKeySet();

    public ImportTestSourceService(SourceRepository sourceRepository, ActivityRepository activityRepository) {
        super(sourceRepository, activityRepository);
        this.sourceRepository = sourceRepository;
    }

    /**
     * Persiste une source collectible (type {@code API}, statut {@code ACTIVE}, {@code agendaUid} non
     * vide) et l'ouvre à {@code ImportService#importAll()} — la seule que l'import du test parcourra.
     */
    public Source createCollectibleTestSource(String name) {
        testSourceNames.add(name);
        return sourceRepository.save(
                new Source(null, name, "API", null, "ACTIVE", null, "agenda-uid-" + UUID.randomUUID(), null));
    }

    @Override
    public List<Source> getAllSources() {
        return super.getAllSources().stream()
                .filter(source -> testSourceNames.contains(source.name()))
                .toList();
    }
}
