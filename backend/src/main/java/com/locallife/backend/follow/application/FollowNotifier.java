package com.locallife.backend.follow.application;

import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.common.i18n.FrenchDates;
import com.locallife.backend.follow.domain.FieldChange;
import com.locallife.backend.follow.domain.FollowRecipient;
import com.locallife.backend.follow.domain.FollowTargetType;
import com.locallife.backend.follow.domain.FollowUpdate;
import com.locallife.backend.follow.infrastructure.FollowRepository;
import com.locallife.backend.push.application.ExpoPushService;
import com.locallife.backend.push.infrastructure.PushTokenRepository;
import com.locallife.backend.subscription.application.PlanLimits;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Envoi planifié des mises à jour des activités, lieux et food trucks suivis : pour chaque utilisateur, un seul message
 * récapitulant ce qui a changé depuis l'envoi précédent (rien si rien n'a changé). Pour un food truck suivi, le jour où
 * il est quelque part, ses emplacements du jour sont annoncés une fois (au premier passage où il est présent).
 *
 * <p>Canal : notification push si l'utilisateur a enregistré un appareil (application mobile), sinon e-mail vers son
 * adresse vérifiée — un seul message par mise à jour, pas les deux. Les suivis sont marqués notifiés dès qu'un canal a
 * réussi ; sinon ils seront retentés au passage suivant.
 *
 * <p>Un compte qui n'a plus droit au suivi (offre Premium ou Pro terminée) ne reçoit rien et ses suivis sont avancés
 * sans envoi : il ne recevra pas d'arriéré s'il reprend une offre. Ses suivis restent, il peut les retirer.
 */
