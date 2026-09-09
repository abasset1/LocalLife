package com.locallife.backend.place.application;

import com.locallife.backend.place.domain.Location;
import com.locallife.backend.place.infrastructure.LocationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Location (LL-11001). Délégation simple vers le repository, à
 * l'image de {@code ActivityService} pour ses méthodes de base — aucun
 * endpoint {@code api} n'est demandé par ce ticket (« créer le modèle »),
 * ce service n'est donc pour l'instant consommé que par les tests
 * d'intégration ; un futur ticket (schedules/import) l'appellera pour de
 * vrai.
 */
@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public List<Location> findAll() {
        return locationRepository.findAll();
    }

    public Optional<Location> findById(Long id) {
        return locationRepository.findById(id);
    }

    /**
     * Crée un lieu, avec le seul garde-fou explicitement demandé par le
     * ticket : « aucun lieu ne doit être créé artificiellement à partir de
     * données insuffisantes ». Traduit ici par l'exigence d'au moins une
     * donnée réellement identifiante — un nom, une adresse, ou des
     * coordonnées complètes — plutôt qu'une contrainte {@code NOT NULL}
     * en base sur un champ précis (aucun des champs de {@link Location}
     * n'est individuellement obligatoire selon la source, voir sa
     * javadoc) : un objet {@code Location} entièrement vide (aucun champ
     * renseigné) ne peut donc jamais être persisté par ce chemin.
     *
     * @throws IllegalArgumentException si aucune donnée identifiante n'est fournie
     */
    public Location create(Location location) {
        boolean hasName = location.name() != null && !location.name().isBlank();
        boolean hasAddress = location.address() != null && !location.address().isBlank();
        boolean hasCoordinates = location.latitude() != null && location.longitude() != null;
        if (!hasName && !hasAddress && !hasCoordinates) {
            throw new IllegalArgumentException(
                    "Un lieu doit avoir au moins un nom, une adresse ou des coordonnées.");
        }
        return locationRepository.save(location);
    }
}
