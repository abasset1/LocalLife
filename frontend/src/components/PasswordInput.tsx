import { useEffect, useRef, useState } from "react";
import type { InputHTMLAttributes } from "react";

type PasswordInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type">;

/**
 * Champ mot de passe avec un bouton « Afficher » / « Masquer » qui rend la saisie lisible, pour
 * vérifier ce que l'on tape (connexion, création de compte). Le texte est masqué de nouveau à
 * l'envoi du formulaire, pour que le navigateur le reconnaisse toujours comme un mot de passe.
 */
function PasswordInput(props: PasswordInputProps) {
    const [isVisible, setIsVisible] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        const form = inputRef.current?.form;
        if (!form) {
            return;
        }
        const hide = () => setIsVisible(false);
        form.addEventListener("submit", hide);
        return () => form.removeEventListener("submit", hide);
    }, []);

    return (
        <div className="password-field">
            <input {...props} ref={inputRef} type={isVisible ? "text" : "password"} />
            <button
                aria-label={isVisible ? "Masquer le mot de passe" : "Afficher le mot de passe"}
                aria-pressed={isVisible}
                className="password-toggle"
                onClick={() => setIsVisible((value) => !value)}
                type="button"
            >
                {isVisible ? "Masquer" : "Afficher"}
            </button>
        </div>
    );
}

export default PasswordInput;
