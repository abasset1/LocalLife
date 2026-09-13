package com.locallife.backend.collector.domain;

/**
 * Un lien ou contact collecté (LL-11011, {@code SPRINT_11.md}, section
 * 14 : « liens conservés »). Repris tel quel vers {@link
 * com.locallife.backend.link.domain.ActivityLink} par {@code
 * NormalizationService#normalizeLinks} — {@code type} suit la même
 * convention que {@code ActivityLink#type()} ({@code PHONE}/{@code
 * EMAIL} reconnus par {@code ActivityLinkService}, tout autre type
 * validé comme une URL).
 */
public record CollectedLink(String type, String label, String value) {
}
