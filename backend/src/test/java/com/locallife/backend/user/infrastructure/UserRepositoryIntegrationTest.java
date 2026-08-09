package com.locallife.backend.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
                new User(null, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now()));

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
        userRepository.save(new User(null, "bob", "bob@example.com", "hash", Role.USER, LocalDateTime.now()));

        Optional<User> found = userRepository.findByEmail("bob@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo("bob");
    }

    @Test
    void findByEmail_ShouldReturnEmpty_WhenNotExists() {
        Optional<User> found = userRepository.findByEmail("nobody@example.com");

        assertThat(found).isEmpty();
    }

}
