package com.locallife.backend.follow.application;

import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.follow.domain.FollowRecipient;
import com.locallife.backend.follow.domain.FollowTargetType;
import com.locallife.backend.follow.domain.FollowUpdate;
import com.locallife.backend.follow.infrastructure.FollowRepository;
import com.locallife.backend.push.application.ExpoPushService;
import com.locallife.backend.push.infrastructure.PushTokenRepository;
import com.locallife.backend.subscription.application.PlanLimits;
import java.time.OffsetDateTime;
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
 * Envoi planifié des mises à jour des activités et lieux suivis : pour chaque utilisateur, un seul message
 * récapitulant ce qui a changé depuis l'envoi précédent (rien si rien n'a changé).
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
    }

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
            updates.forEach(update -> followRepository.markNotified(update.followId(), update.maxChangeId(), runStart));
        }
        return delivered;
    }

    /** « Du nouveau : Concert au port » pour un seul suivi, sinon « Du nouveau sur 3 activités et lieux suivis ». */
    static String title(List<FollowUpdate> updates) {
        return updates.size() == 1
                ? "Du nouveau : " + updates.get(0).title()
                : "Du nouveau sur " + updates.size() + " activités et lieux suivis";
    }

    /** « annulée », « dates modifiées, adresse modifiée », « 2 nouvelles activités »… */
    static String describe(FollowUpdate update) {
        List<String> parts = new ArrayList<>();
        KIND_LABELS.forEach((kind, label) -> {
            if (update.kinds().contains(kind)) {
                parts.add(label);
            }
        });
        int count = update.newActivities().size();
        if (count == 1) {
            parts.add("nouvelle activité : " + update.newActivities().get(0));
        } else if (count > 1) {
            parts.add(count + " nouvelles activités");
        }
        return String.join(", ", parts);
    }

    /** Une ligne courte : le texte d'une notification est tronqué par le système. */
    static String pushBody(List<FollowUpdate> updates) {
        if (updates.size() == 1) {
            String description = describe(updates.get(0));
            return Character.toUpperCase(description.charAt(0)) + description.substring(1);
        }
        String names = updates.stream().limit(3).map(FollowUpdate::title).collect(Collectors.joining(" · "));
        return updates.size() > 3 ? names + " · …" : names;
    }

    /** Écran à ouvrir au toucher : la fiche de l'activité s'il n'y en a qu'une, sinon la liste des suivis. */
    static Map<String, Object> pushData(List<FollowUpdate> updates) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "follow-update");
        if (updates.size() == 1 && updates.get(0).type() == FollowTargetType.ACTIVITY
                && !updates.get(0).kinds().contains("DELETED")) {
            data.put("activityId", updates.get(0).targetId());
        }
        return data;
    }

    String emailBody(List<FollowUpdate> updates) {
        StringBuilder text = new StringBuilder("Bonjour,\n\nDu nouveau sur ce que tu suis :\n\n");
        for (FollowUpdate update : updates) {
            text.append("- ").append(update.type() == FollowTargetType.PLACE ? "Lieu « " : "« ")
                    .append(update.title()).append(" » : ").append(describe(update)).append('\n');
            if (update.newActivities().size() > 1) {
                update.newActivities().forEach(activity -> text.append("    · ").append(activity).append('\n'));
            }
        }
        return text
                .append("\nVoir sur Neiri : ").append(frontendBaseUrl).append("/\n")
                .append("Gérer mes suivis : ").append(frontendBaseUrl).append("/profile\n\n")
                .append("L'équipe Neiri")
                .toString();
    }
}
