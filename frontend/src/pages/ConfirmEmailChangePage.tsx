import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

type Status = "loading" | "success" | "error";

/**
 * Confirmation d'un changement d'adresse e-mail (`POST /api/v1/auth/confirm-email-change`), depuis le lien envoyé à la
 * nouvelle adresse. Publique : le jeton du lien suffit.
 */
function ConfirmEmailChangePage() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";
    const [status, setStatus] = useState<Status>(token ? "loading" : "error");
    const [message, setMessage] = useState<string | null>(token ? null : "Ce lien de confirmation est incomplet.");
    // Pas de seconde requête au double rendu de développement : le jeton n'est valable qu'une fois.
    const hasRequested = useRef(false);

    useEffect(() => {
        if (!token || hasRequested.current) {
            return;
        }
        hasRequested.current = true;
        void (async () => {
            try {
                const response = await fetch("/api/v1/auth/confirm-email-change", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ token }),
                });
                const body = (await response.json().catch(() => null)) as { message?: string } | null;
                setStatus(response.ok ? "success" : "error");
                setMessage(body?.message ?? (response.ok ? "Adresse email modifiée." : "Impossible de confirmer cette adresse."));
            } catch {
                setStatus("error");
                setMessage("Impossible de contacter le serveur, réessaie plus tard.");
            }
        })();
    }, [token]);

    return (
        <main className="auth-shell">
            <div className="auth-form">
                <h1>Nouvelle adresse email</h1>
                {status === "loading" && <p>Confirmation en cours…</p>}
                {status === "success" && message && <span className="form-message form-message-success">{message}</span>}
                {status === "error" && message && <span className="form-message form-message-error">{message}</span>}
                <p className="auth-switch">
                    <Link to="/profile">Revenir à mon profil</Link>
                </p>
            </div>
        </main>
    );
}

export default ConfirmEmailChangePage;