@Service
public class FollowNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(FollowNotifier.class);

    /** Nouvelles activités listées au plus par lieu suivi et par envoi. */
    static final int MAX_NEW_ACTIVITIES_PER_PLACE = 10;

    /** Changements conservés : au-delà, déjà notifiés ou trop anciens pour l'être. */
    static final int CHANGE_RETENTION_DAYS = 30;

    private static final Map<String, String> KIND_LABELS = new LinkedHashMap<>();

    static {
        KIND_LABELS.put("CANCELLED", "annulée");
        KIND_LABELS.put("DELETED", "retirée de Neiri");
        KIND_LABELS.put("CLOSED", "retiré de la carte");
        KIND_LABELS.put("DATES", "dates modifiées");
        KIND_LABELS.put("LOCATION", "adresse modifiée");
        KIND_LABELS.put("DETAILS", "informations mises à jour");
        KIND_LABELS.put("CONTACT", "coordonnées mises à jour");
        KIND_LABELS.put("PLANNING", "planning mis à jour");
    }

    /** Nature du changement (V79, V91) dont relève chaque champ détaillé (V94). */
    private static final Map<String, String> FIELD_KINDS = Map.ofEntries(
            Map.entry("start_date", "DATES"), Map.entry("end_date", "DATES"),
            Map.entry("address", "LOCATION"), Map.entry("position", "LOCATION"),
            Map.entry("title", "DETAILS"), Map.entry("name", "DETAILS"), Map.entry("description", "DETAILS"),
            Map.entry("long_description", "DETAILS"), Map.entry("conditions", "DETAILS"),
            Map.entry("age_min", "DETAILS"), Map.entry("age_max", "DETAILS"), Map.entry("url", "DETAILS"),
            Map.entry("category", "DETAILS"), Map.entry("cuisine_type", "DETAILS"),
            Map.entry("website", "CONTACT"), Map.entry("email", "CONTACT"), Map.entry("phone", "CONTACT"),
            Map.entry("contact", "CONTACT"), Map.entry("instagram_url", "CONTACT"),
            Map.entry("facebook_url", "CONTACT"));

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("start_date", "Date"), Map.entry("end_date", "Fin"), Map.entry("address", "Adresse"),
            Map.entry("title", "Titre"), Map.entry("name", "Nom"),
            Map.entry("description", "Description mise à jour"),
            Map.entry("long_description", "Description mise à jour"),
            Map.entry("conditions", "Tarifs et conditions"), Map.entry("age_min", "Âge minimum"),
            Map.entry("age_max", "Âge maximum"), Map.entry("url", "Lien"), Map.entry("category", "Spécialité"),
            Map.entry("cuisine_type", "Type de cuisine"), Map.entry("website", "Site web"),
            Map.entry("email", "E-mail"), Map.entry("phone", "Téléphone"), Map.entry("contact", "Contact"),
            Map.entry("instagram_url", "Instagram"), Map.entry("facebook_url", "Facebook"));

    /** Textes longs : seul le fait qu'ils ont changé est dit. */
    private static final Set<String> TEXT_FIELDS = Set.of("description", "long_description");

    /** Champs dont seule la nouvelle valeur est utile (liens, contacts, tarifs). */
    private static final Set<String> NEW_VALUE_ONLY_FIELDS =
            Set.of("conditions", "url", "website", "email", "contact", "instagram_url", "facebook_url");

    /** Libellés des types de cuisine (FoodTruckCuisine), comme sur le site et l'application. */
    private static final Map<String, String> CUISINE_LABELS = Map.ofEntries(
            Map.entry("BURGER", "Burgers"), Map.entry("PIZZA", "Pizzas"), Map.entry("ASIAN", "Asiatique"),
            Map.entry("MEXICAN", "Mexicain"), Map.entry("CREPES", "Crêpes et galettes"),
            Map.entry("SANDWICH", "Sandwichs"), Map.entry("FRENCH", "Cuisine française"),
            Map.entry("ITALIAN", "Italien"), Map.entry("WORLD", "Cuisine du monde"),
            Map.entry("VEGETARIAN", "Végétarien"), Map.entry("DESSERT", "Desserts et glaces"),
            Map.entry("DRINKS", "Boissons et café"), Map.entry("OTHER", "Autre"));

    private final FollowRepository followRepository;
    private final PlanLimits planLimits;
    private final EmailService emailService;
    private final ExpoPushService expoPushService;
    private final PushTokenRepository pushTokenRepository;
    private final String frontendBaseUrl;

    public FollowNotifier(FollowRepository followRepository, PlanLimits planLimits, EmailService emailService,
            ExpoPushService expoPushService, PushTokenRepository pushTokenRepository,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.followRepository = followRepository;
        this.planLimits = planLimits;
        this.emailService = emailService;
        this.expoPushService = expoPushService;
        this.pushTokenRepository = pushTokenRepository;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Par défaut toutes les heures de 8 h à 21 h (heure de Paris), configurable par {@code app.follows.cron}. */
    @Scheduled(cron = "${app.follows.cron:0 20 8-21 * * *}", zone = "Europe/Paris")
    public void scheduledSend() {
        int sent = sendAll();
        int purged = followRepository.purgeChangesOlderThan(CHANGE_RETENTION_DAYS);
        LOGGER.info("Suivis : {} utilisateur(s) notifié(s), {} ancien(s) changement(s) purgé(s)", sent, purged);
    }

    /** @return le nombre d'utilisateurs notifiés (sur un canal) */
    public int sendAll() {
        int sent = 0;
        for (FollowRecipient recipient : followRepository.findRecipients()) {
            try {
                if (send(recipient)) {
                    sent++;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Échec de l'envoi des suivis de l'utilisateur {} : {}", recipient.userId(),
                        exception.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private boolean send(FollowRecipient recipient) {
        // Instant pris avant la lecture : une activité publiée pendant l'envoi partira au passage suivant.
        OffsetDateTime runStart = OffsetDateTime.now();
        if (!planLimits.canFollow(recipient.userId())) {
            followRepository.markAllNotified(recipient.userId(), runStart);
            return false;
        }
        List<FollowUpdate> updates = followRepository
                .findPendingUpdates(recipient.userId(), MAX_NEW_ACTIVITIES_PER_PLACE).stream()
                .filter(update -> !update.isEmpty())
                .toList();
        if (updates.isEmpty()) {
            return false;
        }

        String title = title(updates);
        boolean delivered = false;
        List<String> tokens = pushTokenRepository.findTokensByUserId(recipient.userId());
        if (!tokens.isEmpty()) {
            try {
                List<String> unregistered = expoPushService.send(tokens, title, pushBody(updates), pushData(updates));
                if (!unregistered.isEmpty()) {
                    pushTokenRepository.deleteByTokens(unregistered);
                }
                delivered = unregistered.size() < tokens.size();
            } catch (RuntimeException exception) {
                LOGGER.warn("Échec de la notification des suivis de l'utilisateur {} : {}", recipient.userId(),
                        exception.getMessage());
            }
        }
        if (!delivered && recipient.email() != null) {
            try {
                emailService.sendActivityAlertEmail(recipient.email(), "Neiri : " + title, emailBody(updates));
                delivered = true;
            } catch (RuntimeException exception) {
                LOGGER.warn("Échec de l'e-mail des suivis de l'utilisateur {} : {}", recipient.userId(),
                        exception.getClass().getSimpleName());
            }
        }

        if (delivered) {
            updates.forEach(update -> {
                followRepository.markNotified(update.followId(), update.maxChangeId(), runStart);
                if (!update.presentToday().isEmpty()) {
                    followRepository.markPresenceNotified(update.followId());
                }
            });
        }
        return delivered;
    }

    /**
     * « Du nouveau : Concert au port » pour un seul suivi, « Chez Marco est là aujourd'hui » pour un food truck dont
     * seule la présence du jour est à annoncer, sinon « Du nouveau sur 3 de tes suivis ».
     */
    static String title(List<FollowUpdate> updates) {
        if (updates.size() > 1) {
            return "Du nouveau sur " + updates.size() + " de tes suivis";
        }
        FollowUpdate update = updates.get(0);
        return update.kinds().isEmpty() && update.newActivities().isEmpty() && !update.presentToday().isEmpty()
                ? update.title() + " est là aujourd'hui"
                : "Du nouveau : " + update.title();
    }

    /**
     * Ce qui a changé, une ligne par élément : le détail des champs quand il est connu (« Date : samedi 22 novembre à
     * 20 h 30 → dimanche 23 novembre à 20 h 30 », « Adresse : … → … »), sinon la nature du changement (« annulée »,
     * « planning mis à jour ») ; puis les nouvelles activités d'un lieu et les emplacements du jour d'un food truck.
     */
    static List<String> lines(FollowUpdate update) {
        List<String> lines = new ArrayList<>();
        KIND_LABELS.forEach((kind, label) -> {
            if (!update.kinds().contains(kind)) {
                return;
            }
            List<String> details = update.changes().stream()
                    .filter(change -> kind.equals(FIELD_KINDS.get(change.field())))
                    .map(FollowNotifier::formatChange)
                    .distinct()
                    .toList();
            if (details.isEmpty()) {
                lines.add(label);
            } else {
                lines.addAll(details);
            }
        });
        int count = update.newActivities().size();
        if (count == 1) {
            lines.add("nouvelle activité : " + update.newActivities().get(0));
        } else if (count > 1) {
            lines.add(count + " nouvelles activités");
        }
        if (!update.presentToday().isEmpty()) {
            lines.add("aujourd'hui : " + String.join(" · ", update.presentToday()));
        }
        return lines;
    }

    /** Tout ce qui a changé, sur une ligne : « Date : … → … · Description mise à jour ». */
    static String describe(FollowUpdate update) {
        return lines(update).stream().map(FollowNotifier::capitalize).collect(Collectors.joining(" · "));
    }

    /** Un champ modifié en clair : « Adresse : 1 rue A, Avignon → 3 quai B, Avignon », « Description mise à jour ». */
    static String formatChange(FieldChange change) {
        String field = change.field();
        String label = FIELD_LABELS.getOrDefault(field, field);
        if (TEXT_FIELDS.contains(field)) {
            return label;
        }
        if ("position".equals(field)) {
            return "Emplacement précisé sur la carte";
        }
        String oldValue = displayValue(field, change.oldValue());
        String newValue = displayValue(field, change.newValue());
        if (newValue == null) {
            return label + " : retiré" + (oldValue == null ? "" : " (avant : " + oldValue + ")");
        }
        if (oldValue == null || NEW_VALUE_ONLY_FIELDS.contains(field)) {
            return label + " : " + newValue;
        }
        return label + " : " + oldValue + " → " + newValue;
    }

    private static String displayValue(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return switch (field) {
            case "start_date", "end_date" -> {
                try {
                    yield FrenchDates.dayAndTime(LocalDateTime.parse(trimmed));
                } catch (DateTimeParseException exception) {
                    yield trimmed;
                }
            }
            case "age_min", "age_max" -> trimmed + " ans";
            case "cuisine_type" -> CUISINE_LABELS.getOrDefault(trimmed, trimmed);
            case "title", "name" -> "« " + trimmed + " »";
            default -> trimmed.replaceAll("\\s+", " ");
        };
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Une ligne courte : le texte d'une notification est tronqué par le système. */
    static String pushBody(List<FollowUpdate> updates) {
        if (updates.size() == 1) {
            return describe(updates.get(0));
        }
        String names = updates.stream().limit(3).map(FollowUpdate::title).collect(Collectors.joining(" · "));
        return updates.size() > 3 ? names + " · …" : names;
    }

    /**
     * Écran à ouvrir au toucher : la fiche de l'activité ou du food truck s'il n'y en a qu'un (encore visible), sinon
     * la liste des suivis.
     */
    static Map<String, Object> pushData(List<FollowUpdate> updates) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "follow-update");
        if (updates.size() == 1) {
            FollowUpdate update = updates.get(0);
            if (update.type() == FollowTargetType.ACTIVITY && !update.kinds().contains("DELETED")) {
                data.put("activityId", update.targetId());
            } else if (update.type() == FollowTargetType.FOOD_TRUCK && !update.kinds().contains("CLOSED")) {
                data.put("foodTruckId", update.targetId());
            }
        }
        return data;
    }

    /**
     * Texte de l'e-mail : un bloc par suivi — type et nom, puis une ligne par changement (avant → après quand il est
     * connu), lien vers la fiche d'une activité encore visible — puis les liens vers Neiri et la gestion des suivis.
     */
    String emailBody(List<FollowUpdate> updates) {
        StringBuilder text = new StringBuilder("Bonjour,\n\n")
                .append(updates.size() == 1 ? "Voici ce qui a changé dans ce que tu suis :"
                        : "Du nouveau sur " + updates.size() + " de tes suivis :")
                .append("\n");
        for (FollowUpdate update : updates) {
            text.append('\n').append(switch (update.type()) {
                case ACTIVITY -> "📅 Activité « ";
                case PLACE -> "📍 Lieu « ";
                case FOOD_TRUCK -> "🚚 Food truck « ";
            }).append(update.title()).append(" »\n");
            for (String line : lines(update)) {
                text.append("  • ").append(capitalize(line)).append('\n');
                if (line.endsWith(" nouvelles activités")) {
                    update.newActivities().forEach(activity -> text.append("      · ").append(activity).append('\n'));
                }
            }
            if (update.type() == FollowTargetType.ACTIVITY && !update.kinds().contains("DELETED")) {
                text.append("  → Voir l'activité : ").append(frontendBaseUrl).append("/?activity=")
                        .append(update.targetId()).append('\n');
            }
        }
        return text
                .append("\n────────────────────\n")
                .append("Voir sur Neiri : ").append(frontendBaseUrl).append("/\n")
                .append("Gérer mes suivis : ").append(frontendBaseUrl).append("/profile\n\n")
                .append("À bientôt,\nL'équipe Neiri")
                .toString();
    }
}
