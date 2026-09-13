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

    /**
     * Réutilise un lieu déjà connu plutôt que d'en créer un nouveau
     * (LL-11011) : {@code Location} est explicitement partageable (voir
     * sa javadoc, « un même lieu peut être partagé par plusieurs
     * activités ») — sans ce garde-fou, {@code ImportService}
     * dupliquerait une ligne {@code location} à chaque réimport d'une
     * même activité. Cherche d'abord par adresse complète ({@code
     * address}/{@code city}/{@code postalCode} tous renseignés), puis
     * par coordonnées exactes ; à défaut de correspondance, délègue à
     * {@link #create}.
     */
    public Location findOrCreate(Location candidate) {
        return findMatching(candidate).orElseGet(() -> create(candidate));
    }

    private Optional<Location> findMatching(Location candidate) {
        boolean hasFullAddress = hasText(candidate.address())
                && hasText(candidate.city())
                && hasText(candidate.postalCode());
        if (hasFullAddress) {
            return locationRepository.findByAddressAndCityAndPostalCode(
                    candidate.address(), candidate.city(), candidate.postalCode());
        }
        if (candidate.latitude() != null && candidate.longitude() != null) {
            return locationRepository.findByLatitudeAndLongitude(candidate.latitude(), candidate.longitude());
        }
        return Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
