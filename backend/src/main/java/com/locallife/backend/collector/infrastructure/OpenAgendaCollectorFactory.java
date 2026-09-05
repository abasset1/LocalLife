package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.domain.Source;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    public OpenAgendaCollectorFactory(@Value("${openagenda.api-key:}") String apiKey) {
        this.apiKey = apiKey;
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
                RestClient.builder(), apiKey, source.agendaUid(), source.name(), source.regionFilter());
    }
}
