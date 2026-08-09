package com.locallife.backend.user.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHashingServiceTest {

    private final PasswordHashingService passwordHashingService = new PasswordHashingService();

    @Test
    void hash_ShouldNotReturnPlainTextPassword() {
        String hashed = passwordHashingService.hash("motDePasse123");

        assertNotEquals("motDePasse123", hashed);
    }

    @Test
    void matches_ShouldReturnTrue_WhenPasswordIsCorrect() {
        String hashed = passwordHashingService.hash("motDePasse123");

        assertTrue(passwordHashingService.matches("motDePasse123", hashed));
    }

    @Test
    void matches_ShouldReturnFalse_WhenPasswordIsIncorrect() {
        String hashed = passwordHashingService.hash("motDePasse123");

        assertFalse(passwordHashingService.matches("mauvaisMotDePasse", hashed));
    }

}
