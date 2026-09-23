import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { saveToken } from "../auth/authStorage";
import GoogleSignInButton from "../components/GoogleSignInButton";

interface ApiErrorBody {
    message: string;
}

function LoginPage() {
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [needsVerification, setNeedsVerification] = useState(false);
    const [resendMessage, setResendMessage] = useState<string | null>(null);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setNeedsVerification(false);
        setResendMessage(null);
        setIsSubmitting(true);

        try {
            const response = await fetch("/api/v1/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email, password }),
            });

            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setError(body?.message ?? "Email ou mot de passe incorrect.");
                // 403 : identifiants corrects mais adresse email pas encore confirmée.
                setNeedsVerification(response.status === 403);
                return;
            }

            const { token } = (await response.json()) as { token: string };
            saveToken(token);
            navigate("/");
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSubmitting(false);
        }
    }

    async function handleResendVerification() {
        setResendMessage(null);
        try {
            const response = await fetch("/api/v1/auth/resend-verification", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email }),
            });
            const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
            setResendMessage(
                body?.message ??
                    (response.ok
                        ? "Un nouvel email de confirmation vient d'être envoyé."
                        : "Impossible de renvoyer l'email pour le moment.")
            );
        } catch {
            setResendMessage("Impossible de contacter le serveur, réessaie plus tard.");
        }
    }

    return (
        <main className="auth-shell">
            <form className="auth-form" onSubmit={(event) => void handleSubmit(event)}>
                <h1>Connexion</h1>
                <input
                    aria-label="Email"
                    autoComplete="email"
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="Email"
                    required
                    type="email"
                    value={email}
                />
                <input
                    aria-label="Mot de passe"
                    autoComplete="current-password"
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="Mot de passe"
                    required
                    type="password"
                    value={password}
                />
                <button disabled={isSubmitting} type="submit">
                    Se connecter
                </button>
                {error && <span className="form-message form-message-error">{error}</span>}
                {needsVerification && (
                    <button onClick={() => void handleResendVerification()} type="button">
                        Renvoyer l'email de confirmation
                    </button>
                )}
                {resendMessage && <span className="form-message form-message-success">{resendMessage}</span>}
                <div className="auth-divider">
                    <span>ou</span>
                </div>
                <GoogleSignInButton onError={setError} />
                <p className="auth-switch">
                    <Link to="/forgot-password">Mot de passe oublié ?</Link>
                </p>
                <p className="auth-switch">
                    Pas encore de compte ? <Link to="/register">Créer un compte</Link>
                </p>
            </form>
        </main>
    );
}

export default LoginPage;
