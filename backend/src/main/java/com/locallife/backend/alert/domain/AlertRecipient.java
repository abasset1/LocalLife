package com.locallife.backend.alert.domain;

/**
 * Alerte à traiter par l'envoi planifié. {@code email} : adresse du
 * propriétaire, {@code null} si elle n'est pas vérifiée (pas d'envoi
 * d'e-mail dans ce cas).
 */
public record AlertRecipient(ActivityAlert alert, String email, String unsubscribeToken) {
}
