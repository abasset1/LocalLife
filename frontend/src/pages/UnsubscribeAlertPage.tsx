import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

/**
 * Lien « Ne plus recevoir cette alerte » des emails d'alerte : supprime
 * l'alerte désignée par le jeton, sans connexion requise
 * (`POST /api/v1/alerts/unsubscribe`, public — `fetch` direct).
 */
function UnsubscribeAlertPage() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token");
    const [status, setStatus] = useState<"pending" | "done" | "error">("pending");
    const [message, setMessage] = useState<string | null>(null);
    /** Requête envoyée une seule fois (StrictMode rejoue l'effet) : un second appel répondrait 404. */
    const hasRequestedRef = useRef(false);

    useEffect(() => {
        if (!token) {
            setStatus("error");
            setMessage("Lien de désinscription incomplet.");
            return;
        }
        if (hasRequestedRef.current) {
            return;
        }
        hasRequestedRef.current = true;
        (async () => {
            try {
                const response = await fetch("/api/v1/alerts/unsubscribe", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ token }),
                });
                if (response.ok) {
                    setStatus("done");
                } else {
                    const body = (await response.json().catch(() => null)) as { message?: string } | null;
                    setStatus("error");
                    setMessage(body?.message ?? "Impossible de supprimer cette alerte.");
                }
            } catch {
                setStatus("error");
                setMessage("Impossible de contacter le serveur, réessaie plus tard.");
            }
        })();
    }, [token]);

    return (
        <div className="admin-shell">
            <header className="admin-header">
                <h1>Alerte e-mail</h1>
                <Link to="/">← Retour à la carte</Link>
            </header>
            <main className="profile-content">
                {status === "pending" && <p className="activities-status">Suppression de l'alerte…</p>}
                {status === "done" && (
                    <p className="activities-status">
                        C'est fait : tu ne recevras plus cette alerte. Tu peux gérer tes autres alertes depuis{" "}
                        <Link to="/profile">ton profil</Link>.
                    </p>
                )}
                {status === "error" && <p className="activities-status activities-status-error">{message}</p>}
            </main>
        </div>
    );
}

export default UnsubscribeAlertPage;
