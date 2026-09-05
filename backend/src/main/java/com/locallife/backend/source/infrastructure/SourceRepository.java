package com.locallife.backend.source.infrastructure;

import com.locallife.backend.source.domain.Source;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Source — création, consultation, mise à jour et suppression
 * (LL-5002, étendu en LL-EF-005 pour la gestion des agendas depuis
 * l'interface d'administration : {@code save} sert aussi de mise à jour,
 * comme les autres repositories de ce projet, et {@code deleteById} a été
 * ajouté — décision explicite d'Alex pour ce ticket, alors qu'aucun
 * mécanisme de suppression n'était prévu jusqu'ici, voir
 * {@code SOURCE_CONTRACT.md}).
 *
 * Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository} : seules les méthodes explicitement listées ici
 * sont disponibles, comme {@code CategoryRepository} et
 * {@code UserRepository}.
 */
public interface SourceRepository extends Repository<Source, Long> {

    Source save(Source source);

    List<Source> findAll();

    Optional<Source> findById(Long id);

    /**
     * Suppression (LL-EF-005) : la garde métier (interdiction de supprimer
     * la source réservée {@code MANUAL}, détachement des activités liées
     * vers cette même source de repli) est appliquée par
     * {@code SourceService#deleteSource}, pas ici — ce repository reste
     * une couche d'accès aux données sans logique métier.
     */
    void deleteById(Long id);

    /**
     * Recherche par nom (LL-5008) : conservée comme méthode
     * généraliste de recherche par attribut unique, au même titre que
     * {@link #findByType(String)}, même si son unique consommateur
     * historique ({@code SourceService#findOrCreateByName}) a disparu en
     * LL-EF-005 (la configuration des agendas ne se fait plus en
     * associant un {@code Collector} à une source par son nom au moment
     * de la collecte, mais en configurant directement la source en base,
     * voir {@code ImportService}).
     */
    Optional<Source> findByName(String name);

    /**
     * Recherche par type (LL-5008) : sert à retrouver la source réservée
     * {@code MANUAL} (une seule ligne, insérée par la migration
     * {@code V8__create_source_table.sql}) sans dépendre de son libellé
     * exact — utilisé par {@code ActivityService#createActivity} et, depuis
     * LL-EF-005, par {@code SourceService#deleteSource} (source de repli
     * pour les activités détachées d'une source supprimée).
     */
    Optional<Source> findByType(String type);

}
