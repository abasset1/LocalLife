package com.locallife.backend.collector.application;

import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.collector.domain.ImportedActivityContact;
import com.locallife.backend.collector.infrastructure.ImportedActivityContactNoticeRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Avis aux contacts des activités importées : l'adresse e-mail de contact d'une activité récupérée chez une source
 * externe (lien {@code EMAIL}, ex. contact d'inscription OpenAgenda) reçoit un message indiquant que LocalLife
 * référence l'événement, et qu'il faut créer un compte professionnel pour qu'il continue d'être référencé.
 *
 * <p>Un seul message par adresse et par passage, récapitulant ses activités (un organisateur peut en publier des
 * dizaines dans un même import), puis plus rien pour cette adresse pendant {@link #COOLDOWN_DAYS} jours ; une
 * activité n'est jamais annoncée deux fois au même contact. Seules les activités importées depuis moins de
 * {@link #MAX_AGE_DAYS} jours sont concernées, et jamais celles d'une adresse de compte professionnel. Désactivé
 * par défaut ({@code app.import-notices.enabled}) : le message part vers des tiers qui n'ont rien demandé.
 *
 * <p>L'échec d'un envoi est journalisé sans l'adresse en clair ; ses activités seront retentées au passage suivant.
 */
@Service
public class ImportedActivityContactNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedActivityContactNotifier.class);

    /** Ancienneté maximale (création) d'une activité importée pour qu'elle soit annoncée. */
    static final int MAX_AGE_DAYS = 7;

    /** Délai minimal entre deux avis à une même adresse. */
    static final int COOLDOWN_DAYS = 30;

    /** Contacts (adresse × activité) traités par passage ; le surplus part au passage suivant. */
    static final int MAX_CONTACTS_PER_RUN = 500;

    /** Activités détaillées dans le message ; les autres sont seulement comptées. */
    static final int MAX_ACTIVITIES_LISTED = 20;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ImportedActivityContactNoticeRepository repository;
    private final EmailService emailService;
    private final boolean enabled;
    private final String frontendBaseUrl;

    public ImportedActivityContactNotifier(
            ImportedActivityContactNoticeRepository repository, EmailService emailService,
            @Value("${app.import-notices.enabled:false}") boolean enabled,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.repository = repository;
        this.emailService = emailService;
        this.enabled = enabled;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Par défaut chaque jour à 9 h 30 (heure de Paris), configurable par {@code app.import-notices.cron}. */
    @Scheduled(cron = "${app.import-notices.cron:0 30 9 * * *}", zone = "Europe/Paris")
    public void scheduledSend() {
        if (!enabled) {
            return;
        }
        int sent = sendAll();
        LOGGER.info("Avis aux contacts des activités importées : {} message(s) envoyé(s)", sent);
    }

    /** @return le nombre de messages envoyés */
    public int sendAll() {
        Map<String, List<ImportedActivityContact>> byEmail = new LinkedHashMap<>();
        for (ImportedActivityContact contact
                : repository.findPendingContacts(MAX_AGE_DAYS, COOLDOWN_DAYS, MAX_CONTACTS_PER_RUN)) {
            byEmail.computeIfAbsent(contact.email(), email -> new ArrayList<>()).add(contact);
        }
        int sent = 0;
        for (Map.Entry<String, List<ImportedActivityContact>> entry : byEmail.entrySet()) {
            List<ImportedActivityContact> activities = entry.getValue();
            try {
                emailService.sendImportedActivityNoticeEmail(entry.getKey(), subject(activities), body(activities));
                repository.markSent(entry.getKey(),
                        activities.stream().map(ImportedActivityContact::activityId).toList());
                sent++;
            } catch (RuntimeException exception) {
                LOGGER.warn("Échec de l'avis au contact des activités importées {} : {}",
                        activities.stream().map(ImportedActivityContact::activityId).toList(),
                        exception.getClass().getSimpleName());
            }
        }
        return sent;
    }

    static String subject(List<ImportedActivityContact> activities) {
        if (activities.size() == 1) {
            return "Votre événement « " + activities.get(0).title() + " » est référencé sur LocalLife";
        }
        return "Vos " + activities.size() + " événements sont référencés sur LocalLife";
    }

    String body(List<ImportedActivityContact> activities) {
        boolean single = activities.size() == 1;
        StringBuilder text = new StringBuilder()
                .append("Bonjour,\n\n")
                .append("LocalLife, la carte des activités et événements locaux, référence ")
                .append(single ? "votre événement" : "vos événements")
                .append(", repris d'un agenda public où cette adresse figure comme contact :\n\n");
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
                .append("\nPour que ").append(single ? "cet événement continue" : "ces événements continuent")
                .append(" d'être référencé").append(single ? "" : "s")
                .append(" sur LocalLife, créez un compte professionnel :\n")
                .append("1. Créez votre compte : ").append(frontendBaseUrl).append("/register\n")
                .append("2. Depuis votre profil, passez à l'offre Pro : ").append(frontendBaseUrl)
                .append("/profile\n\n")
                .append("Votre compte professionnel vous permet aussi de gérer vos lieux et vos food trucks, "
                        + "depuis l'espace professionnel : ").append(frontendBaseUrl).append("/pro\n\n")
                .append("Voir la carte : ").append(frontendBaseUrl).append("/\n\n")
                .append("Ce message n'est envoyé qu'une fois par événement. Si vous n'êtes pas concerné, "
                        + "vous pouvez l'ignorer.\n\n")
                .append("L'équipe LocalLife")
                .toString();
    }
}
