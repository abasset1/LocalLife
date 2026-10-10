package com.locallife.backend.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (comme
 * ActivityControllerIntegrationTest). Chaque test est englobé dans une
 * transaction annulée à la fin (@Transactional) pour ne pas polluer la
 * base : ce repository n'expose volontairement pas de méthode de
 * suppression.
 */
@SpringBootTest
@Transactional
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_ShouldPersistUser_AndFindById_ShouldReturnIt() {
        User saved = userRepository.save(
                new User(null, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now(), true));

        assertThat(saved.id()).isNotNull();

        Optional<User> found = userRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo("alice");
        assertThat(found.get().email()).isEqualTo("alice@example.com");
    }

    @Test
    void findById_ShouldReturnEmpty_WhenNotExists() {
        Optional<User> found = userRepository.findById(999_999L);

        assertThat(found).isEmpty();
    }

    @Test
    void findByEmail_ShouldReturnUser_WhenExists() {
        userRepository.save(new User(null, "bob", "bob@example.com", "hash", Role.USER, LocalDateTime.now(), true));

        Optional<User> found = userRepository.findByEmail("bob@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo("bob");
    }

    @Test
    void findByEmail_ShouldReturnEmpty_WhenNotExists() {
        Optional<User> found = userRepository.findByEmail("nobody@example.com");

        assertThat(found).isEmpty();
    }


    // --- LS-000 : nom d'utilisateur unique, garanti par la base ---

    @Test
    void findByUsernameIgnoreCase_ShouldMatchWhateverTheCase() {
        userRepository.save(new User(null, "Ls000-Carol", "ls000-carol@example.com", "hash", Role.USER,
                LocalDateTime.now(), true));

        assertThat(userRepository.findByUsernameIgnoreCase("ls000-CAROL"))
                .map(User::username).contains("Ls000-Carol");
        assertThat(userRepository.findByUsernameIgnoreCase("ls000-dave")).isEmpty();
    }

    @Test
    void save_ShouldBeRejectedByDatabase_WhenUsernameTakenWithAnotherCase() {
        userRepository.save(new User(null, "ls000-erin", "ls000-erin@example.com", "hash", Role.USER,
                LocalDateTime.now(), true));

        assertThatThrownBy(() -> userRepository.save(new User(null, "LS000-ERIN", "ls000-erin2@example.com", "hash",
                Role.USER, LocalDateTime.now(), true)))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("users_username_lower_key");
    }

    @Test
    void save_ShouldBeRejectedByDatabase_WhenUsernameMalformed() {
        assertThatThrownBy(() -> userRepository.save(new User(null, "jean dupont", "ls000-jean@example.com", "hash",
                Role.USER, LocalDateTime.now(), true)))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("users_username_format");
    }
}
