package com.locallife.backend.alert.application;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.domain.AlertActivity;
import com.locallife.backend.alert.domain.AlertRecipient;
import com.locallife.backend.alert.infrastructure.ActivityAlertRepository;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.domain.CategoryTranslation;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Envoi planifié des alertes email : pour chaque alerte dont le
 * propriétaire a confirmé son adresse, un seul email récapitulant les
 * nouvelles activités correspondantes depuis l'envoi précédent (aucun email
 * si rien de nouveau). Un digest plutôt qu'un email par activité : un import
 * peut publier des dizaines d'activités d'un coup.
 *
 * Une activité n'est marquée envoyée qu'après l'envoi réussi de l'email ;
 * l'échec d'une alerte n'empêche pas le traitement des suivantes.
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
    private final String frontendBaseUrl;

    public ActivityAlertNotifier(
            ActivityAlertRepository activityAlertRepository, CategoryRepository categoryRepository,
            EmailService emailService,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.activityAlertRepository = activityAlertRepository;
        this.categoryRepository = categoryRepository;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Par défaut chaque jour à 8 h (heure de Paris), configurable par {@code app.alerts.cron}. */
    @Scheduled(cron = "${app.alerts.cron:0 0 8 * * *}", zone = "Europe/Paris")
    public void scheduledSend() {
        int sent = sendAll();
        LOGGER.info("Alertes email : {} email(s) envoyé(s)", sent);
    }

    /** @return le nombre d'emails envoyés */
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
        if (activities.isEmpty()) {
            return false;
        }

        String subject = activities.size() == 1
                ? "LocalLife : 1 nouvelle activité près de " + alert.city()
                : "LocalLife : " + activities.size() + " nouvelles activités près de " + alert.city();
        emailService.sendActivityAlertEmail(recipient.email(), subject, body(recipient, activities, labels));
        activityAlertRepository.markSent(alert.id(), activities.stream().map(AlertActivity::id).toList());
        return true;
    }

    String body(AlertRecipient recipient, List<AlertActivity> activities, Map<String, String> labels) {
        ActivityAlert alert = recipient.alert();
        String categories = alert.categoryCodes().stream()
                .map(code -> labels.getOrDefault(code, code))
                .collect(Collectors.joining(", "));
        StringBuilder text = new StringBuilder()
                .append("Bonjour,\n\n")
                .append("De nouvelles activités correspondent à ton alerte « ").append(categories)
                .append(" » à moins de ").append(alert.radiusKm()).append(" km de ").append(alert.city())
                .append(" :\n\n");
        activities.stream().limit(MAX_ACTIVITIES_LISTED).forEach(activity -> {
            text.append("- ").append(activity.title());
            if (activity.city() != null && !activity.city().isBlank()) {
                text.append(" — ").append(activity.city());
            }
            if (activity.startDate() != null) {
                text.append(", le ").append(DATE_FORMAT.format(activity.startDate()));
            }
            text.append('\n');
        });
        if (activities.size() > MAX_ACTIVITIES_LISTED) {
            text.append("… et ").append(activities.size() - MAX_ACTIVITIES_LISTED).append(" autre(s).\n");
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
