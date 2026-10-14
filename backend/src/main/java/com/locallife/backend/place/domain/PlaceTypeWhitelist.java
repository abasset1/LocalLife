package com.locallife.backend.place.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Liste blanche explicite des types de lieux DATAtourisme importés (ADR-0011, § 7.2.3 et décisions du 14/10/2026 :
 * groupe 1, 46 types ; groupe 2 exclu). Aucun type n'est retenu implicitement.
 *
 * <p>Chaque lieu DATAtourisme porte tous ses types, parents compris (un musée porte aussi {@code CulturalSite},
 * {@code PlaceOfInterest}…). Règle :
 * <ol>
 *   <li>un lieu portant un type exclu (hébergement, restauration, dégustation) n'est jamais retenu ;</li>
 *   <li>un type précis de la liste ({@link #SPECIFIC_TYPES}) suffit ; le premier dans l'ordre de la liste devient
 *       le type du lieu ;</li>
 *   <li>un type général de la liste ({@link #GENERAL_TYPES}, parents d'autres types) n'est retenu que si le lieu
 *       ne porte aucun autre type que des types généraux : « site culturel » sans sous-type. Un village
 *       ({@code CityHeritage}) ou un commerce, qui portent aussi {@code CulturalSite}, ne sont donc pas
 *       retenus.</li>
 * </ol>
 * Mesuré sur l'export PACA du 13/10/2026 : 418 lieux retenus dans le Vaucluse, 4 773 en PACA
 * ({@code collectors/verification/place_types_report.py}).
 */
public final class PlaceTypeWhitelist {

    /** Types précis retenus, dans l'ordre de priorité pour choisir le type d'un lieu. */
    public static final List<String> SPECIFIC_TYPES = List.of(
            // Culture
            "Museum", "Theater", "Cinema", "Library", "InterpretationCentre",
            // Patrimoine
            "Castle", "Palace", "Tower", "Dungeon", "Fort", "Bridge", "Aqueduct", "Ruins", "RomanPath", "Mill",
            "WashHouse", "CoveredMarket", "IndustrialSite", "Mine", "RemembranceSite", "ParkAndGarden",
            // Patrimoine religieux
            "Church", "Chapel", "Abbey", "Cathedral", "Basilica", "Collegiate", "Convent", "Monastery", "Synagogue",
            "Temple", "Commanderie",
            // Équipements sportifs et de plein air
            "SwimmingPool", "Stadium", "TennisComplex", "BoulesPitch", "TrackRollerOrSkateBoard", "FitnessPath",
            "PlayArea", "Beach", "PointOfView");

    /** Types généraux retenus seulement sans autre type, du plus précis au plus général. */
    public static final List<String> GENERAL_TYPES = List.of(
            "TechnicalHeritage", "ArcheologicalSite", "DefenceSite", "NaturalHeritage", "CulturalSite");

    /** Un lieu portant l'un de ces types n'est jamais retenu. */
    public static final Set<String> EXCLUDED_TYPES = Set.of("Accommodation", "FoodEstablishment", "TastingProvider");

    /** Types communs à tous les lieux, sans valeur de classement. */
    private static final Set<String> ROOT_TYPES = Set.of("PointOfInterest", "PlaceOfInterest");

    private PlaceTypeWhitelist() {
    }

    /**
     * Type retenu pour un lieu, d'après tous ses types DATAtourisme (noms locaux, sans l'espace de noms), ou vide
     * si le lieu n'est pas à importer.
     */
    public static Optional<String> placeType(Set<String> types) {
        if (types.stream().anyMatch(EXCLUDED_TYPES::contains)) {
            return Optional.empty();
        }
        for (String type : SPECIFIC_TYPES) {
            if (types.contains(type)) {
                return Optional.of(type);
            }
        }
        boolean onlyGeneralTypes = types.stream()
                .allMatch(type -> ROOT_TYPES.contains(type) || GENERAL_TYPES.contains(type));
        if (!onlyGeneralTypes) {
            return Optional.empty();
        }
        return GENERAL_TYPES.stream().filter(types::contains).findFirst();
    }

    public static boolean isWhitelisted(Set<String> types) {
        return placeType(types).isPresent();
    }
}
