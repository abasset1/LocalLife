package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.CollectedAccessibility;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLink;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedMedia;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.collector.domain.CollectionReport;
import com.locallife.backend.collector.domain.Collector;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.client.RestClient;

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
 *       réelle.</li>
 * </ul>
 *
 * <b>{@code detailed=1}</b> : toujours envoyé, avec ou sans filtre région.
 * Sans lui, l'API ne renvoie qu'une version résumée des événements —
 * constaté sur un import réel : un seul créneau par événement (repli
 * {@code nextTiming}), aucun {@code longDescription}/{@code conditions}/
 * lien/accessibilité/inscription, et un lieu sans {@code postalCode}/
 * {@code region}/{@code timezone}.
 *
 * <b>Pagination</b> (corrige un écart constaté par Alex : seuls 20
 * événements — la première page, taille par défaut de l'API — étaient
 * collectés au lieu de la totalité d'un agenda). {@code size=300}
 * (maximum autorisé par l'API) et boucle sur les pages suivantes en
 * repassant la clé {@code after} de chaque réponse en paramètres
 * {@code after[]} de la requête suivante (protocole documenté
 * developers.openagenda.com/10-lecture/ ; forme {@code after[]} identique
 * à celle de l'annuaire des agendas, lue comme un tableau par le serveur
 * même pour un curseur à un seul élément). Une valeur {@code null} dans
 * {@code after} est repassée comme la chaîne littérale {@code "null"}
 * (exigé par la documentation).
 *
 * <p><b>Exhaustivité (sprint « agendas exhaustifs », AGD-04)</b> : la
 * collecte n'est déclarée complète ({@link CollectionReport.Status#COMPLETE})
 * que si la fin réelle de la pagination est atteinte : page vide, ou
 * {@code after} absent/vide. Tout autre arrêt donne une collecte
 * {@link CollectionReport.Status#PARTIAL} avec sa raison, jamais une liste
 * tronquée présentée comme complète :
 * <ul>
 *   <li>limite de sécurité de {@code maxPages} pages atteinte alors que
 *       l'API annonce encore une page (configurable,
 *       {@code openagenda.events.max-pages}) ;</li>
 *   <li>curseur déjà rencontré (l'API bouclerait) ;</li>
 *   <li>échec d'une page après les reprises bornées de
 *       {@link OpenAgendaRetry} (quota, 5xx, réseau, réponse illisible) ;</li>
 *   <li>réponse sans tableau {@code events} (incohérente) ;</li>
 *   <li>fin de pagination avec moins d'événements que le {@code total}
 *       annoncé par la première page, quand l'API en annonce un.</li>
 * </ul>
 * Un échec dès la première page reste une {@link CollectorException} : rien
 * n'a été reçu. {@link #collect()} échoue aussi sur une collecte partielle :
 * seul {@link #collectWithReport()} donne accès à des données incomplètes,
 * explicitement signalées. ⚠️ Forme exacte du curseur, présence et sens de
 * {@code total} non vérifiés contre l'API réelle (pas de clé disponible en
 * développement) : voir {@code COLLECTOR_OPERATIONS.md}.
 *
 * <b>Refonte LL-11011 (« adapter le pipeline d'import »)</b> : ce
 * collecteur exploitait jusqu'ici une fraction seulement des données
 * disponibles côté OpenAgenda (un seul horaire via {@code nextTiming},
 * pas de médias/liens/accessibilité/tags/inscription). {@link #collect()}
 * exploite désormais :
 * <ul>
 *   <li><b>{@code timings}</b> (tableau, tous les créneaux de
 *       l'événement) plutôt que {@code nextTiming}/{@code lastTiming}
 *       seuls — un événement récurrent sur OpenAgenda est déjà « à
 *       plat » (chaque répétition listée individuellement), c'est donc
 *       {@code timings} qui porte la « récurrence » au sens de
 *       {@code SPRINT_11.md} pour cette source, pas un RRULE : {@link
 *       CollectedActivity#recurrenceRule()} reste toujours {@code null}
 *       pour ce collecteur (aucun champ RRULE documenté côté OpenAgenda
 *       à notre connaissance — voir la javadoc de {@code
 *       CollectedActivity} pour la raison pour laquelle ce champ existe
 *       malgré tout, en prévision d'une source future qui en fournirait
 *       un réellement). Repli sur {@code nextTiming}/{@code lastTiming}
 *       (comportement historique) si {@code timings} est absent ou
 *       vide, pour tolérer une réponse partielle ;</li>
 *   <li><b>{@code image}</b> : un média principal ({@code base +
 *       filename}, {@code credit}, dimensions) — OpenAgenda ne documente
 *       qu'une seule image par événement à notre connaissance, d'où une
 *       liste à au plus un élément ;</li>
 *   <li><b>{@code links}</b> : liens complémentaires ({@code label} +
 *       {@code link}), repris avec le type générique {@code WEBSITE}
 *       (validé comme une URL par {@code ActivityLinkService}) ;</li>
 *   <li><b>{@code registration}</b> : informations d'inscription — champ
 *       texte le plus riche disponible retenu (email si présent, sinon
 *       téléphone, sinon lien) devient un {@link CollectedLink}
 *       ({@code EMAIL}/{@code PHONE}/{@code WEBSITE} selon le cas) ; une
 *       valeur textuelle brute (ex. instructions non structurées) est en
 *       plus reprise telle quelle dans {@link
 *       CollectedActivity#registrationInfo()} ;</li>
 *   <li><b>{@code accessibility}</b> : reprise des cinq indicateurs
 *       (codes courts {@code mi}/{@code hi}/{@code vi}/{@code pi}/
 *       {@code ii}, voir la javadoc d'{@code Accessibility}) ;</li>
 *   <li><b>{@code keywords.fr}</b> : le premier mot clé reste utilisé
 *       comme {@code category} (comportement historique inchangé, voir
 *       {@code firstKeyword}), et l'ensemble des mots clés devient
 *       désormais {@link CollectedActivity#tags()} (auparavant les
 *       mots clés suivant le premier étaient silencieusement perdus).</li>
 * </ul>
 * ⚠️ Comme le reste de cette classe (voir les décisions déjà signalées
 * ci-dessus), ces structures ({@code image}/{@code links}/{@code
 * registration}/{@code accessibility}) ne sont pas vérifiées contre
 * l'API réelle en sandbox (pas d'accès réseau à api.openagenda.com) —
 * noms de champs déduits de la documentation publique
 * (developers.openagenda.com/evenements/structure/), à confirmer avec
 * une clé réelle.
 *
 * ⚠️ Décisions prises pour ce premier collecteur, à valider :
 * <ul>
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
     * Limite de sécurité par défaut (nombre de pages, soit 600 000 événements) : son déclenchement rend la
     * collecte {@code PARTIAL}, jamais complète — voir la Javadoc de la classe.
     */
    public static final int DEFAULT_MAX_PAGES = 2000;

    /** Type de lien générique (LL-11011) — voir {@code ActivityLinkService}. */
    private static final String LINK_TYPE_WEBSITE = "WEBSITE";
    private static final String LINK_TYPE_EMAIL = "EMAIL";
    private static final String LINK_TYPE_PHONE = "PHONE";

    private final RestClient restClient;
    private final String apiKey;
    private final String agendaUid;
    private final String sourceName;
    private final String regionFilter;
    private final Clock clock;
    private final int maxPages;
    private final OpenAgendaRetry retry;

    /**
     * Constructeur package-privé sans filtre de région, pour les tests
     * qui n'en ont pas besoin (voir le second constructeur pour l'usage
     * en production, {@link OpenAgendaCollectorFactory}).
     */
    OpenAgendaCollector(RestClient.Builder builder, String apiKey, String agendaUid, String sourceName) {
        this(builder, apiKey, agendaUid, sourceName, "", Clock.systemUTC());
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
        this(builder, apiKey, agendaUid, sourceName, regionFilter, Clock.systemUTC());
    }

    OpenAgendaCollector(
            RestClient.Builder builder,
            String apiKey,
            String agendaUid,
            String sourceName,
            String regionFilter,
            Clock clock) {
        this(builder, apiKey, agendaUid, sourceName, regionFilter, clock, DEFAULT_MAX_PAGES,
                OpenAgendaRetry.immediate(1));
    }

    /** Constructeur complet : limite de pages et reprises (production : {@link OpenAgendaCollectorFactory}). */
    OpenAgendaCollector(
            RestClient.Builder builder,
            String apiKey,
            String agendaUid,
            String sourceName,
            String regionFilter,
            Clock clock,
            int maxPages,
            OpenAgendaRetry retry) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages doit être au moins 1.");
        }
        this.maxPages = maxPages;
        this.retry = retry;
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
        this.agendaUid = agendaUid;
        this.sourceName = sourceName;
        this.regionFilter = regionFilter == null ? "" : regionFilter.trim();
        this.clock = clock;
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Collecte complète, ou échec : une collecte partielle lève une {@link CollectorException} avec sa raison,
     * pour qu'aucun appelant ne prenne une liste tronquée pour l'agenda entier.
     */
    @Override
    public List<CollectedActivity> collect() {
        CollectionReport report = collectWithReport();
        if (!report.isComplete()) {
            throw new CollectorException("Collecte OpenAgenda incomplète : " + report.stopReason(), null);
        }
        return report.activities();
    }

    @Override
    public CollectionReport collectWithReport() {
        if (isBlank(apiKey) || isBlank(agendaUid)) {
            throw new CollectorException(
                    "OPENAGENDA_API_KEY et OPENAGENDA_AGENDA_UID doivent être configurés pour collecter.", null);
        }

        List<OpenAgendaEvent> allEvents = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        List<Object> after = null;
        Integer announcedTotal = null;
        int pagesRead = 0;
        String partialReason = null;

        while (true) {
            if (pagesRead >= maxPages) {
                partialReason = "limite de sécurité de " + maxPages + " pages atteinte (openagenda.events.max-pages) "
                        + "alors que l'API annonce encore des événements";
                break;
            }
            List<Object> currentAfter = after;
            OpenAgendaEventsResponse response;
            try {
                response = retry.call("Échec de la collecte depuis OpenAgenda", () -> fetchPage(currentAfter));
            } catch (CollectorException exception) {
                if (pagesRead == 0) {
                    throw exception;
                }
                partialReason = "page " + (pagesRead + 1) + " en échec après " + retry.maxAttempts()
                        + " essai(s) : " + exception.getMessage();
                break;
            }

            if (response == null || response.events() == null) {
                String inconsistency = "réponse incohérente à la page " + (pagesRead + 1)
                        + " (aucun tableau 'events')";
                if (pagesRead == 0) {
                    throw new CollectorException("Échec de la collecte depuis OpenAgenda : " + inconsistency + ".",
                            null);
                }
                partialReason = inconsistency;
                break;
            }
            if (pagesRead == 0) {
                announcedTotal = response.total();
            }
            if (response.events().isEmpty()) {
                break;
            }
            allEvents.addAll(response.events());
            pagesRead++;

            List<Object> nextAfter = response.after();
            if (nextAfter == null || nextAfter.isEmpty()) {
                break;
            }
            if (!seenCursors.add(nextAfter.toString())) {
                partialReason = "curseur de pagination répété après la page " + pagesRead
                        + " : l'API renverrait les mêmes événements en boucle";
                break;
            }
            after = nextAfter;
        }

        if (partialReason == null && announcedTotal != null && allEvents.size() < announcedTotal) {
            partialReason = "fin de pagination après " + allEvents.size() + " événement(s) alors que l'API en "
                    + "annonçait " + announcedTotal;
        }

        List<CollectedActivity> activities = allEvents.stream()
                .filter(event -> event.location() != null)
                .filter(this::matchesRegionFilter)
                .filter(this::hasUpcomingTiming)
                .map(this::toCollectedActivity)
                .toList();
        if (partialReason != null) {
            return new CollectionReport(activities, CollectionReport.Status.PARTIAL, pagesRead, allEvents.size(),
                    "Collecte incomplète : " + partialReason + ".");
        }
        return new CollectionReport(activities, CollectionReport.Status.COMPLETE, pagesRead, allEvents.size(),
                "Fin de pagination atteinte (" + pagesRead + " page(s), " + allEvents.size() + " événement(s)).");
    }

    private OpenAgendaEventsResponse fetchPage(List<Object> currentAfter) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v2/agendas/{agendaUid}/events")
                            .queryParam("key", apiKey)
                            .queryParam("size", PAGE_SIZE)
                            .queryParam("detailed", "1");
                    OpenAgendaCursor.append(uriBuilder, currentAfter);
                    return uriBuilder.build(agendaUid);
                })
                .retrieve()
                .body(OpenAgendaEventsResponse.class);
    }

    /**
     * Filtre temporaire par région (LL non planifié, demande explicite, voir Javadoc de la
     * classe). Comparaison insensible à la casse et aux espaces superflus ; un événement sans
     * champ {@code region} renseigné est exclu dès qu'un filtre est actif, plutôt que retenu par
     * défaut.
     */
    private boolean matchesRegionFilter(OpenAgendaEvent event) {
        if (regionFilter.isEmpty()) {
            return true;
        }
        String eventRegion = event.location().region();
        return eventRegion != null && eventRegion.trim().equalsIgnoreCase(regionFilter);
    }

    /**
     * Exclut les événements dont tous les créneaux sont terminés.
     *
     * <p>Un événement en cours reste conservé tant que son heure de fin
     * (ou, à défaut, son début) n'est pas passée. Pour les événements
     * multi-créneaux, un seul créneau encore à venir suffit à conserver
     * l'activité.</p>
     */
    private boolean hasUpcomingTiming(OpenAgendaEvent event) {
        ZoneId zone = resolveZone(event.location().timezone());
        LocalDateTime now = LocalDateTime.now(clock.withZone(zone));
        return toCollectedTimings(event).stream()
                .map(timing -> timing.end() != null ? timing.end() : timing.begin())
                .filter(value -> value != null)
                .anyMatch(value -> !value.isBefore(now));
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }

    private CollectedActivity toCollectedActivity(OpenAgendaEvent event) {
        OpenAgendaAge age = event.age();
        return new CollectedActivity(
                text(event.title()),
                text(event.description()),
                firstKeyword(event.keywords()),
                "https://openagenda.com/agendas/" + agendaUid + "/events/" + event.slug(),
                event.slug(),
                sourceName,
                text(event.longDescription()),
                text(event.conditions()),
                age == null ? null : age.min(),
                age == null ? null : age.max(),
                toCollectedLocation(event.location()),
                toCollectedTimings(event),
                // Aucun RRULE côté OpenAgenda (timings déjà "à plat") — voir la javadoc de
                // la classe.
                null,
                toCollectedMedia(event.image()),
                toCollectedLinks(event),
                toCollectedAccessibility(event.accessibility()),
                allKeywords(event.keywords()),
                registrationText(event.registration()));
    }

    private CollectedLocation toCollectedLocation(OpenAgendaLocation location) {
        return new CollectedLocation(
                location.name(),
                location.address(),
                location.city(),
                location.postalCode(),
                location.region(),
                null,
                location.latitude(),
                location.longitude(),
                location.timezone());
    }

    /**
     * {@code timings} (tous les créneaux) plutôt que {@code nextTiming}/{@code lastTiming} seuls
     * — voir la javadoc de la classe (LL-11011, « plusieurs horaires supportés »). Repli sur
     * {@code nextTiming}/{@code lastTiming} si {@code timings} est absent ou vide (réponse
     * partielle, ou compatibilité avec l'ancien format de test).
     */
    private List<CollectedTiming> toCollectedTimings(OpenAgendaEvent event) {
        if (event.timings() != null && !event.timings().isEmpty()) {
            return event.timings().stream()
                    .map(timing -> new CollectedTiming(toLocalDateTime(timing.begin()), toLocalDateTime(timing.end())))
                    .toList();
        }
        OpenAgendaTiming fallback = event.nextTiming() != null ? event.nextTiming() : event.lastTiming();
        if (fallback == null) {
            return List.of();
        }
        return List.of(new CollectedTiming(toLocalDateTime(fallback.begin()), toLocalDateTime(fallback.end())));
    }

    private List<CollectedMedia> toCollectedMedia(OpenAgendaImage image) {
        if (image == null || isBlank(image.base()) || isBlank(image.filename())) {
            return List.of();
        }
        Integer width = image.size() == null ? null : image.size().width();
        Integer height = image.size() == null ? null : image.size().height();
        return List.of(new CollectedMedia(image.base() + image.filename(), null, image.credit(), width, height));
    }

    private List<CollectedLink> toCollectedLinks(OpenAgendaEvent event) {
        List<CollectedLink> links = new ArrayList<>();
        if (event.links() != null) {
            for (OpenAgendaLink link : event.links()) {
                if (!isBlank(link.link())) {
                    links.add(new CollectedLink(LINK_TYPE_WEBSITE, link.label(), link.link()));
                }
            }
        }
        links.addAll(toRegistrationLinks(event.registration()));
        return links;
    }

    /**
     * {@code registration} arrive sous deux formes. L'API réelle
     * ({@code detailed=1}) renvoie un tableau {@code [{"type": "email",
     * "value": "..."}]} ({@code type} parmi {@code email}/{@code phone}/
     * {@code link}) : c'est cette forme, non prévue à l'origine, qui
     * faisait échouer la désérialisation de toute la page. La forme objet
     * ({@code email}/{@code phone}/{@code link}/{@code description})
     * supposée initialement reste tolérée. Toute autre forme est ignorée
     * plutôt que de faire échouer la collecte.
     */
    private List<CollectedLink> toRegistrationLinks(Object registration) {
        List<CollectedLink> links = new ArrayList<>();
        if (registration instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> fields) {
                    addRegistrationLink(links, asString(fields.get("type")), asString(fields.get("value")));
                }
            }
        } else if (registration instanceof Map<?, ?> fields) {
            addRegistrationLink(links, "email", asString(fields.get("email")));
            addRegistrationLink(links, "phone", asString(fields.get("phone")));
            addRegistrationLink(links, "link", asString(fields.get("link")));
        }
        return links;
    }

    private String asString(Object value) {
        return value instanceof String string ? string : null;
    }

    private void addRegistrationLink(List<CollectedLink> links, String registrationType, String value) {
        if (isBlank(value) || registrationType == null) {
            return;
        }
        String linkType = switch (registrationType) {
            case "email" -> LINK_TYPE_EMAIL;
            case "phone" -> LINK_TYPE_PHONE;
            case "link" -> LINK_TYPE_WEBSITE;
            default -> null;
        };
        if (linkType != null) {
            links.add(new CollectedLink(linkType, "Inscription", value.trim()));
        }
    }

    /** Texte libre d'inscription : n'existe que dans la forme objet, voir {@link #toRegistrationLinks}. */
    private String registrationText(Object registration) {
        if (registration instanceof Map<?, ?> fields && fields.get("description") instanceof Map<?, ?> description) {
            return asString(description.get("fr"));
        }
        return null;
    }

    private CollectedAccessibility toCollectedAccessibility(OpenAgendaAccessibility accessibility) {
        if (accessibility == null) {
            return null;
        }
        return new CollectedAccessibility(
                accessibility.mi(), accessibility.hi(), accessibility.vi(), accessibility.pi(), accessibility.ii());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Map<String, String> multilingual) {
        return multilingual == null ? null : multilingual.get("fr");
    }

    private String firstKeyword(Map<String, List<String>> keywords) {
        List<String> frenchKeywords = frenchKeywords(keywords);
        return frenchKeywords.isEmpty() ? null : frenchKeywords.get(0);
    }

    /** LL-11011 : tous les mots clés français, désormais conservés (voir la javadoc de la classe). */
    private List<String> allKeywords(Map<String, List<String>> keywords) {
        return frenchKeywords(keywords);
    }

    private List<String> frenchKeywords(Map<String, List<String>> keywords) {
        if (keywords == null) {
            return List.of();
        }
        List<String> frenchKeywords = keywords.get("fr");
        return frenchKeywords == null ? List.of() : frenchKeywords;
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
    private record OpenAgendaEventsResponse(List<OpenAgendaEvent> events, List<Object> after, Integer total) {
    }

    private record OpenAgendaEvent(
            String slug,
            Map<String, String> title,
            Map<String, String> description,
            Map<String, List<String>> keywords,
            OpenAgendaLocation location,
            List<OpenAgendaTiming> timings,
            OpenAgendaTiming nextTiming,
            OpenAgendaTiming lastTiming,
            Map<String, String> longDescription,
            Map<String, String> conditions,
            OpenAgendaAge age,
            OpenAgendaImage image,
            List<OpenAgendaLink> links,
            Object registration,
            OpenAgendaAccessibility accessibility) {
    }

    /**
     * {@code address}/{@code city}/{@code postalCode} ajoutés en LL-EF-008 :
     * champs documentés de l'objet {@code location} OpenAgenda (voir
     * developers.openagenda.com/en/lieux/), au même titre que
     * {@code region} — non vérifiés contre l'API réelle en sandbox, comme
     * le reste de cette classe (voir sa javadoc). {@code timezone} ajouté
     * en LL-11011, même principe.
     */
    private record OpenAgendaLocation(
            String name,
            double latitude,
            double longitude,
            String region,
            String address,
            String city,
            String postalCode,
            String timezone) {
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

    /**
     * LL-11011 : image principale de l'événement — {@code base + filename}
     * forme l'URL complète (convention OpenAgenda pour les images
     * hébergées par leur CDN), non vérifiée contre l'API réelle en
     * sandbox (voir la javadoc de la classe).
     */
    private record OpenAgendaImage(String base, String filename, String credit, OpenAgendaImageSize size) {
    }

    private record OpenAgendaImageSize(Integer width, Integer height) {
    }

    /** LL-11011 : lien complémentaire ({@code label} + {@code link}), non vérifié en sandbox. */
    private record OpenAgendaLink(String label, String link) {
    }

    /**
     * LL-11011 : accessibilité, codes courts OpenAgenda (voir la javadoc
     * d'{@code Accessibility} pour la correspondance moteur/auditif/
     * visuel/psychique/intellectuel) — non vérifié contre l'API réelle
     * en sandbox (voir la javadoc de la classe).
     */
    private record OpenAgendaAccessibility(Boolean mi, Boolean hi, Boolean vi, Boolean pi, Boolean ii) {
    }

}
