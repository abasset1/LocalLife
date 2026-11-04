package com.locallife.backend.foodtruck.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Type de cuisine d'un food truck : liste fermée, pour filtrer la carte et la liste. La spécialité reste saisie
 * librement ({@code category}). Libellés côté clients (web et mobile).
 */
public enum FoodTruckCuisine {
    BURGER, PIZZA, ASIAN, MEXICAN, CREPES, SANDWICH, FRENCH, ITALIAN, WORLD, VEGETARIAN, DESSERT, DRINKS, OTHER;

    /** Code reçu d'un client ; vide = aucun type, inconnu = {@link IllegalArgumentException}. */
    public static Optional<FoodTruckCuisine> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
    }
}
