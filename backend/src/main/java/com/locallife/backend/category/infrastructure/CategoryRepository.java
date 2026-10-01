package com.locallife.backend.category.infrastructure;

import com.locallife.backend.category.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Category — uniquement des opérations de lecture (CAT-001 ne
 * demande pas de CRUD : les 9 catégories V1 sont créées une fois pour
 * toutes par migration, pas par une API d'écriture — voir
 * {@code V32__refactor_category_referential.sql}).
 *
 * Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository} : aucune méthode d'écriture n'est disponible, même
 * par erreur — même convention que {@code SourceRepository} et
 * {@code UserRepository}.
 */
public interface CategoryRepository extends Repository<Category, Long> {

    List<Category> findAll();

    Optional<Category> findById(Long id);

    /**
     * Recherche par code métier (CAT-001 : "le CODE est la véritable
     * identité métier"). Destinée aux futurs tickets qui devront résoudre
     * un code (ex. {@code CULTURE}) reçu depuis l'API ou l'import vers la
     * catégorie correspondante, sans dépendre de l'id technique.
     */
    Optional<Category> findByCode(String code);

}
