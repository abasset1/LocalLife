package com.locallife.backend.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.domain.UserPreferences;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle, chaque test annulé en fin de
 * transaction (même principe que {@link UserRepositoryIntegrationTest}).
 */
@SpringBootTest
@Transactional
class UserPreferencesRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPreferencesRepository userPreferencesRepository;

    private Long createUser() {
        return userRepository.save(new User(
                null, "prefs", "prefs-repository@example.com", "hash", Role.USER, LocalDateTime.now(), true)).id();
    }

    @Test
    void findByUserId_ShouldReturnEmpty_WhenNothingSaved() {
        assertThat(userPreferencesRepository.findByUserId(createUser())).isEmpty();
    }

    @Test
    void save_ShouldInsertThenReplacePreferences() {
        Long userId = createUser();

        userPreferencesRepository.save(
                new UserPreferences(userId, "Lyon", 45.76, 4.83, 20, List.of("CULTURE", "SPORT")));
        assertThat(userPreferencesRepository.findByUserId(userId)).contains(
                new UserPreferences(userId, "Lyon", 45.76, 4.83, 20, List.of("CULTURE", "SPORT")));

        userPreferencesRepository.save(new UserPreferences(userId, null, null, null, null, List.of("NATURE")));
        assertThat(userPreferencesRepository.findByUserId(userId)).contains(
                new UserPreferences(userId, null, null, null, null, List.of("NATURE")));
    }

}
