package com.locallife.backend.source.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.source.domain.Source;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (comme
 * UserRepositoryIntegrationTest). Chaque test est englobé dans une
 * transaction annulée à la fin (@Transactional) pour ne pas polluer la
 * base : ce repository n'expose volontairement pas de méthode de
 * suppression.
 */
@SpringBootTest
@Transactional
class SourceRepositoryIntegrationTest {

    @Autowired
    private SourceRepository sourceRepository;

    @Test
    void save_ShouldPersistSource_AndFindById_ShouldReturnIt() {
        Source saved = sourceRepository.save(
                new Source(null, "OpenAgenda Marseille", "API", "https://api.openagenda.com", "ACTIVE", null));

        assertThat(saved.id()).isNotNull();

        Optional<Source> found = sourceRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("OpenAgenda Marseille");
        assertThat(found.get().type()).isEqualTo("API");
        assertThat(found.get().status()).isEqualTo("ACTIVE");
    }

    @Test
    void findById_ShouldReturnEmpty_WhenNotExists() {
        Optional<Source> found = sourceRepository.findById(999_999L);

        assertThat(found).isEmpty();
    }

    @Test
    void findAll_ShouldIncludeReservedManualSource_FromMigration() {
        List<Source> sources = sourceRepository.findAll();

        assertThat(sources)
                .anySatisfy(source -> {
                    assertThat(source.type()).isEqualTo("MANUAL");
                    assertThat(source.status()).isEqualTo("ACTIVE");
                });
    }

}
