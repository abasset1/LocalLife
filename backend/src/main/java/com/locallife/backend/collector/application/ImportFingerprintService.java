package com.locallife.backend.collector.application;

import com.locallife.backend.collector.domain.CollectedActivity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
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

    /**
     * Empreinte {@code f} d'une activité {@code a} non archivée d'une source <b>active</b> : une source désactivée
     * ne retient plus ses éléments (sinon ses activités, figées, masqueraient celles de la source qui la remplace).
     * Le {@code WHERE} est complété par l'appelant.
     */
    private static final String LIVE_ACTIVITY_OF_ANOTHER_SOURCE =
            "SELECT 1 FROM import_fingerprint f JOIN activity a ON a.source_id = f.source_id "
                    + "AND a.import_key = f.import_key AND a.status <> 'ARCHIVED' "
                    + "JOIN source s ON s.id = f.source_id AND s.status = 'ACTIVE'";

    /**
     * Préfixe des URI DATAtourisme : une URI identifie une fiche dans toute la France, quel que soit le fichier
     * (régional, France) qui la publie. Deux sources DATAtourisme qui la lisent ont donc le même élément, même si
     * le titre ou la date de début diffèrent (marché découpé par jours de la semaine dans l'une, continu dans
     * l'autre parce que l'API n'a pas répondu).
     */
    static final String DATATOURISME_URI_PREFIX = "https://data.datatourisme.fr/";

    /**
     * Même élément que l'activité {@code a} : même URI DATAtourisme, ou, pour un autre identifiant (slug
     * OpenAgenda, qui peut être partagé), même titre (casse et espaces de bord ignorés) et même date de début.
     * {@code %1$s} et {@code %2$s} : titre et date de l'autre activité (colonnes ou paramètres).
     */
    private static final String SAME_ELEMENT_AS = "(f.external_id LIKE '" + DATATOURISME_URI_PREFIX + "%%' "
            + "OR (lower(btrim(a.title)) = lower(btrim(%1$s)) AND a.start_date = %2$s))";

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

    /**
     * Enregistre (ou remplace) l'empreinte d'un élément importé avec succès.
     *
     * @param externalId identifiant de l'élément chez son fournisseur (slug OpenAgenda), {@code null} si
     *                   la source n'en fournit pas ; sert à la déduplication entre sources
     */
    public void record(Long sourceId, String importKey, String contentHash, String externalId) {
        jdbcTemplate.update(
                "INSERT INTO import_fingerprint (source_id, import_key, content_hash, external_id) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (source_id, import_key) DO UPDATE SET "
                        + "content_hash = EXCLUDED.content_hash, external_id = EXCLUDED.external_id, "
                        + "updated_at = now()",
                sourceId, importKey, contentHash, externalId);
    }

    /**
     * Vrai si une <b>autre</b> source, active, a déjà une activité non archivée pour le même élément : même URI
     * DATAtourisme, ou même identifiant externe avec même titre et même date de début (voir {@link
     * #SAME_ELEMENT_AS}).
     */
    public boolean existsLiveDuplicateInAnotherSource(
            Long sourceId, String externalId, String title, LocalDateTime startDate) {
        Boolean found = jdbcTemplate.queryForObject(
                "SELECT EXISTS (" + LIVE_ACTIVITY_OF_ANOTHER_SOURCE
                        + " WHERE f.external_id = ? AND f.source_id <> ? AND "
                        + String.format(SAME_ELEMENT_AS, "?", "?") + ")",
                Boolean.class, externalId, sourceId, title, startDate);
        return Boolean.TRUE.equals(found);
    }

    /**
     * Activités non archivées à archiver parce qu'elles doublonnent une activité restée en place, selon la même
     * règle que {@link #existsLiveDuplicateInAnotherSource} :
     * <ul>
     *   <li>de {@code sourceId}, celles qui doublonnent une activité <b>plus ancienne</b> (id plus petit) d'une
     *       autre source active : la plus ancienne reste publiée ;</li>
     *   <li>d'une source <b>désactivée</b>, celles qui doublonnent une activité de {@code sourceId} : la source
     *       qui l'a remplacée prend le relais.</li>
     * </ul>
     * Sert à résorber les doublons créés avant la déduplication, ou par deux imports qui auraient créé le même
     * élément chacun de leur côté.
     */
    public List<Long> findDuplicateActivityIds(Long sourceId) {
        return jdbcTemplate.queryForList(
                "SELECT mine.id FROM activity mine "
                        + "JOIN import_fingerprint mf ON mf.source_id = mine.source_id "
                        + "AND mf.import_key = mine.import_key "
                        + "WHERE mine.source_id = ? AND mine.status <> 'ARCHIVED' AND mf.external_id IS NOT NULL "
                        + "AND EXISTS (" + LIVE_ACTIVITY_OF_ANOTHER_SOURCE
                        + " WHERE f.external_id = mf.external_id AND f.source_id <> mine.source_id "
                        + " AND a.id < mine.id AND " + String.format(SAME_ELEMENT_AS, "mine.title", "mine.start_date")
                        + ") "
                        + "UNION "
                        + "SELECT a.id FROM activity a "
                        + "JOIN import_fingerprint f ON f.source_id = a.source_id AND f.import_key = a.import_key "
                        + "JOIN source s ON s.id = a.source_id AND s.status <> 'ACTIVE' "
                        + "WHERE a.source_id <> ? AND a.status <> 'ARCHIVED' AND f.external_id IS NOT NULL "
                        + "AND EXISTS (SELECT 1 FROM activity mine JOIN import_fingerprint mf "
                        + " ON mf.source_id = mine.source_id AND mf.import_key = mine.import_key "
                        + " WHERE mine.source_id = ? AND mine.status <> 'ARCHIVED' AND mf.external_id = f.external_id"
                        + " AND " + String.format(SAME_ELEMENT_AS, "mine.title", "mine.start_date") + ") "
                        + "ORDER BY 1",
                Long.class, sourceId, sourceId, sourceId);
    }

    /** Oublie l'empreinte d'un élément : il sera réimporté en entier au prochain passage. */
    public void forget(Long sourceId, String importKey) {
        jdbcTemplate.update(
                "DELETE FROM import_fingerprint WHERE source_id = ? AND import_key = ?", sourceId, importKey);
    }

}
