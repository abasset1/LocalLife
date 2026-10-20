package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.domain.Source;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Construit un {@link OpenAgendaCollector} à partir d'une {@link Source}
 * (LL-EF-005), en remplacement de {@code OpenAgendaSourcesConfig}
 * (LL-8004/LL-8009, supprimée par ce ticket) : les agendas OpenAgenda ne
 * sont plus enregistrés comme une liste fixe de beans Spring construite
 * une fois au démarrage à partir de propriétés d'environnement, mais lus
 * dynamiquement depuis la table {@code source} par {@code ImportService},
 * qui appelle {@link #create(Source)} pour chaque source collectible
 * trouvée à chaque exécution de l'import — ajouter, modifier ou supprimer
 * un agenda depuis l'interface d'administration (LL-EF-005) prend donc
 * effet dès le prochain import, sans redémarrage de l'application.
 *
 * <p>Existe comme classe séparée (plutôt qu'une construction directe dans
 * {@code ImportService}, module {@code collector.application}) pour ne
 * pas exposer les deux constructeurs package-privés d'
 * {@link OpenAgendaCollector} (package {@code collector.infrastructure})
 * en dehors de ce package — {@code ImportService} ne connaît que ce
 * factory et l'interface {@link Collector}, pas les détails de
 * construction d'un collecteur OpenAgenda en particulier.
 *
 * <p>La clé API OpenAgenda ({@code openagenda.api-key}) reste un secret
 * partagé, lu une seule fois depuis les variables d'environnement — comme
 * {@code jwt.secret}, elle n'est jamais stockée en base ni exposée via
 * l'API d'administration des sources ({@code SourceController}), même si
 * {@link Source#agendaUid()} et {@link Source#regionFilter()} (non
 * sensibles) le sont.
 */
@Component
public class OpenAgendaCollectorFactory {

    private final String apiKey;
    private final int maxPages;
    private final int maxAttempts;

    /** Sans configuration de pagination : valeurs par défaut (tests). */
    public OpenAgendaCollectorFactory(String apiKey) {
        this(apiKey, OpenAgendaCollector.DEFAULT_MAX_PAGES, OpenAgendaRetry.DEFAULT_MAX_ATTEMPTS);
    }

    /**
     * @param maxPages    limite de sécurité par agenda ; atteinte, la collecte est signalée incomplète
     * @param maxAttempts essais par page (quota, 5xx, réseau, réponse illisible), voir {@link OpenAgendaRetry}
     */
    @Autowired
    public OpenAgendaCollectorFactory(
            @Value("${openagenda.api-key:}") String apiKey,
            @Value("${openagenda.events.max-pages:" + OpenAgendaCollector.DEFAULT_MAX_PAGES + "}") int maxPages,
            @Value("${openagenda.http.max-attempts:" + OpenAgendaRetry.DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts) {
        this.apiKey = apiKey;
        this.maxPages = maxPages;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Construit un collecteur pour la source donnée. Ne vérifie pas que
     * {@code source.agendaUid()} est renseigné : c'est à l'appelant
     * ({@code ImportService}) de ne sélectionner que des sources
     * collectibles avant d'appeler cette méthode (voir sa Javadoc) —
     * {@link OpenAgendaCollector#collect()} échoue explicitement sinon.
     */
    public Collector create(Source source) {
        return new OpenAgendaCollector(
                OpenAgendaErrors.restClientBuilder(), apiKey, source.agendaUid(), source.name(),
                source.regionFilter(), Clock.systemUTC(), maxPages, OpenAgendaRetry.withDefaults(maxAttempts));
    }
}
