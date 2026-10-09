package com.locallife.backend.alert.domain;

/** Food truck nouvellement publié correspondant à une alerte : le strict nécessaire au message. */
public record AlertFoodTruck(Long id, String name, String category) {
}
