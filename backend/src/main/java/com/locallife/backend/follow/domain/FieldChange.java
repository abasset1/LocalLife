package com.locallife.backend.follow.domain;

/**
 * Champ modifié d'une activité, d'un lieu ou d'un food truck suivi ({@code follow_change.details}, V94) : nom de la
 * colonne ({@code start_date}, {@code address}, {@code title}…), ancienne et nouvelle valeur en texte. Valeurs nulles
 * pour un texte long (description) : seul le fait qu'il a changé est noté.
 */
public record FieldChange(String field, String oldValue, String newValue) {
}
