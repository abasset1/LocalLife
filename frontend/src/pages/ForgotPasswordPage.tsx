import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";

function ForgotPasswordPage() {
    const [email, setEmail] = useState("");
    const [message, setMessage] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setMessage(null);
        setIsSubmitting(true);

        try {
            const response = await fetch("/api/v1/auth/forgot-password", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email }),
            });

            // Le backend renvoie toujours le même message générique, que le
            // compte existe ou non (LL-EF-007 : pas d'énumération des
            // comptes) — on l'affiche tel quel, sans distinguer les cas.
            const body = (await response.json().catch(() => null)) as { message: string } | null;

            if (!response.ok) {
                setError(body?.message ?? "Impossible de traiter la demande pour le moment.");
                return;
            }

            setMessage(
                body?.message ??
                    "Si un compte existe pour cette adresse, un email de réinitialisation vient d'être envoyé."
            );
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="auth-shell">
            <form className="auth-form" onSubmit={(event) => void handleSubmit(event)}>
                <h1>Mot de passe oublié</h1>
                {!message && (
                    <>
                        <p>Renseigne ton adresse email : si un compte existe, tu recevras un lien de réinitialisation.</p>
                        <input
                            aria-label="Email"
                            autoComplete="email"
                            onChange={(event) => setEmail(event.target.value)}
                            placeholder="Email"
                            required
                            type="email"
                            value={email}
                        />
                        <button disabled={isSubmitting} type="submit">
                            Envoyer le lien de réinitialisation
                        </button>
                    </>
                )}
                {message && <span className="form-message form-message-success">{message}</span>}
                {error && <span className="form-message form-message-error">{error}</span>}
                <p className="auth-switch">
                    <Link to="/login">Retour à la connexion</Link>
                </p>
            </form>
        </main>
    );
}

export default ForgotPasswordPage;
