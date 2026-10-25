package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.ImportedActivityContact;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accès à {@code imported_activity_contact_notice} (V75), en {@link JdbcTemplate} : la recherche des contacts à
 * prévenir croise activités, liens, comptes et avis déjà envoyés.
 */
@Repository
public class ImportedActivityContactNoticeRepository {

    /**
     * Contacts à prévenir : adresse d'un lien {@code EMAIL} d'une activité
     * <ul>
     *   <li>importée ({@code import_key} renseignée : jamais une contribution manuelle), publiée, créée depuis moins
     *       de {@code ?} jours (les activités importées avant la mise en place de l'avis ne sont pas rattrapées) et
     *       pas encore terminée ;</li>
     *   <li>jamais prévenue pour cette activité ;</li>
     *   <li>dont l'adresse n'a reçu aucun avis depuis {@code ?} jours (un organisateur aux nombreux événements ne
     *       reçoit pas un message par import) ;</li>
     *   <li>dont l'adresse n'est pas celle d'un compte professionnel ou administrateur.</li>
     * </ul>
     * Triés par adresse, pour regrouper les activités d'un même contact dans un seul message.
     */
    private static final String PENDING_CONTACTS = """
            SELECT DISTINCT a.id, a.title, a.city, a.start_date, lower(trim(l.value)) AS email
            FROM activity a
            JOIN activity_link l ON l.activity_id = a.id AND l.type = 'EMAIL'
            WHERE a.import_key IS NOT NULL
              AND a.status = 'PUBLISHED'
              AND a.created_at > now() - make_interval(days => ?)
              AND (a.end_date IS NULL OR a.end_date >= LOCALTIMESTAMP)
              AND NOT EXISTS (
                  SELECT 1 FROM imported_activity_contact_notice n
                  WHERE n.activity_id = a.id AND n.email = lower(trim(l.value)))
              AND NOT EXISTS (
                  SELECT 1 FROM imported_activity_contact_notice n
                  WHERE n.email = lower(trim(l.value)) AND n.sent_at > now() - make_interval(days => ?))
              AND NOT EXISTS (
                  SELECT 1 FROM users u
                  WHERE lower(u.email) = lower(trim(l.value)) AND u.role IN ('PRO', 'ADMIN'))
            ORDER BY email, a.start_date NULLS LAST, a.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ImportedActivityContactNoticeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ImportedActivityContact> findPendingContacts(int maxAgeDays, int cooldownDays, int limit) {
        return jdbcTemplate.query(PENDING_CONTACTS, (resultSet, rowNum) -> {
            Timestamp startDate = resultSet.getTimestamp("start_date");
            return new ImportedActivityContact(resultSet.getLong("id"), resultSet.getString("title"),
                    resultSet.getString("city"), startDate == null ? null : startDate.toLocalDateTime(),
                    resultSet.getString("email"));
        }, maxAgeDays, cooldownDays, limit);
    }

    /** Enregistre l'avis envoyé à {@code email} pour chacune des activités ; sans effet sur un avis déjà connu. */
    public void markSent(String email, List<Long> activityIds) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO imported_activity_contact_notice (activity_id, email) VALUES (?, ?) "
                        + "ON CONFLICT DO NOTHING",
                activityIds.stream().map(id -> new Object[] {id, email}).toList());
    }
}
