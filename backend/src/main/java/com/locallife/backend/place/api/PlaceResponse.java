package com.locallife.backend.place.api;

import com.locallife.backend.place.domain.Place;

/** Lieu publié, pour la carte et la liste (ADR-0011, phase B). */
public record PlaceResponse(
        Long id, String name, String placeType, String address, String postalCode, String city, Double latitude,
        Double longitude, String description, String website, String coordinatePrecision) {

    static PlaceResponse from(Place place) {
        return new PlaceResponse(place.id(), place.name(), place.placeType(), place.address(), place.postalCode(),
                place.city(), place.latitude(), place.longitude(), place.description(), place.website(),
                place.coordinatePrecision().name());
    }
}
