package com.locallife.backend.collector.domain;

/**
 * Un média collecté (LL-11011, {@code SPRINT_11.md}, section 14 :
 * « médias conservés »). Repris tel quel vers {@link
 * com.locallife.backend.media.domain.Media} par {@code
 * NormalizationService#normalizeMedia} — seul {@code url} est
 * réellement indispensable (même règle que {@code MediaService#create}).
 */
public record CollectedMedia(String url, String altText, String credit, Integer width, Integer height) {
}
