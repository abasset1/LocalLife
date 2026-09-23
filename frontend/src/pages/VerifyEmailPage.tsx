import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

interface ApiMessageBody {
    message: string;
}

type VerificationStatus = "loading" | "success" | "error";

function VerifyEmailPage() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";

    const [status, setStatus] = useState<VerificationStatus>(token ? "loading" : "error");
    const [message, setMessage] = useState<string | null>(
        token ? null : "Ce lien de confirmation est incomplet."
    );
    // Garde contre le double déclenchement de l'effet (StrictMode en développement) : le
    // backend est idempotent, mais une seconde requête inutile est évitée.
    const hasRequested = useRef(false);

    useEffect(() => {
        if (!token || hasRequested.current) {
            return;
        }
        hasRequested.current = true;

        async function verify() {
            try {
                const response = await fetch("/api/v1/auth/verify-email", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ token }),
                });
                const body = (await response.json().catch(() => null)) as ApiMessageBody | null;

                if (!response.ok) {
                    setStatus("error");
                    setMessage(body?.message ?? "Impossible de confirmer l'adresse email.");
                    return;
                }

                setStatus("success");
                setMessage(body?.message ?? "Adresse email confirmée. Vous pouvez vous connecter.");
            } catch {
                setStatus("error");
                setMessage("Impossible de contacter le serveur, réessaie plus tard.");
            }
        }

        void verify();
    }, [token]);

    return (
        <main className="auth-shell">
            <div className="auth-form">
                <h1>Confirmation de l'adresse email</h1>
                {status === "loading" && <p>Confirmation en cours…</p>}
                {status === "success" && message && (
                    <span className="form-message form-message-success">{message}</span>
                )}
                {status === "error" && message && (
                    <span className="form-message form-message-error">{message}</span>
                )}
                <p className="auth-switch">
                    <Link to="/login">
                        {status === "error" ? "Retour à la connexion (un nouveau lien peut y être demandé)" : "Se connecter"}
                    </Link>
                </p>
            </div>
        </main>
    );
}

export default VerifyEmailPage;
