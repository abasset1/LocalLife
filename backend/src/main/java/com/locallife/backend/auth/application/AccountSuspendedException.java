package com.locallife.backend.auth.application;

/**
 * Connexion refusée à un compte suspendu (ACC-02). Levée seulement après vérification des identifiants (mot de
 * passe ou jeton Google) : elle ne permet pas d'énumérer les comptes. Réponse {@code 423 Locked}, distincte du
 * {@code 403} « adresse non confirmée » pour que les clients n'y proposent pas le renvoi du lien de confirmation.
 */
public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException() {
        super("Ce compte est suspendu. Contacte l'équipe LocalLife pour en savoir plus.");
    }
}
