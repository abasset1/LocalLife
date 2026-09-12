package com.locallife.backend.collector.application;

import com.locallife.backend.collector.domain.ExternalActivity;
import com.locallife.backend.collector.infrastructure.ExternalActivityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service ExternalActivity (LL-11010). {@link #upsert} et
 * {@link #computeHash} fournissent la capacité demandée par le ticket
 * (« détection de modification possible ») ; l'usage réel dans le
 * pipeline d'import (décider quoi faire d'un changement détecté) est du
 * ressort de LL-11011, pas de celui-ci.
 */
@Service
public class ExternalActivityService {

    private final ExternalActivityRepository externalActivityRepository;

    public ExternalActivityService(ExternalActivityRepository externalActivityRepository) {
        this.externalActivityRepository = externalActivityRepository;
    }

    public Optional<ExternalActivity> findBySourceIdAndExternalId(Long sourceId, String externalId) {
        return externalActivityRepository.findBySourceIdAndExternalId(sourceId, externalId);
    }

    /**
     * Empreinte SHA-256 (hexadécimale) d'un payload brut — critère
     * d'acceptation « détection de modification possible » : comparer
     * deux empreintes plutôt que le contenu complet. SHA-256 via
     * {@link MessageDigest} (JDK standard, aucune dépendance
     * supplémentaire) : pas un usage cryptographique à proprement
     * parler, seulement une empreinte de détection de changement, mais
     * SHA-256 reste un choix sûr par défaut (pas de collision pratique à
     * l'échelle du volume d'activités de LocalLife).
     */
    public String computeHash(String rawPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            // Ne peut arriver en pratique : SHA-256 est garanti disponible sur toute JVM standard.
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Crée ou met à jour l'élément identifié par {@code (sourceId,
     * externalId)} — même élément source recollecté, voir la javadoc de
     * {@link ExternalActivity}. {@code lastSeenAt} et {@code
     * payloadHash} sont toujours recalculés ici (pas repris de {@code
     * externalActivity}) : {@code upsert} est le point d'entrée
     * garantissant leur cohérence avec {@code rawPayload}, quel que soit
     * ce que l'appelant a mis dans ces deux champs.
     */
    public ExternalActivity upsert(ExternalActivity externalActivity) {
        Optional<ExternalActivity> existing = externalActivityRepository.findBySourceIdAndExternalId(
                externalActivity.sourceId(), externalActivity.externalId());
        Long id = existing.map(ExternalActivity::id).orElse(null);
        ExternalActivity toSave = new ExternalActivity(
                id,
                externalActivity.sourceId(),
                externalActivity.externalId(),
                externalActivity.externalUrl(),
                externalActivity.sourceUpdatedAt(),
                LocalDateTime.now(),
                computeHash(externalActivity.rawPayload()),
                externalActivity.rawPayload());
        return externalActivityRepository.save(toSave);
    }
}
