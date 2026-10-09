package com.locallife.backend.collector.application;

import com.locallife.backend.collector.domain.CollectedActivity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Empreintes des éléments déjà importés (table {@code import_fingerprint}, V48), pour l'import
 * incrémental : {@link ImportService} saute un élément dont l'empreinte n'a pas changé depuis son
 * dernier import réussi, au lieu de réécrire l'activité et de reconstruire tout son enrichissement.
 *
 * <p>L'empreinte porte sur l'élément tel que collecté ({@link CollectedActivity}), pas sur l'activité
 * en base : elle change dès qu'un champ lu depuis la source change, y compris un champ qui n'alimente
 * que l'enrichissement (créneaux, médias, liens, tags...).
 */
@Service
public class ImportFingerprintService {

    /**
     * À incrémenter quand le collecteur ou la normalisation changent ce qui est tiré d'un même élément
     * source (nouveau champ lu, règle de normalisation modifiée) : toutes les empreintes enregistrées
     * deviennent différentes, donc tout est réimporté une fois.
     */
    private static final String VERSION = "v1";

    private final JdbcTemplate jdbcTemplate;

    public ImportFingerprintService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * SHA-256 de l'élément collecté. {@code toString()} d'un record est déterministe (champs dans
     * l'ordre de déclaration, records et listes imbriqués compris).
     */
    public String compute(CollectedActivity collected) {
        return sha256(VERSION + "\n" + collected);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 fait partie de tout JDK conforme : ne peut pas survenir en pratique.
            throw new IllegalStateException("SHA-256 non disponible.", exception);
        }
    }

    /**
     * Empreinte d'un élément <b>en quarantaine</b> : son contenu et l'état du référentiel de
     * classification au moment de la décision. Elle change si l'un ou l'autre change, donc un élément
     * non classé est re-décidé dès qu'un mot-clé ou une catégorie est modifié.
     */
    public String computeQuarantined(String contentFingerprint, String referentialVersion) {
        return sha256("quarantine\n" + contentFingerprint + "\n" + referentialVersion);
    }

    /** Empreintes enregistrées pour une source, en une requête : clé d'import → empreinte. */
    public Map<String, String> findBySourceId(Long sourceId) {
        Map<String, String> fingerprints = new HashMap<>();
        jdbcTemplate.query(
                "SELECT import_key, content_hash FROM import_fingerprint WHERE source_id = ?",
                resultSet -> {
                    fingerprints.put(resultSet.getString("import_key"), resultSet.getString("content_hash"));
                },
                sourceId);
        return fingerprints;
    }

    /** Enregistre (ou remplace) l'empreinte d'un élément importé avec succès. */
    public void record(Long sourceId, String importKey, String contentHash) {
        jdbcTemplate.update(
                "INSERT INTO import_fingerprint (source_id, import_key, content_hash) VALUES (?, ?, ?) "
                        + "ON CONFLICT (source_id, import_key) "
                        + "DO UPDATE SET content_hash = EXCLUDED.content_hash, updated_at = now()",
                sourceId, importKey, contentHash);
    }

    /** Oublie l'empreinte d'un élément : il sera réimporté en entier au prochain passage. */
    public void forget(Long sourceId, String importKey) {
        jdbcTemplate.update(
                "DELETE FROM import_fingerprint WHERE source_id = ? AND import_key = ?", sourceId, importKey);
    }

}
