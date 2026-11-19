package com.locallife.backend.alert.application;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.domain.AlertActivity;
import com.locallife.backend.alert.domain.AlertFoodTruck;
import com.locallife.backend.alert.domain.AlertRecipient;
import com.locallife.backend.alert.infrastructure.ActivityAlertRepository;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.domain.CategoryTranslation;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.common.i18n.FrenchDates;
import com.locallife.backend.push.application.ExpoPushService;
import com.locallife.backend.push.infrastructure.PushTokenRepository;
import com.locallife.backend.subscription.application.PlanLimits;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Envoi planifié des alertes : pour chaque alerte, un seul message
 * récapitulant les nouvelles activités (et, si l'alerte les couvre, les
 * nouveaux food trucks) correspondants depuis l'envoi
 * précédent (rien si rien de nouveau), sur ses canaux actifs — e-mail vers
 * une adresse vérifiée et/ou notification push vers les appareils
 * enregistrés (application mobile). Un récapitulatif plutôt qu'un message
 * par activité : un import peut publier des dizaines d'activités d'un coup.
 *
 * Les activités sont marquées envoyées dès qu'au moins un canal a réussi
 * (sinon elles seront retentées au prochain passage) ; l'échec d'une alerte
 * n'empêche pas le traitement des suivantes.
 *
 * Une alerte à mots-clés (Premium et Pro) n'est plus envoyée quand le compte est revenu au gratuit : ses
 * nouveautés sont marquées traitées sans envoi, pour qu'il ne reçoive pas d'arriéré s'il reprend une offre. Sans
 * ses mots-clés, elle repart normalement.
 */
