package com.locallife.backend.gdpr.application;

/**
 * Textes acceptés à l'inscription (RGPD, décision du 22/10/2026) : conditions d'utilisation et politique de
 * confidentialité, dans la version {@link #VERSION}, par une personne d'au moins {@link #MINIMUM_AGE} ans (âge du
 * consentement numérique en France). À changer avec les textes ; la version acceptée est enregistrée sur le compte.
 */
public final class LegalTerms {

    public static final String VERSION = "2026-10-22";
    public static final int MINIMUM_AGE = 15;
    public static final String CONSENT_REQUIRED_MESSAGE = "Coche la case : tu dois avoir au moins " + MINIMUM_AGE
            + " ans et accepter les conditions d'utilisation et la politique de confidentialité.";

    private LegalTerms() {
    }
}
