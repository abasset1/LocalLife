import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { saveToken } from "../auth/authStorage";

interface ApiErrorBody {
    message: string;
}

function LoginPage() {
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
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
                <p className="auth-switch">
                    Pas encore de compte ? <Link to="/register">Créer un compte</Link>
                </p>
            </form>
        </main>
    );
}

export default LoginPage;
