package com.locallife.backend.auth.application;

/**
 * Adresse e-mail masquée pour les journaux (RGPD, 22/10/2026) : {@code jean@exemple.fr} devient
 * {@code j***@exemple.fr}. Les journaux de l'application ne contiennent jamais d'adresse complète.
 */
public final class EmailMask {

    private EmailMask() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(aucune)";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
