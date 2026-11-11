package com.locallife.backend.follow.domain;

/**
 * Ce que l'on suit : une activité, un lieu permanent ({@code location} portant un {@code place_type}) ou un food
 * truck.
 */
public enum FollowTargetType {
    ACTIVITY,
    PLACE,
    FOOD_TRUCK
}
