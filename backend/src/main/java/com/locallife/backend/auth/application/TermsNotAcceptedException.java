package com.locallife.backend.auth.application;

import com.locallife.backend.gdpr.application.LegalTerms;

/**
 * Création d'un compte (par Google) sans l'acceptation des conditions et de l'âge minimum : 428, l'interface affiche
 * la case à cocher puis recommence.
 */
public class TermsNotAcceptedException extends RuntimeException {

    public TermsNotAcceptedException() {
        super(LegalTerms.CONSENT_REQUIRED_MESSAGE);
    }
}
