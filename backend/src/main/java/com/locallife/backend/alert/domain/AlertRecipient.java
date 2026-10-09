package com.locallife.backend.alert.domain;

/** Alerte à traiter par l'envoi planifié, avec l'adresse (vérifiée) de son propriétaire. */
public record AlertRecipient(ActivityAlert alert, String email, String unsubscribeToken) {
}
