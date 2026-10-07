package com.locallife.backend.activityclassification.infrastructure;

import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Lecture seule des mots-clés métier (comme {@code CategoryRepository}) : ils sont gérés par
 * migration, pas par une API d'écriture.
 */
public interface CategoryKeywordRepository extends Repository<CategoryKeyword, Long> {

    /** Ordre stable (id) : les candidats d'un conflit sont toujours listés dans le même ordre. */
    List<CategoryKeyword> findByActiveTrueOrderByIdAsc();

}
