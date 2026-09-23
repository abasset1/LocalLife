import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import GoogleSignInButton from "../components/GoogleSignInButton";

interface ApiErrorBody {
    message: string;
}

function RegisterPage() {
    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        try {
            const response = await fetch("/api/v1/auth/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, email, password }),
            });

            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setError(body?.message ?? "Impossible de créer le compte.");
                return;
            }

            // Le backend ne renvoie pas de JWT à l'inscription (LL-3007) et le compte n'est
            // utilisable qu'après confirmation de l'adresse : on affiche la consigne plutôt
            // que de rediriger vers une connexion qui serait refusée.
            setRegisteredEmail(email.trim());
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSubmitting(false);
        }
    }

    if (registeredEmail) {
        return (
            <main className="auth-shell">
                <div className="auth-form">
                    <h1>Vérifie ta boîte mail</h1>
                    <span className="form-message form-message-success">
                        Compte créé. Un email de confirmation a été envoyé à {registeredEmail} : clique sur le
                        lien qu'il contient (valable 24 h) pour activer ton compte.
                    </span>
                    <p className="auth-switch">
                        <Link to="/login">Aller à la connexion</Link>
                    </p>
                </div>
            </main>
        );
    }

    return (
        <main className="auth-shell">
            <form className="auth-form" onSubmit={(event) => void handleSubmit(event)}>
                <h1>Créer un compte</h1>
                <input
                    aria-label="Nom d'utilisateur"
                    autoComplete="username"
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder="Nom d'utilisateur"
                    required
                    type="text"
                    value={username}
                />
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
                    autoComplete="new-password"
                    minLength={8}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="Mot de passe (8 caractères minimum)"
                    required
                    type="password"
                    value={password}
                />
                <button disabled={isSubmitting} type="submit">
                    Créer mon compte
                </button>
                {error && <span className="form-message form-message-error">{error}</span>}
                <div className="auth-divider">
                    <span>ou</span>
                </div>
                <GoogleSignInButton onError={setError} />
                <p className="auth-switch">
                    Déjà un compte ? <Link to="/login">Se connecter</Link>
                </p>
            </form>
        </main>
    );
}

export default RegisterPage;
