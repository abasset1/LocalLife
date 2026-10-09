package com.locallife.backend.user.infrastructure;

import com.locallife.backend.user.domain.UserPreferences;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accès aux tables {@code user_preferences}/{@code user_preference_category}
 * (V51). {@link JdbcTemplate} plutôt qu'un repository Spring Data JDBC :
 * la clé primaire est {@code user_id}, fournie par l'appelant — Spring Data
 * JDBC traiterait alors tout {@code save} comme une mise à jour, y compris
 * le premier (aucune ligne insérée). Un {@code INSERT ... ON CONFLICT} règle
 * le cas en une requête.
 */
@Repository
public class UserPreferencesRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserPreferencesRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserPreferences> findByUserId(Long userId) {
        List<String> categoryCodes = jdbcTemplate.queryForList(
                "SELECT category_code FROM user_preference_category WHERE user_id = ? ORDER BY category_code",
                String.class, userId);
        return jdbcTemplate.query(
                "SELECT default_city, default_latitude, default_longitude, default_radius_km "
                        + "FROM user_preferences WHERE user_id = ?",
                (resultSet, rowNumber) -> new UserPreferences(
                        userId,
                        resultSet.getString("default_city"),
                        resultSet.getObject("default_latitude", Double.class),
                        resultSet.getObject("default_longitude", Double.class),
                        resultSet.getObject("default_radius_km", Integer.class),
                        categoryCodes),
                userId).stream().findFirst();
    }

    /**
     * Remplace intégralement les préférences de l'utilisateur (ligne
     * principale et catégories). À appeler dans une transaction (voir
     * {@code UserPreferencesService#updatePreferences}).
     */
    public void save(UserPreferences preferences) {
        jdbcTemplate.update(
                "INSERT INTO user_preferences "
                        + "(user_id, default_city, default_latitude, default_longitude, default_radius_km, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, now()) "
                        + "ON CONFLICT (user_id) DO UPDATE SET default_city = EXCLUDED.default_city, "
                        + "default_latitude = EXCLUDED.default_latitude, "
                        + "default_longitude = EXCLUDED.default_longitude, "
                        + "default_radius_km = EXCLUDED.default_radius_km, updated_at = now()",
                preferences.userId(), preferences.defaultCity(), preferences.defaultLatitude(),
                preferences.defaultLongitude(), preferences.defaultRadiusKm());
        jdbcTemplate.update("DELETE FROM user_preference_category WHERE user_id = ?", preferences.userId());
        jdbcTemplate.batchUpdate(
                "INSERT INTO user_preference_category (user_id, category_code) VALUES (?, ?)",
                preferences.defaultCategoryCodes().stream()
                        .map(code -> new Object[] {preferences.userId(), code})
                        .toList());
    }

}