@Service
public class ActivityAlertNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityAlertNotifier.class);

    /** Activités traitées par alerte et par envoi ; le surplus part à l'envoi suivant. */
    static final int MAX_ACTIVITIES_PER_RUN = 100;

    /** Activités détaillées dans l'email ; les autres sont seulement comptées. */
    static final int MAX_ACTIVITIES_LISTED = 20;

    private static final String LOCALE = "fr-FR";
    private static final String SEPARATOR = "────────────────────";
    /** Zoom de la carte ouverte par le lien d'une activité, et par le lien « Tout voir sur la carte ». */
    private static final int ACTIVITY_ZOOM = 15;
    private static final int ALERT_ZOOM = 12;

    private final ActivityAlertRepository activityAlertRepository;
    private final CategoryRepository categoryRepository;
    private final EmailService emailService;
    private final ExpoPushService expoPushService;
    private final PushTokenRepository pushTokenRepository;
    private final PlanLimits planLimits;
    private final String frontendBaseUrl;

    public ActivityAlertNotifier(
            ActivityAlertRepository activityAlertRepository, CategoryRepository categoryRepository,
            EmailService emailService, ExpoPushService expoPushService, PushTokenRepository pushTokenRepository,
            PlanLimits planLimits, @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.activityAlertRepository = activityAlertRepository;
        this.categoryRepository = categoryRepository;
        this.emailService = emailService;
        this.expoPushService = expoPushService;
        this.pushTokenRepository = pushTokenRepository;
        this.planLimits = planLimits;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Par défaut chaque jour à 8 h (heure de Paris), configurable par {@code app.alerts.cron}. */
    @Scheduled(cron = "${app.alerts.cron:0 0 8 * * *}", zone = "Europe/Paris")
    public void scheduledSend() {
        int sent = sendAll();
        LOGGER.info("Alertes : {} alerte(s) notifiée(s)", sent);
    }

    /** @return le nombre d'alertes notifiées (sur au moins un canal) */
    public int sendAll() {
        Map<String, String> labels = categoryLabels();
        int sent = 0;
        for (AlertRecipient recipient : activityAlertRepository.findAllRecipients()) {
            try {
                if (send(recipient, labels)) {
                    sent++;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Échec de l'envoi de l'alerte {} : {}", recipient.alert().id(),
                        exception.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private boolean send(AlertRecipient recipient, Map<String, String> labels) {
        ActivityAlert alert = recipient.alert();
        List<AlertActivity> activities =
                activityAlertRepository.findNewMatchingActivities(alert.id(), MAX_ACTIVITIES_PER_RUN);
        List<AlertFoodTruck> foodTrucks = alert.includeFoodTrucks()
                ? activityAlertRepository.findNewMatchingFoodTrucks(alert.id(), MAX_ACTIVITIES_PER_RUN)
                : List.of();
        if (activities.isEmpty() && foodTrucks.isEmpty()) {
            return false;
        }
        if (!alert.keywords().isEmpty() && !planLimits.canUseAlertKeywords(alert.userId())) {
            markAllSent(alert, activities, foodTrucks);
            return false;
        }

        String title = title(alert.city(), activities, foodTrucks);
        boolean delivered = false;

        if (alert.notifyEmail() && recipient.email() != null) {
            try {
                emailService.sendActivityAlertEmail(
                        recipient.email(), "Neiri : " + title, body(recipient, activities, foodTrucks, labels),
                        oneClickUnsubscribeUrl(recipient));
                delivered = true;
            } catch (RuntimeException exception) {
                LOGGER.warn("Échec de l'e-mail de l'alerte {} : {}", alert.id(), exception.getClass().getSimpleName());
            }
        }

        if (alert.notifyPush()) {
            List<String> tokens = pushTokenRepository.findTokensByUserId(alert.userId());
            if (!tokens.isEmpty()) {
                try {
                    List<String> unregistered = expoPushService.send(
                            tokens, title, pushBody(activities, foodTrucks), pushData(activities, foodTrucks));
                    if (!unregistered.isEmpty()) {
                        pushTokenRepository.deleteByTokens(unregistered);
                    }
                    // Au moins un appareil encore enregistré a reçu la notification.
                    delivered = delivered || unregistered.size() < tokens.size();
                } catch (RuntimeException exception) {
                    LOGGER.warn("Échec de la notification de l'alerte {} : {}", alert.id(), exception.getMessage());
                }
            }
        }

        if (delivered) {
            markAllSent(alert, activities, foodTrucks);
        }
        return delivered;
    }

    private void markAllSent(ActivityAlert alert, List<AlertActivity> activities, List<AlertFoodTruck> foodTrucks) {
        if (!activities.isEmpty()) {
            activityAlertRepository.markSent(alert.id(), activities.stream().map(AlertActivity::id).toList());
        }
        if (!foodTrucks.isEmpty()) {
            activityAlertRepository.markFoodTrucksSent(
                    alert.id(), foodTrucks.stream().map(AlertFoodTruck::id).toList());
        }
    }

    /** « 1 nouvelle activité », « 2 nouveaux food trucks » ou « 3 nouveautés » près de la ville. */
    static String title(String city, List<AlertActivity> activities, List<AlertFoodTruck> foodTrucks) {
        int count = activities.size() + foodTrucks.size();
        String what;
        if (foodTrucks.isEmpty()) {
            what = count == 1 ? "1 nouvelle activité" : count + " nouvelles activités";
        } else if (activities.isEmpty()) {
            what = count == 1 ? "1 nouveau food truck" : count + " nouveaux food trucks";
        } else {
            what = count + " nouveautés";
        }
        return what + " près de " + city;
    }

    /** Premiers titres, sur une ligne : le texte d'une notification est tronqué par le système. */
    static String pushBody(List<AlertActivity> activities, List<AlertFoodTruck> foodTrucks) {
        List<String> names = new ArrayList<>();
        activities.forEach(activity -> names.add(activity.title()));
        foodTrucks.forEach(foodTruck -> names.add("🚚 " + foodTruck.name()));
        String titles = names.stream().limit(3).collect(Collectors.joining(" · "));
        return names.size() > 3 ? titles + " · …" : titles;
    }

    /**
     * Écran à ouvrir au toucher de la notification : la fiche de l'activité
     * ou du food truck s'il n'y a qu'une nouveauté, sinon la carte.
     */
    static Map<String, Object> pushData(List<AlertActivity> activities, List<AlertFoodTruck> foodTrucks) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "activity-alert");
        if (activities.size() == 1 && foodTrucks.isEmpty()) {
            data.put("activityId", activities.get(0).id());
        } else if (foodTrucks.size() == 1 && activities.isEmpty()) {
            data.put("foodTruckId", foodTrucks.get(0).id());
        }
        return data;
    }

    /**
     * Texte de l'email : résumé, rappel de l'alerte, activités par date (titre ; date, heure et commune ; lien vers
     * la fiche), food trucks, puis liens vers la carte, la gestion des alertes et le désabonnement.
     */
    /** URL de désinscription en un clic (en-tête {@code List-Unsubscribe}), servie par l'API sous le même domaine. */
    private String oneClickUnsubscribeUrl(AlertRecipient recipient) {
        return frontendBaseUrl + "/api/v1/alerts/unsubscribe/one-click?token=" + recipient.unsubscribeToken();
    }

    String body(
            AlertRecipient recipient, List<AlertActivity> activities, List<AlertFoodTruck> foodTrucks,
            Map<String, String> labels) {
        ActivityAlert alert = recipient.alert();
        StringBuilder text = new StringBuilder()
                .append("Bonjour,\n\n")
                .append(summary(alert.city(), activities, foodTrucks)).append("\n\n")
                .append("Ton alerte : ").append(criteria(alert, labels)).append('\n');

        if (!activities.isEmpty()) {
            section(text, activities.size() == 1 ? "1 ACTIVITÉ" : activities.size() + " ACTIVITÉS");
            activities.stream().limit(MAX_ACTIVITIES_LISTED).forEach(activity -> {
                text.append(activity.title()).append('\n');
                String when = when(activity.startDate());
                List<String> details = new ArrayList<>();
                if (when != null) {
                    details.add(when);
                }
                if (activity.city() != null && !activity.city().isBlank()) {
                    details.add(activity.city().trim()
                            + (activity.approximatePosition() ? " (emplacement approximatif)" : ""));
                } else if (activity.approximatePosition()) {
                    details.add("emplacement approximatif");
                }
                if (!details.isEmpty()) {
                    text.append(String.join(" · ", details)).append('\n');
                }
                text.append("→ Voir l'activité : ").append(activityLink(activity)).append("\n\n");
            });
            if (activities.size() > MAX_ACTIVITIES_LISTED) {
                int others = activities.size() - MAX_ACTIVITIES_LISTED;
                text.append("… et ").append(others).append(others == 1 ? " autre activité" : " autres activités")
                        .append(" à découvrir sur la carte.\n");
            }
        }

        if (!foodTrucks.isEmpty()) {
            section(text, foodTrucks.size() == 1 ? "1 FOOD TRUCK" : foodTrucks.size() + " FOOD TRUCKS");
            foodTrucks.stream().limit(MAX_ACTIVITIES_LISTED).forEach(foodTruck -> {
                text.append("🚚 ").append(foodTruck.name());
                if (foodTruck.category() != null && !foodTruck.category().isBlank()) {
                    text.append(" · ").append(foodTruck.category());
                }
                text.append('\n');
            });
            if (foodTrucks.size() > MAX_ACTIVITIES_LISTED) {
                int others = foodTrucks.size() - MAX_ACTIVITIES_LISTED;
                text.append("… et ").append(others).append(others == 1 ? " autre food truck" : " autres food trucks")
                        .append(" à découvrir sur la carte.\n");
            }
        }

        blankLine(text);
        return text
                .append(SEPARATOR).append('\n')
                .append("Tout voir sur la carte : ").append(mapLink(alert)).append('\n')
                .append("Gérer mes alertes : ").append(frontendBaseUrl).append("/profile\n")
                .append("Ne plus recevoir cette alerte : ").append(frontendBaseUrl)
                .append("/alerts/unsubscribe?token=").append(recipient.unsubscribeToken()).append("\n\n")
                .append("À bientôt,\nL'équipe Neiri")
                .toString();
    }

    /** « 3 nouvelles activités correspondent à ton alerte autour de Lyon. » */
    private static String summary(String city, List<AlertActivity> activities, List<AlertFoodTruck> foodTrucks) {
        List<String> parts = new ArrayList<>();
        if (!activities.isEmpty()) {
            parts.add(activities.size() == 1 ? "1 nouvelle activité" : activities.size() + " nouvelles activités");
        }
        if (!foodTrucks.isEmpty()) {
            parts.add(foodTrucks.size() == 1 ? "1 nouveau food truck" : foodTrucks.size() + " nouveaux food trucks");
        }
        boolean single = activities.size() + foodTrucks.size() == 1;
        return String.join(" et ", parts) + (single ? " correspond" : " correspondent")
                + " à ton alerte autour de " + city + ".";
    }

    /** « Sport, Food trucks · mots-clés « jazz » · 10 km autour de Lyon ». */
    private static String criteria(ActivityAlert alert, Map<String, String> labels) {
        List<String> categories = new ArrayList<>(alert.categoryCodes().stream()
                .map(code -> labels.getOrDefault(code, code))
                .toList());
        if (alert.includeFoodTrucks()) {
            categories.add("Food trucks");
        }
        if (categories.isEmpty()) {
            categories.add("Toutes catégories");
        }
        List<String> parts = new ArrayList<>();
        parts.add(String.join(", ", categories));
        if (!alert.keywords().isEmpty()) {
            parts.add((alert.keywords().size() == 1 ? "mot-clé " : "mots-clés ") + alert.keywords().stream()
                    .map(keyword -> "« " + keyword + " »").collect(Collectors.joining(", ")));
        }
        parts.add(alert.radiusKm() + " km autour de " + alert.city());
        return String.join(" · ", parts);
    }

    private static void section(StringBuilder text, String heading) {
        blankLine(text);
        text.append(SEPARATOR).append('\n').append(heading).append('\n').append(SEPARATOR)
                .append("\n\n");
    }

    /** Une seule ligne vide avant un séparateur, que le bloc précédent en finisse déjà une ou non. */
    private static void blankLine(StringBuilder text) {
        if (!text.toString().endsWith("\n\n")) {
            text.append('\n');
        }
    }

    /** « samedi 18 octobre à 15 h », sans heure si elle est inconnue (minuit). */
    static String when(LocalDateTime startDate) {
        return FrenchDates.dayAndTime(startDate);
    }

    /** Fiche de l'activité sur le site, carte centrée sur elle quand sa position est connue. */
    String activityLink(AlertActivity activity) {
        StringBuilder link = new StringBuilder(frontendBaseUrl).append("/?activity=").append(activity.id());
        if (activity.latitude() != null && activity.longitude() != null) {
            link.append(mapPosition(activity.latitude(), activity.longitude(), ACTIVITY_ZOOM));
        }
        return link.toString();
    }

    /** Carte centrée sur la ville de l'alerte. */
    private String mapLink(ActivityAlert alert) {
        return frontendBaseUrl + "/?" + mapPosition(alert.latitude(), alert.longitude(), ALERT_ZOOM).substring(1);
    }

    private static String mapPosition(double latitude, double longitude, int zoom) {
        return String.format(Locale.ROOT, "&lat=%.5f&lng=%.5f&zoom=%d", latitude, longitude, zoom);
    }

    private Map<String, String> categoryLabels() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::code, category -> category.translations().stream()
                        .filter(translation -> LOCALE.equals(translation.locale()))
                        .map(CategoryTranslation::label)
                        .findFirst()
                        .orElse(category.code())));
    }

}
