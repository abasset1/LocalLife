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

}
