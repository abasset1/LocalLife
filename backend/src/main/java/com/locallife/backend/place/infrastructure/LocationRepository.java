package com.locallife.backend.place.infrastructure;

import com.locallife.backend.place.domain.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Location (LL-11001). Même patron que
 * {@code ActivityRepository} : étend {@link Repository} (interface
 * marqueur) plutôt que {@code CrudRepository}, pour n'exposer que les
 * méthodes explicitement listées ici. Pas de recherche géographique
 * ({@code findWithinRadius}/{@code findWithinBounds} côté
 * {@code ActivityRepository}) à ce stade : hors périmètre de LL-11001
 * (« créer le modèle »), à ajouter par un futur ticket exploitant
 * réellement {@code geo_location} (ex. LL-11012, recherche sur les
 * occurrences).
 */
public interface LocationRepository extends Repository<Location, Long> {

    List<Location> findAll();

    Optional<Location> findById(Long id);

    /**
     * Retrouve un lieu déjà connu par adresse complète (LL-11011) : sert
     * {@code LocationService#findOrCreate}, pour éviter de dupliquer un
     * lieu partagé (voir la javadoc de {@code Location}) d'un réimport à
     * l'autre. Volontairement restreinte au cas où les trois champs sont
     * renseignés (comparaison d'égalité stricte, y compris sur {@code
     * city}/{@code postalCode} potentiellement {@code null} — une
     * colonne {@code NULL} ne correspond jamais à un paramètre {@code
     * NULL} en SQL, cette méthode n'est donc appelée par {@code
     * findOrCreate} que lorsque les trois valeurs sont non nulles).
     */
    /*
     * « First ... OrderByIdAsc » sur les deux recherches ci-dessous : aucune contrainte d'unicité ne
     * garantit un seul lieu par adresse ou par coordonnées (deux lieux d'un même bâtiment, lieu créé deux
     * fois par des imports simultanés). Avec un Optional simple, un doublon faisait échouer la recherche,
     * donc tout l'enrichissement des activités de ce lieu, à chaque import. Le plus ancien est retenu.
     */
    Optional<Location> findFirstByAddressAndCityAndPostalCodeOrderByIdAsc(
            String address, String city, String postalCode);

    /**
     * Retrouve un lieu déjà connu par coordonnées exactes (LL-11011),
     * repli de {@code findOrCreate} quand aucune adresse complète n'est
     * disponible — comparaison d'égalité stricte, suffisante pour
     * reconnaître un même lieu réimporté depuis la même source (mêmes
     * coordonnées à chaque collecte).
     */
    Optional<Location> findFirstByLatitudeAndLongitudeOrderByIdAsc(Double latitude, Double longitude);

    Location save(Location location);
}
