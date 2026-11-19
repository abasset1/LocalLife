package com.locallife.backend.auth.application;

/**
 * Abstraction d'envoi d'email (LL-EF-007). Permet de faire varier
 * l'implémentation (SMTP réel, no-op en test, etc.) sans changer
 * {@code PasswordResetService}.
 */
public interface EmailService {

    /**
     * Envoie l'email de réinitialisation de mot de passe.
     *
     * @param to        adresse email du destinataire
     * @param resetLink lien complet (frontend) contenant le token en clair —
     *                  ne doit jamais être loggé par l'implémentation
     *                  (voir {@code SmtpEmailService})
     */
    void sendPasswordResetEmail(String to, String resetLink);

    /**
     * Envoie l'email de confirmation d'adresse à l'inscription.
     *
     * @param to               adresse email du destinataire
     * @param verificationLink lien complet (frontend) contenant le token en
     *                         clair — ne doit jamais être loggé par
     *                         l'implémentation (voir {@code SmtpEmailService})
     */
    void sendEmailVerificationEmail(String to, String verificationLink);

    /**
     * Envoie le récapitulatif d'une alerte email (nouvelles activités
     * correspondant aux critères de l'utilisateur, voir
     * {@code ActivityAlertNotifier}).
     *
     * @param to      adresse email du destinataire
     * @param subject objet de l'email
     * @param text    corps texte, contenant le lien de désinscription — à ne
     *                pas logger (le jeton suffit à supprimer l'alerte)
     */
    void sendActivityAlertEmail(String to, String subject, String text);

    /**
     * Comme {@link #sendActivityAlertEmail(String, String, String)}, avec désinscription en un clic (RFC 8058) :
     * en-têtes {@code List-Unsubscribe} et {@code List-Unsubscribe-Post}, qui font apparaître le bouton
     * « Se désabonner » de Gmail, Outlook ou Apple Mail et sont exigés par Gmail et Yahoo pour les envois réguliers.
     *
     * @param oneClickUnsubscribeUrl URL appelée en POST par la messagerie — contient le jeton, à ne pas logger
     */
    default void sendActivityAlertEmail(String to, String subject, String text, String oneClickUnsubscribeUrl) {
        sendActivityAlertEmail(to, subject, text);
    }


    /**
     * Lien de confirmation d'un changement d'adresse, envoyé à la <b>nouvelle</b> adresse : elle ne remplace
     * l'ancienne qu'au clic.
     */
    void sendEmailChangeConfirmationEmail(String to, String confirmationLink);

    /** Avertit l'<b>ancienne</b> adresse qu'un changement a été demandé, sans le lien de confirmation. */
    void sendEmailChangeNoticeEmail(String to, String newEmailMasked);

    /**
     * Décision de l'équipe sur une démarche professionnelle (offre Pro, gestion d'un lieu, modification de lieu,
     * food truck) : texte complet, sans lien secret.
     */
    void sendProDecisionEmail(String to, String subject, String text);

    /**
     * Avis au contact d'une activité importée d'une source externe (voir {@code ImportedActivityContactNotifier}) :
     * Neiri référence l'événement, un compte professionnel est nécessaire pour qu'il continue de l'être.
     */
    void sendImportedActivityNoticeEmail(String to, String subject, String text);

    /**
     * Alerte destinée à un administrateur (nouvelle suggestion ou anomalie signalée, voir {@code FeedbackNotifier}) :
     * texte complet, sans lien secret.
     */
    void sendAdminNotificationEmail(String to, String subject, String text);

}
