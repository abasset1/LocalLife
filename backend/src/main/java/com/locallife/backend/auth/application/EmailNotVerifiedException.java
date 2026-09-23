package com.locallife.backend.auth.application;

/**
 * Levée à la connexion quand les identifiants sont corrects mais que
 * l'adresse email du compte n'a pas encore été confirmée.
 *
 * <p>Volontairement distincte de {@link IllegalArgumentException} (traduite
 * en 401 par {@code AuthController}) : le contrôleur la traduit en 403 pour
 * que le frontend puisse proposer le renvoi du lien de confirmation. Elle
 * n'est levée qu'après vérification du mot de passe, ce qui ne permet donc
 * pas d'énumérer les comptes existants.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Vous devez confirmer votre adresse email avant de vous connecter. "
                + "Consultez votre boîte mail ou demandez un nouveau lien.");
    }

}
