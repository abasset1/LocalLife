package com.locallife.backend.category.infrastructure;

import com.locallife.backend.category.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Category — uniquement des opérations de lecture. Les catégories
 * V1 sont créées par migration ({@code V32__refactor_category_referential.sql}) ;
 * l'administration en ajoute ou en désactive via {@link CategoryAdminRepository},
 * seul point d'écriture.
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
