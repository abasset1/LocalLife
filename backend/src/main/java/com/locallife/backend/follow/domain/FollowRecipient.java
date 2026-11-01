package com.locallife.backend.follow.domain;

/** Utilisateur qui suit au moins une activité ou un lieu ; {@code email} : adresse vérifiée, sinon {@code null}. */
public record FollowRecipient(Long userId, String email) {
}
