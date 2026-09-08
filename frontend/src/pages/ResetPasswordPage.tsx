import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";

interface ApiErrorBody {
    message: string;
}

function ResetPasswordPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";

    const [newPassword, setNewPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        try {
            const response = await fetch("/api/v1/auth/reset-password", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ token, newPassword }),
            });

            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setError(body?.message ?? "Impossible de réinitialiser le mot de passe.");
                return;
            }

            // Le token vient d'être invalidé côté backend (usage unique) :
            // on redirige vers la connexion plutôt que de rester sur ce
            // formulaire, qui ne peut plus être soumis avec succès.
            navigate("/login");
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSubmitting(false);
        }
    }

    if (!token) {
        return (
            <main className="auth-shell">
                <div className="auth-form">
                    <h1>Lien invalide</h1>
                    <span className="form-message form-message-error">
                        Ce lien de réinitialisation est incomplet. Fais une nouvelle demande.
                    </span>
                    <p className="auth-switch">
                        <Link to="/forgot-password">Demander un nouveau lien</Link>
                    </p>
                </div>
            </main>
        );
    }

    return (
        <main className="auth-shell">
            <form className="auth-form" onSubmit={(event) => void handleSubmit(event)}>
                <h1>Nouveau mot de passe</h1>
                <input
                    aria-label="Nouveau mot de passe"
                    autoComplete="new-password"
                    minLength={8}
                    onChange={(event) => setNewPassword(event.target.value)}
                    placeholder="Nouveau mot de passe (8 caractères minimum)"
                    required
                    type="password"
                    value={newPassword}
                />
                <button disabled={isSubmitting} type="submit">
                    Réinitialiser le mot de passe
                </button>
                {error && <span className="form-message form-message-error">{error}</span>}
                <p className="auth-switch">
                    <Link to="/login">Retour à la connexion</Link>
                </p>
            </form>
        </main>
    );
}

export default ResetPasswordPage;
