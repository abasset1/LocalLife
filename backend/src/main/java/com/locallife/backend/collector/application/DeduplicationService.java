package com.locallife.backend.collector.application;

import com.locallife.backend.collector.domain.CollectedActivity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * Détection simple des doublons (LL-5007) : calcule une clé déterministe
 * identifiant une donnée collectée, destinée à repérer en LL-5008 qu'une
 * activité a déjà été importée plutôt que de la recréer à chaque
 * collecte successive.
 *
 * Stratégie MVP ({@code SPRINT_5.md}) :
 * <ol>
 *   <li>priorité à l'identifiant externe fourni par la source
 *       ({@code source} + {@code externalId}) ;</li>
 *   <li>à défaut (identifiant externe absent), combinaison déterministe
 *       de {@code source}, {@code title}, {@code startDate} et
 *       localisation ({@code latitude}/{@code longitude}), hachée en
 *       SHA-256 pour obtenir une clé de taille fixe.</li>
 * </ol>
 *
 * Ce service ne fait aucune requête en base : il calcule uniquement la
 * clé, en mémoire, à partir d'une {@code CollectedActivity} — avant
 * conversion en {@code Activity} par {@code NormalizationService}, seul
 * endroit où {@code source}/{@code externalId} sont encore disponibles.
 * La comparaison avec les activités déjà persistées (et la décision de
 * créer/mettre à jour/ignorer) relève de LL-5008, pas de ce ticket
 * (dépendance déclarée : LL-5005 uniquement).
 */
@Service
public class DeduplicationService {

    private static final String EXTERNAL_ID_PREFIX = "external:";
    private static final String COMPOSITE_PREFIX = "composite:";

    public String computeDeduplicationKey(CollectedActivity collected) {
        if (hasText(collected.externalId())) {
            return EXTERNAL_ID_PREFIX + nullToEmpty(collected.source()) + ":" + collected.externalId();
        }
        return COMPOSITE_PREFIX + sha256(compositeFields(collected));
    }

    private String compositeFields(CollectedActivity collected) {
        return String.join("|",
                nullToEmpty(collected.source()),
                nullToEmpty(collected.title()),
                nullToEmpty(dateKey(collected.startDate())),
                String.valueOf(collected.latitude()),
                String.valueOf(collected.longitude()));
    }

    private String dateKey(LocalDateTime startDate) {
        return startDate == null ? "" : startDate.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 fait partie de tout JDK conforme : ne peut pas survenir en pratique.
            throw new IllegalStateException("SHA-256 non disponible.", exception);
        }
    }

}
