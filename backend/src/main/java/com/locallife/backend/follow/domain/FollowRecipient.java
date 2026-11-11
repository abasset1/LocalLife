package com.locallife.backend.follow.domain;

/**
 * Utilisateur qui suit au moins une activité, un lieu ou un food truck ; {@code email} : adresse vérifiée, sinon
 * {@code null}.
 */
public record FollowRecipient(Long userId, String email) {
}
