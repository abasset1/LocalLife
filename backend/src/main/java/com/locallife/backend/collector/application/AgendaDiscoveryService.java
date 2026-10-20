package com.locallife.backend.collector.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.collector.domain.DiscoveredAgenda;
import com.locallife.backend.collector.domain.DiscoveryScope;
import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.OpenAgendaAgendaDirectory;
import com.locallife.backend.collector.infrastructure.OpenAgendaAgendaDirectory.AgendaPage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Découverte des agendas OpenAgenda (sprint « agendas exhaustifs », AGD-02) : parcourt l'annuaire page par
 * page jusqu'au marqueur de fin du fournisseur et alimente le <b>catalogue</b> des agendas découverts
 * ({@code discovered_agenda}). Elle ne crée <b>aucune</b> source : découvrir tout l'annuaire ne déclenche donc
 * aucune collecte. La création de sources pour les agendas choisis par un administrateur relève
 * d'{@link AgendaActivationService}.
 *
 * <p>Parcours par lots et reprenable : un appel traite au plus {@code maxPages} pages, puis s'arrête en
 * laissant le parcours {@code IN_PROGRESS} ; l'appel suivant reprend au curseur enregistré. Chaque page est
 * enregistrée (agendas, compteurs, curseur) dans une seule transaction : une interruption, quelle qu'elle soit,
 * laisse le curseur de la dernière page entièrement enregistrée, et la reprise ne duplique rien (le catalogue
 * est unique par agenda).
 *
 * <p>Arrêts et statuts :
 * <ul>
 *   <li>fin de l'annuaire (page vide, curseur absent ou vide) : {@code COMPLETE}, sauf si le nombre d'agendas
 *       examinés est inférieur au {@code total} annoncé par l'API, auquel cas {@code FAILED} ;</li>
 *   <li>lot de pages atteint : {@code IN_PROGRESS}, reprenable ;</li>
 *   <li>échec d'appel après les reprises bornées (quota, 5xx, réseau) : {@code IN_PROGRESS}, reprenable depuis la
 *       dernière page enregistrée, erreur comptée ;</li>
 *   <li>curseur répété (dans le lot, ou égal au dernier curseur enregistré), réponse sans tableau
 *       {@code agendas}, ou plus du double des agendas annoncés examinés (cycle plus long qu'un lot) :
 *       {@code FAILED}, non reprenable : un nouveau parcours repart du début ({@code restart}).</li>
 * </ul>
 * Seul {@code COMPLETE} signifie que l'annuaire a été lu en entier.
 */
@Service
public class AgendaDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgendaDiscoveryService.class);

    /** Pages par appel par défaut (100 agendas par page). */
    public static final int DEFAULT_PAGES_PER_BATCH = 20;

    /** Plafond explicite d'un lot demandé : un appel HTTP d'administration ne doit pas durer indéfiniment. */
    public static final int MAX_PAGES_PER_BATCH = 200;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenAgendaAgendaDirectory agendaDirectory;
    private final AgendaDiscoveryRepository repository;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final int defaultPagesPerBatch;

    /** Un seul parcours à la fois dans cette instance ; l'index unique V68 couvre le cas multi-instance. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public AgendaDiscoveryService(
            OpenAgendaAgendaDirectory agendaDirectory,
            AgendaDiscoveryRepository repository,
            TransactionOperations transactions,
            @Value("${locallife.discovery.pages-per-batch:" + DEFAULT_PAGES_PER_BATCH + "}") int defaultPagesPerBatch) {
        this(agendaDirectory, repository, transactions, Clock.systemDefaultZone(), defaultPagesPerBatch);
    }

    AgendaDiscoveryService(
            OpenAgendaAgendaDirectory agendaDirectory,
            AgendaDiscoveryRepository repository,
            TransactionOperations transactions,
            Clock clock,
            int defaultPagesPerBatch) {
        if (defaultPagesPerBatch < 1 || defaultPagesPerBatch > MAX_PAGES_PER_BATCH) {
            throw new IllegalArgumentException("locallife.discovery.pages-per-batch doit être entre 1 et "
                    + MAX_PAGES_PER_BATCH + ".");
        }
        this.agendaDirectory = agendaDirectory;
        this.repository = repository;
        this.transactions = transactions;
        this.clock = clock;
        this.defaultPagesPerBatch = defaultPagesPerBatch;
    }

    public int defaultPagesPerBatch() {
        return defaultPagesPerBatch;
    }

    /**
     * Traite un lot de pages de l'annuaire : reprend le parcours en cours de ce périmètre, ou en commence un.
     *
     * @param maxPages pages au plus pour ce lot ({@code null} : valeur configurée), entre 1 et
     *                 {@value #MAX_PAGES_PER_BATCH}
     * @param restart  abandonne le parcours en cours de ce périmètre et repart du début de l'annuaire
     * @throws IllegalArgumentException      si {@code maxPages} est hors bornes
     * @throws CollectorException            si la clé API n'est pas configurée (aucun parcours n'est créé)
     * @throws DiscoveryAlreadyRunningException si un lot est déjà en cours
     */
    public AgendaDiscoveryResult discover(DiscoveryScope scope, Integer maxPages, boolean restart) {
        int budget = maxPages == null ? defaultPagesPerBatch : maxPages;
        if (budget < 1 || budget > MAX_PAGES_PER_BATCH) {
            throw new IllegalArgumentException(
                    "Le paramètre 'maxPages' doit être compris entre 1 et " + MAX_PAGES_PER_BATCH + ".");
        }
        agendaDirectory.requireApiKey();
        if (!running.compareAndSet(false, true)) {
            throw new DiscoveryAlreadyRunningException();
        }
        try {
            return runBatch(scope, budget, restart);
        } finally {
            running.set(false);
        }
    }

    /** Dernier parcours de ce périmètre, pour afficher son état (reprenable ou non) sans rien lancer. */
    public Optional<AgendaDiscoveryResult> latest(DiscoveryScope scope) {
        return repository.findLatest(scope).map(run -> AgendaDiscoveryResult.of(run, 0));
    }

    private AgendaDiscoveryResult runBatch(DiscoveryScope scope, int budget, boolean restart) {
        Optional<AgendaDiscoveryRun> inProgress = repository.findInProgress(scope);
        if (restart && inProgress.isPresent()) {
            AgendaDiscoveryRun abandoned = inProgress.get();
            repository.save(withStatus(abandoned, AgendaDiscoveryRun.Status.ABANDONED,
                    "Abandonné : nouveau parcours demandé par un administrateur.", now(), now()));
            inProgress = Optional.empty();
        }
        AgendaDiscoveryRun run = inProgress.orElseGet(() -> repository.create(scope, now()));

        Set<String> seenCursors = new HashSet<>();
        if (run.cursor() != null) {
            seenCursors.add(run.cursor());
        }
        int pagesThisBatch = 0;
        while (run.status() == AgendaDiscoveryRun.Status.IN_PROGRESS && pagesThisBatch < budget) {
            List<Object> after = parseCursor(run.cursor());
            AgendaPage page;
            try {
                page = agendaDirectory.fetchPage(scope, after);
            } catch (CollectorException exception) {
                run = copy(run, AgendaDiscoveryRun.Status.IN_PROGRESS, run.cursor(), run.pagesRead(), run.examined(),
                        run.newCount(), run.alreadyKnown(), run.ignored(), run.errors() + 1, run.providerTotal(),
                        exception.getMessage() + " Parcours incomplet, reprenable depuis la page "
                                + (run.pagesRead() + 1) + ".", null);
                repository.save(run);
                LOGGER.warn("Découverte des agendas ({}) interrompue à la page {} : {}", scope, run.pagesRead() + 1,
                        exception.getMessage());
                return AgendaDiscoveryResult.of(run, pagesThisBatch);
            }
            run = processPage(run, page, seenCursors);
            pagesThisBatch++;
        }
        if (run.status() == AgendaDiscoveryRun.Status.IN_PROGRESS) {
            run = copy(run, run.status(), run.cursor(), run.pagesRead(), run.examined(), run.newCount(),
                    run.alreadyKnown(), run.ignored(), run.errors(), run.providerTotal(),
                    "Lot de " + budget + " page(s) traité ; l'annuaire continue : relancer pour poursuivre.", null);
            repository.save(run);
        }
        LOGGER.info("Découverte des agendas ({}) : parcours {} {}, {} page(s) ce lot, {} examinés, {} nouveaux, "
                + "{} déjà connus, {} ignorés, {} erreurs", scope, run.id(), run.status(), pagesThisBatch,
                run.examined(), run.newCount(), run.alreadyKnown(), run.ignored(), run.errors());
        return AgendaDiscoveryResult.of(run, pagesThisBatch);
    }

    /** Une page, enregistrée avec le nouvel état du parcours dans une seule transaction. */
    private AgendaDiscoveryRun processPage(AgendaDiscoveryRun run, AgendaPage page, Set<String> seenCursors) {
        LocalDateTime now = now();
        int pageNumber = run.pagesRead() + 1;
        if (page.agendas() == null) {
            AgendaDiscoveryRun failed = withStatus(run, AgendaDiscoveryRun.Status.FAILED,
                    "Réponse incohérente d'OpenAgenda à la page " + pageNumber + " (aucun tableau 'agendas') : "
                            + "parcours arrêté, à relancer depuis le début.", now, now);
            repository.save(failed);
            return failed;
        }
        Integer total = run.providerTotal() != null ? run.providerTotal() : page.total();
        if (page.agendas().isEmpty()) {
            AgendaDiscoveryRun ended = endOfDirectory(run, run.cursor(), run.pagesRead(), run.examined(),
                    run.newCount(), run.alreadyKnown(), run.ignored(), total, now);
            repository.save(ended);
            return ended;
        }

        String nextCursor = serializeCursor(page.after());
        boolean endReached = nextCursor == null;
        boolean repeated = !endReached && !seenCursors.add(nextCursor);

        return transactions.execute(status -> {
            int examined = run.examined();
            int created = run.newCount();
            int known = run.alreadyKnown();
            int ignored = run.ignored();
            for (DiscoveredAgenda agenda : page.agendas()) {
                examined++;
                if (agenda.uid() == null || agenda.uid().isBlank()) {
                    ignored++;
                } else if (repository.upsert(agenda, run.id(), now)) {
                    created++;
                } else {
                    known++;
                }
            }
            AgendaDiscoveryRun next;
            if (!endReached && !repeated && isAbnormallyLong(examined, total)) {
                next = copy(run, AgendaDiscoveryRun.Status.FAILED, nextCursor, pageNumber, examined, created, known,
                        ignored, run.errors(), total, examined + " agendas examinés pour " + total + " annoncés : "
                                + "la pagination revient probablement sur ses pas ; parcours arrêté, à relancer "
                                + "depuis le début.", now);
            } else if (repeated) {
                next = copy(run, AgendaDiscoveryRun.Status.FAILED, run.cursor(), pageNumber, examined, created, known,
                        ignored, run.errors(), total, "OpenAgenda a renvoyé un curseur déjà rencontré après la page "
                                + pageNumber + " : parcours arrêté pour ne pas boucler, à relancer depuis le début.",
                        now);
            } else if (endReached) {
                next = endOfDirectory(run, run.cursor(), pageNumber, examined, created, known, ignored, total, now);
            } else {
                next = copy(run, AgendaDiscoveryRun.Status.IN_PROGRESS, nextCursor, pageNumber, examined, created,
                        known, ignored, run.errors(), total, null, null);
            }
            repository.save(next);
            return next;
        });
    }

    /**
     * Un cycle de curseurs plus long qu'un lot n'est pas vu par la détection de curseur répété (seul le dernier
     * curseur est conservé d'un lot à l'autre) : il se traduit par bien plus d'agendas examinés qu'annoncés.
     */
    static boolean isAbnormallyLong(int examined, Integer total) {
        return total != null && examined > 2L * total + OpenAgendaAgendaDirectory.PAGE_SIZE;
    }

    private AgendaDiscoveryRun endOfDirectory(
            AgendaDiscoveryRun run, String cursor, int pagesRead, int examined, int created, int known, int ignored,
            Integer total, LocalDateTime now) {
        if (total != null && examined < total) {
            return copy(run, AgendaDiscoveryRun.Status.FAILED, cursor, pagesRead, examined, created, known, ignored,
                    run.errors(), total, "Fin de l'annuaire après " + examined + " agenda(s) alors qu'OpenAgenda en "
                            + "annonçait " + total + " : parcours non exhaustif, à relancer depuis le début.", now);
        }
        return copy(run, AgendaDiscoveryRun.Status.COMPLETE, cursor, pagesRead, examined, created, known, ignored,
                run.errors(), total, "Fin de l'annuaire atteinte : " + examined + " agenda(s) examiné(s) en "
                        + pagesRead + " page(s).", now);
    }

    private static AgendaDiscoveryRun withStatus(
            AgendaDiscoveryRun run, AgendaDiscoveryRun.Status status, String reason, LocalDateTime now,
            LocalDateTime endedAt) {
        return new AgendaDiscoveryRun(run.id(), run.scope(), status, run.cursor(), run.pagesRead(), run.examined(),
                run.newCount(), run.alreadyKnown(), run.ignored(), run.errors(), run.providerTotal(), reason,
                run.startedAt(), now, endedAt);
    }

    private AgendaDiscoveryRun copy(
            AgendaDiscoveryRun run, AgendaDiscoveryRun.Status status, String cursor, int pagesRead, int examined,
            int created, int known, int ignored, int errors, Integer total, String reason, LocalDateTime endedAt) {
        return new AgendaDiscoveryRun(run.id(), run.scope(), status, cursor, pagesRead, examined, created, known,
                ignored, errors, total, reason, run.startedAt(), now(), endedAt);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** Curseur de la page suivante en JSON ; {@code null} si l'API n'en renvoie pas (fin de l'annuaire). */
    static String serializeCursor(List<Object> after) {
        if (after == null || after.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(after);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Curseur OpenAgenda non sérialisable.", exception);
        }
    }

    static List<Object> parseCursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(cursor, new TypeReference<List<Object>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Curseur de découverte enregistré illisible.", exception);
        }
    }
}
