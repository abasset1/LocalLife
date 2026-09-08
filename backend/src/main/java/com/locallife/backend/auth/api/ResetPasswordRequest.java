package com.locallife.backend.auth.api;

/**
 * DTO pour la requête de réinitialisation de mot de passe (LL-EF-007) :
 * {@code token} est le token en clair reçu par email, {@code newPassword}
 * le nouveau mot de passe choisi par l'utilisateur.
 */
public record ResetPasswordRequest(String token, String newPassword) {
}
