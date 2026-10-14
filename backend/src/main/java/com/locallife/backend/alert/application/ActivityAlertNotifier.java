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
import com.locallife.backend.push.application.ExpoPushService;
import com.locallife.backend.push.infrastructure.PushTokenRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 */
@Service
public class ActivityAlertNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityAlertNotifier.class);

    /** Activités traitées par alerte et par envoi ; le surplus part à l'envoi suivant. */
    static final int MAX_ACTIVITIES_PER_RUN = 100;

    /** Activités détaillées dans l'email ; les autres sont seulement comptées. */
    static final int MAX_ACTIVITIES_LISTED = 20;

    private static final String LOCALE = "fr-FR";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ActivityAlertRepository activityAlertRepository;
    private final CategoryRepository categoryRepository;
    private final EmailService emailService;
    private final ExpoPushService expoPushService;
    private final PushTokenRepository pushTokenRepository;
    private final String frontendBaseUrl;

    public ActivityAlertNotifier(
            ActivityAlertRepository activityAlertRepository, CategoryRepository categoryRepository,
            EmailService emailService, ExpoPushService expoPushService, PushTokenRepository pushTokenRepository,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.activityAlertRepository = activityAlertRepository;
        this.categoryRepository = categoryRepository;
        this.emailService = emailService;
        this.expoPushService = expoPushService;
        this.pushTokenRepository = pushTokenRepository;
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
                LOGGER.warn("Échec de l'envoi de l'alerte {} : {}", recipient.alert().id(), exception.getMessage());
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

        String title = title(alert.city(), activities, foodTrucks);
        boolean delivered = false;

        if (alert.notifyEmail() && recipient.email() != null) {
            try {
                emailService.sendActivityAlertEmail(
                        recipient.email(), "LocalLife : " + title, body(recipient, activities, foodTrucks, labels));
                delivered = true;
            } catch (RuntimeException exception) {
                LOGGER.warn("Échec de l'e-mail de l'alerte {} : {}", alert.id(), exception.getMessage());
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
            if (!activities.isEmpty()) {
                activityAlertRepository.markSent(alert.id(), activities.stream().map(AlertActivity::id).toList());
            }
            if (!foodTrucks.isEmpty()) {
                activityAlertRepository.markFoodTrucksSent(
                        alert.id(), foodTrucks.stream().map(AlertFoodTruck::id).toList());
            }
        }
        return delivered;
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

    String body(
            AlertRecipient recipient, List<AlertActivity> activities, List<AlertFoodTruck> foodTrucks,
            Map<String, String> labels) {
        ActivityAlert alert = recipient.alert();
        List<String> criteria = new ArrayList<>(alert.categoryCodes().stream()
                .map(code -> labels.getOrDefault(code, code))
                .toList());
        if (alert.includeFoodTrucks()) {
            criteria.add("Food trucks");
        }
        StringBuilder text = new StringBuilder()
                .append("Bonjour,\n\n")
                .append("Du nouveau pour ton alerte « ").append(String.join(", ", criteria))
                .append(" » à moins de ").append(alert.radiusKm()).append(" km de ").append(alert.city())
                .append(" :\n\n");
        boolean sections = !activities.isEmpty() && !foodTrucks.isEmpty();
        if (!activities.isEmpty()) {
            if (sections) {
                text.append("Activités :\n");
            }
            activities.stream().limit(MAX_ACTIVITIES_LISTED).forEach(activity -> {
                text.append("- ").append(activity.title());
                if (activity.city() != null && !activity.city().isBlank()) {
                    text.append(" — ").append(activity.city());
                }
                if (activity.startDate() != null) {
                    text.append(", le ").append(DATE_FORMAT.format(activity.startDate()));
                }
                if (activity.approximatePosition()) {
                    text.append(" (emplacement approximatif)");
                }
                text.append('\n');
            });
            if (activities.size() > MAX_ACTIVITIES_LISTED) {
                text.append("… et ").append(activities.size() - MAX_ACTIVITIES_LISTED).append(" autre(s).\n");
            }
        }
        if (!foodTrucks.isEmpty()) {
            if (sections) {
                text.append("\nFood trucks :\n");
            }
            foodTrucks.stream().limit(MAX_ACTIVITIES_LISTED).forEach(foodTruck -> {
                text.append("- ").append(foodTruck.name());
                if (foodTruck.category() != null && !foodTruck.category().isBlank()) {
                    text.append(" (").append(foodTruck.category()).append(')');
                }
                text.append('\n');
            });
            if (foodTrucks.size() > MAX_ACTIVITIES_LISTED) {
                text.append("… et ").append(foodTrucks.size() - MAX_ACTIVITIES_LISTED).append(" autre(s).\n");
            }
        }
        return text
                .append("\nVoir sur la carte : ").append(frontendBaseUrl).append("/\n")
                .append("Gérer mes alertes : ").append(frontendBaseUrl).append("/profile\n")
                .append("Ne plus recevoir cette alerte : ").append(frontendBaseUrl)
                .append("/alerts/unsubscribe?token=").append(recipient.unsubscribeToken()).append("\n\n")
                .append("L'équipe LocalLife")
                .toString();
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
