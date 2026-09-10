package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Premier collecteur réel (LL-5006). Source retenue : l'API officielle
 * OpenAgenda (https://developers.openagenda.com/), sur l'agenda choisi par
 * Alex — décision validée le 14/08/2026 (voir {@code PROJECT_STATUS.md})
 * après comparaison avec Open Data AMP / DATAtourisme : format JSON plus
 * simple à parser, au prix d'une clé API à gérer (pas de secret committé,
 * comme {@code jwt.secret}).
 *
 * Configuration (LL-EF-005 : seule la clé API reste une variable
 * d'environnement partagée entre tous les agendas — l'identifiant
 * d'agenda, le nom de la source et le filtre région sont désormais portés
 * par chaque {@code Source} en base, gérée depuis l'interface
 * d'administration, voir {@link OpenAgendaCollectorFactory}) :
 * <ul>
 *   <li>{@code OPENAGENDA_API_KEY} (variable d'environnement, valeur vide
 *       par défaut — {@link #collect()} échoue explicitement tant qu'elle
 *       n'est pas renseignée) — clé publique OpenAgenda (compte gratuit,
 *       voir developers.openagenda.com/authentification), partagée par
 *       tous les agendas d'un même compte ;</li>
 *   <li>{@code agendaUid} ({@code Source.agendaUid()}) — identifiant
 *       numérique de l'agenda ciblé (visible en pied de barre latérale
 *       sur openagenda.com une fois l'agenda choisi) ;</li>
 *   <li>{@code sourceName} ({@code Source.name()}) — nom lisible à
 *       utiliser comme {@code Source.name} (voir {@code SOURCE_CONTRACT.md}
 *       et {@code getSourceName()} ci-dessous).</li>
 *   <li>{@code regionFilter} ({@code Source.regionFilter()}), optionnel —
 *       filtre <strong>temporaire</strong> (demande explicite, hors ticket
 *       de sprint) : ne conserve que les événements dont {@code
 *       location.region} correspond exactement (insensible à la casse et
 *       aux espaces superflus) à la valeur fournie. Filtrage effectué
 *       côté client après récupération — non vérifié contre l'API réelle
 *       en sandbox (pas d'accès réseau à api.openagenda.com), le champ
 *       {@code region} exact à recevoir est à confirmer avec une clé
 *       réelle. Quand ce filtre est actif, {@code detailed=1} est ajouté
 *       à la requête pour maximiser les chances que {@code region} soit
 *       présent dans la réponse.</li>
 * </ul>
 *
 * <b>Pagination</b> (corrige un écart constaté par Alex : seuls 20
 * événements — la première page, taille par défaut de l'API — étaient
 * collectés au lieu de la totalité d'un agenda). {@code collect()} envoie
 * désormais {@code size=300} (maximum autorisé par l'API) et boucle sur
 * les pages suivantes en repassant la clé {@code after} de chaque réponse
 * en paramètres {@code after} de la requête suivante (protocole documenté
 * developers.openagenda.com/10-lecture/), jusqu'à obtenir une page
 * d'événements vide. Une valeur {@code null} dans {@code after} est
 * repassée telle quelle comme la chaîne littérale {@code "null"} (exigé
 * par la documentation). Un garde-fou ({@link #MAX_PAGES}) arrête la
 * boucle après un nombre de pages déraisonnable, pour ne jamais boucler
 * indéfiniment sur une réponse API inattendue. ⚠️ Non vérifié contre
 * l'API réelle en sandbox (pas d'accès réseau à api.openagenda.com) — le
 * format exact des valeurs {@code after} (types, nombre d'éléments) est à
 * confirmer avec une clé réelle.
 *
 * ⚠️ Décisions prises pour ce premier collecteur, à valider :
 * <ul>
 *   <li>un seul événement par occurrence à venir ({@code nextTiming}),
 *       pas une entrée par créneau de {@code timings} — un événement
 *       récurrent produit donc une seule {@code CollectedActivity} (sa
 *       prochaine occurrence), pas une par répétition ;</li>
 *   <li>catégorie dérivée du premier mot clé français ({@code
 *       keywords.fr[0]}) — OpenAgenda n'a pas de champ « catégorie »
 *       dédié sur les événements ;</li>
 *   <li>les événements sans lieu physique ({@code location} absent, ex.
 *       événements en ligne) sont ignorés — non pertinents pour une
 *       application de découverte d'activités géolocalisées ;</li>
 *   <li>URL source reconstruite ({@code
 *       https://openagenda.com/agendas/{agendaUid}/events/{slug}}) : la
 *       documentation OpenAgenda ne fournit pas d'URL canonique directe
 *       dans la réponse de lecture des événements ;</li>
 *   <li>{@code longDescription}/{@code conditions}/{@code age} (LL-11006,
 *       « fiche événementielle riche ») : mêmes champs français ({@code
 *       .fr}) que {@code title}/{@code description} pour les deux
 *       premiers (multilingues sur OpenAgenda, voir
 *       {@code developers.openagenda.com/evenements/structure/}) ;
 *       {@code age.min}/{@code age.max} repris tels quels (non
 *       multilingue).</li>
 * </ul>
 *
 * <b>Multi-agenda (LL-8004/LL-8009)</b> : cette classe n'est plus un
 * {@code @Component} auto-enregistré — un même agenda (un seul
 * {@code openagenda.agenda-uid}) ne suffisait pas au critère
 * d'acceptation de LL-8004 (« plusieurs agendas Avignon »), et les
 * propriétés {@code openagenda.avignon-*-uid} ajoutées pour ce ticket
 * n'étaient en réalité jamais lues par aucun bean. Depuis LL-EF-005, une
 * instance par source de type {@code API} configurée en base avec un
 * {@code agendaUid} est construite dynamiquement à chaque import par
 * {@link OpenAgendaCollectorFactory} (voir sa Javadoc), qui remplace
 * l'ancienne configuration par propriétés ({@code OpenAgendaSourcesConfig},
 * qui construisait un {@code List<Collector>} fixé au démarrage).
 */
public class OpenAgendaCollector implements Collector {

    private static final String BASE_URL = "https://api.openagenda.com";

    /** Nombre d'événements par page, maximum autorisé par l'API. */
    private static final int PAGE_SIZE = 300;

    /**
     * Garde-fou anti-boucle infinie (nombre de pages, pas d'événements) —
     * voir Javadoc de la classe.
     */
    private static final int MAX_PAGES = 500;

    private final RestClient restClient;
    private final String apiKey;
    private final String agendaUid;
    private final String sourceName;
    private final String regionFilter;

    /**
     * Constructeur package-privé sans filtre de région, pour les tests
     * qui n'en ont pas besoin (voir le second constructeur pour l'usage
     * en production, {@link OpenAgendaCollectorFactory}).
     */
    OpenAgendaCollector(RestClient.Builder builder, String apiKey, String agendaUid, String sourceName) {
        this(builder, apiKey, agendaUid, sourceName, "");
    }

    /**
     * Constructeur package-privé : permet d'injecter un
     * {@link RestClient.Builder} lié à un {@code MockRestServiceServer}
     * plutôt que d'appeler la vraie API OpenAgenda dans les tests, comme
     * {@code GeocodingService}. Utilisé aussi en production, une fois par
     * agenda configuré en base, par {@link OpenAgendaCollectorFactory}.
     */
    OpenAgendaCollector(
            RestClient.Builder builder,
            String apiKey,
            String agendaUid,
            String sourceName,
            String regionFilter) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
        this.agendaUid = agendaUid;
        this.sourceName = sourceName;
        this.regionFilter = regionFilter == null ? "" : regionFilter.trim();
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public List<CollectedActivity> collect() {
        if (isBlank(apiKey) || isBlank(agendaUid)) {
            throw new CollectorException(
                    "OPENAGENDA_API_KEY et OPENAGENDA_AGENDA_UID doivent être configurés pour collecter.", null);
        }

        boolean hasRegionFilter = !regionFilter.isEmpty();

        List<OpenAgendaEvent> allEvents = new ArrayList<>();
        List<Object> after = null;
        int page = 0;

        do {
            List<Object> currentAfter = after;
            OpenAgendaEventsResponse response;
            try {
                response = restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/v2/agendas/{agendaUid}/events")
                                    .queryParam("key", apiKey)
                                    .queryParam("size", PAGE_SIZE);
                            if (hasRegionFilter) {
                                uriBuilder.queryParam("detailed", "1");
                            }
                            if (currentAfter != null) {
                                for (Object value : currentAfter) {
                                    uriBuilder.queryParam("after", value == null ? "null" : String.valueOf(value));
                                }
                            }
                            return uriBuilder.build(agendaUid);
                        })
                        .retrieve()
                        .body(OpenAgendaEventsResponse.class);
            } catch (RestClientException exception) {
                throw new CollectorException("Échec de la collecte depuis OpenAgenda.", exception);
            }

            if (response == null || response.events() == null || response.events().isEmpty()) {
                break;
            }

            allEvents.addAll(response.events());
            after = response.after();
            page++;
        } while (after != null && page < MAX_PAGES);

        return allEvents.stream()
                .filter(event -> event.location() != null)
                .filter(this::matchesRegionFilter)
                .map(this::toCollectedActivity)
                .toList();
    }

    /**
     * Filtre temporaire par région (LL non planifié, demande explicite,
     * voir Javadoc de la classe). Comparaison insensible à la casse et aux
     * espaces superflus ; un événement sans champ {@code region} renseigné
     * est exclu dès qu'un filtre est actif, plutôt que retenu par défaut.
     */
    private boolean matchesRegionFilter(OpenAgendaEvent event) {
        if (regionFilter.isEmpty()) {
            return true;
        }
        String eventRegion = event.location().region();
        return eventRegion != null && eventRegion.trim().equalsIgnoreCase(regionFilter);
    }

    private CollectedActivity toCollectedActivity(OpenAgendaEvent event) {
        OpenAgendaTiming timing = event.nextTiming() != null ? event.nextTiming() : event.lastTiming();
        OpenAgendaAge age = event.age();
        return new CollectedActivity(
                text(event.title()),
                text(event.description()),
                toLocalDateTime(timing == null ? null : timing.begin()),
                toLocalDateTime(timing == null ? null : timing.end()),
                firstKeyword(event.keywords()),
                event.location().latitude(),
                event.location().longitude(),
                "https://openagenda.com/agendas/" + agendaUid + "/events/" + event.slug(),
                event.slug(),
                sourceName,
                event.location().address(),
                event.location().city(),
                event.location().postalCode(),
                text(event.longDescription()),
                text(event.conditions()),
                age == null ? null : age.min(),
                age == null ? null : age.max());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Map<String, String> multilingual) {
        return multilingual == null ? null : multilingual.get("fr");
    }

    private String firstKeyword(Map<String, List<String>> keywords) {
        if (keywords == null) {
            return null;
        }
        List<String> frenchKeywords = keywords.get("fr");
        return (frenchKeywords == null || frenchKeywords.isEmpty()) ? null : frenchKeywords.get(0);
    }

    /**
     * Tolère les deux formats de décalage horaire ISO 8601 ({@code +0100}
     * et {@code +01:00}) : {@link OffsetDateTime#parse(CharSequence)} exige
     * un « : », or la forme exacte renvoyée par l'API OpenAgenda n'est pas
     * garantie (non vérifiable en sandbox faute d'accès réseau à
     * l'API réelle — voir échec signalé par Alex après LL-5006/5008).
     */
    private LocalDateTime toLocalDateTime(String isoOffsetDateTime) {
        if (isoOffsetDateTime == null) {
            return null;
        }
        String normalized = isoOffsetDateTime.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
        return OffsetDateTime.parse(normalized).toLocalDateTime();
    }

    /** Sous-ensemble de la réponse JSON OpenAgenda qui nous intéresse. */
    private record OpenAgendaEventsResponse(List<OpenAgendaEvent> events, List<Object> after) {
    }

    private record OpenAgendaEvent(
            String slug,
            Map<String, String> title,
            Map<String, String> description,
            Map<String, List<String>> keywords,
            OpenAgendaLocation location,
            OpenAgendaTiming nextTiming,
            OpenAgendaTiming lastTiming,
            Map<String, String> longDescription,
            Map<String, String> conditions,
            OpenAgendaAge age) {
    }

    /**
     * {@code address}/{@code city}/{@code postalCode} ajoutés en LL-EF-008 :
     * champs documentés de l'objet {@code location} OpenAgenda (voir
     * developers.openagenda.com/en/lieux/), au même titre que
     * {@code region} — non vérifiés contre l'API réelle en sandbox, comme
     * le reste de cette classe (voir sa javadoc).
     */
    private record OpenAgendaLocation(
            double latitude, double longitude, String region, String address, String city, String postalCode) {
    }

    private record OpenAgendaTiming(String begin, String end) {
    }

    /**
     * LL-11006 : {@code age}, champ optionnel de la réponse OpenAgenda
     * (« âge du public ciblé (par défaut : null). Si défini, objet
     * {@code {min, max}} », developers.openagenda.com/evenements/structure/
     * — non vérifié contre l'API réelle en sandbox, comme le reste de
     * cette classe, voir sa javadoc). {@code min}/{@code max}
     * indépendamment nullables — OpenAgenda documente explicitement le
     * cas d'un âge minimum sans maximum (ex. « interdit aux moins de 18
     * ans »).
     */
    private record OpenAgendaAge(Integer min, Integer max) {
    }

}
