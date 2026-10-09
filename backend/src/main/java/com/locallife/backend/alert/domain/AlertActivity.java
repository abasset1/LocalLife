package com.locallife.backend.alert.domain;

import java.time.LocalDateTime;

/** Activité nouvellement publiée correspondant à une alerte : le strict nécessaire à l'email. */
public record AlertActivity(Long id, String title, String city, LocalDateTime startDate) {
}
