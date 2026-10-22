import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { saveToken } from "../auth/authStorage";
import TermsConsentCheckbox from "./TermsConsentCheckbox";

interface ApiErrorBody {
    message: string;
}

interface GoogleCredentialResponse {
    credential: string;
}

/**
 * Typage minimal de la partie de Google Identity Services utilisée ici
 * (script chargé dynamiquement, voir loadGoogleScript ci-dessous — il n'y
 * a pas de paquet @types officiel pour ce script).
 */
declare global {
    interface Window {
        google?: {
            accounts: {
                id: {
                    initialize(config: {
                        client_id: string;
                        callback: (response: GoogleCredentialResponse) => void;
                    }): void;
                    renderButton(
                        parent: HTMLElement,
                        options: { theme?: string; size?: string; width?: string; text?: string },
                    ): void;
                    prompt(): void;
                };
            };
        };
    }
}

// Variable Vite (préfixe VITE_ obligatoire pour être exposée au bundle client, voir
// https://vite.dev/guide/env-and-mode) : c'est l'identifiant client OAuth, une valeur
// publique par conception pour ce flux (contrairement à un client secret, jamais utilisé
// ici — voir ADR-0002 côté backend) — sans risque à l'exposer dans le bundle.
// Lue au rendu (et non au chargement du module) pour rester remplaçable dans les tests.
function googleClientId(): string | undefined {
    return (import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined) || undefined;
}

const GOOGLE_SCRIPT_SRC = "https://accounts.google.com/gsi/client";

// Mémorisé au niveau du module (LoginPage et RegisterPage utilisent toutes deux ce
// composant) : évite d'injecter deux fois la même balise <script> si l'utilisateur
// navigue entre les deux pages au cours d'une même session.
let googleScriptPromise: Promise<void> | null = null;

function loadGoogleScript(): Promise<void> {
    if (googleScriptPromise) {
        return googleScriptPromise;
    }
    googleScriptPromise = new Promise<void>((resolve, reject) => {
        if (document.querySelector(`script[src="${GOOGLE_SCRIPT_SRC}"]`)) {
            resolve();
            return;
        }
        const script = document.createElement("script");
        script.src = GOOGLE_SCRIPT_SRC;
        script.async = true;
        script.defer = true;
        script.onload = () => resolve();
        script.onerror = () => {
            script.remove();
            reject(new Error("Impossible de charger le script Google."));
        };
        document.head.appendChild(script);
    }).catch((error: unknown) => {
        // Un échec (réseau coupé…) ne doit pas empêcher un nouvel essai au clic suivant.
        googleScriptPromise = null;
        throw error;
    });
    return googleScriptPromise;
}

interface GoogleSignInButtonProps {
    onError: (message: string) => void;
    /**
     * `register` : le bouton n'est actif qu'une fois la case de consentement de la page cochée
     * (`consentAccepted`) et envoie `acceptTerms: true`. `login` (défaut) : connexion sans case ;
     * si le serveur répond 428 (la connexion créerait un compte), la case s'affiche ici même et
     * l'appel est relancé avec le même ID token et `acceptTerms: true`.
     */
    mode?: "login" | "register";
    consentAccepted?: boolean;
}

/**
 * Bouton « Continuer avec Google » (LL-EF-011). Contrat RGPD : le script Google
 * Identity Services n'est plus chargé à l'ouverture de la page — c'est un bouton
 * LocalLife, et le script n'est chargé qu'au clic, qui lance aussitôt la connexion
 * Google (`prompt`). Le bouton officiel de Google est rendu en secours au même
 * endroit, au cas où le navigateur bloquerait la fenêtre de connexion.
 *
 * Flux : Google renvoie un ID token signé directement au frontend (pas
 * de redirection serveur), transmis tel quel à `POST /api/v1/auth/google`
 * qui le vérifie côté serveur (voir `GoogleAuthService`) et renvoie le
 * JWT LocalLife habituel. Ce composant ne lit et n'envoie jamais l'email
 * ou le nom séparément — exigence de sécurité explicite du ticket, voir
 * ADR-0002.
 *
 * Ne rend rien si `VITE_GOOGLE_CLIENT_ID` n'est pas configurée (voir
 * `.env.example`), plutôt qu'un bouton cassé qui échouerait au clic.
 */
function GoogleSignInButton({ onError, mode = "login", consentAccepted = false }: GoogleSignInButtonProps) {
    const navigate = useNavigate();
    const fallbackContainerRef = useRef<HTMLDivElement>(null);
    const [status, setStatus] = useState<"idle" | "loading" | "ready">("idle");
    const [isSending, setIsSending] = useState(false);
    // ID token gardé après un 428 : renvoyé tel quel avec acceptTerms une fois la case cochée.
    const [pendingIdToken, setPendingIdToken] = useState<string | null>(null);
    const [consentMessage, setConsentMessage] = useState<string | null>(null);
    const [loginConsent, setLoginConsent] = useState(false);

    // Le callback Google est enregistré une seule fois (initialize) : il lit les props à jour via cette référence.
    const latest = useRef({ mode, consentAccepted, onError });
    latest.current = { mode, consentAccepted, onError };
    const initializedRef = useRef(false);

    const clientId = googleClientId();
    if (!clientId) {
        return null;
    }

    async function sendIdToken(idToken: string, acceptTerms: boolean) {
        setIsSending(true);
        try {
            const apiResponse = await fetch("/api/v1/auth/google", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(acceptTerms ? { idToken, acceptTerms: true } : { idToken }),
            });

            if (apiResponse.status === 428) {
                // Pas encore de compte LocalLife pour ce compte Google : consentement requis.
                const body = (await apiResponse.json().catch(() => null)) as ApiErrorBody | null;
                setPendingIdToken(idToken);
                setConsentMessage(body?.message ?? "Pour créer ton compte, coche la case ci-dessous.");
                return;
            }

            if (!apiResponse.ok) {
                const body = (await apiResponse.json().catch(() => null)) as ApiErrorBody | null;
                // ID token refusé (expiré par exemple) : il faudra relancer Google.
                setPendingIdToken(null);
                setConsentMessage(null);
                latest.current.onError(body?.message ?? "Impossible de se connecter avec Google.");
                return;
            }

            const { token } = (await apiResponse.json()) as { token: string };
            saveToken(token);
            navigate("/");
        } catch {
            latest.current.onError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSending(false);
        }
    }

    function handleCredential(response: GoogleCredentialResponse) {
        const current = latest.current;
        void sendIdToken(response.credential, current.mode === "register" && current.consentAccepted);
    }

    async function handleClick() {
        if (mode === "register" && !consentAccepted) {
            return;
        }
        setStatus("loading");
        try {
            await loadGoogleScript();
        } catch {
            setStatus("idle");
            onError("Impossible de charger Google Sign-In, réessaie plus tard.");
            return;
        }
        const google = window.google;
        if (!google) {
            setStatus("idle");
            onError("Impossible de charger Google Sign-In, réessaie plus tard.");
            return;
        }
        if (!initializedRef.current) {
            google.accounts.id.initialize({ client_id: clientId!, callback: handleCredential });
            initializedRef.current = true;
        }
        if (fallbackContainerRef.current) {
            google.accounts.id.renderButton(fallbackContainerRef.current, {
                theme: "outline",
                size: "large",
                text: "continue_with",
                width: "280",
            });
        }
        setStatus("ready");
        google.accounts.id.prompt();
    }

    const disabled = status === "loading" || isSending || (mode === "register" && !consentAccepted);

    return (
        <div className="google-signin">
            <button
                className="google-continue-button"
                disabled={disabled}
                onClick={() => void handleClick()}
                type="button"
            >
                <span aria-hidden="true" className="google-continue-mark">G</span>
                {status === "loading" ? "Chargement de Google…" : "Continuer avec Google"}
            </button>
            <p className="auth-note">Google recevra ton adresse e-mail.</p>
            {mode === "register" && !consentAccepted && (
                <p className="auth-note">Coche la case ci-dessus pour continuer avec Google.</p>
            )}
            {status === "ready" && !pendingIdToken && (
                <p className="auth-note">Si la fenêtre Google ne s'ouvre pas, utilise le bouton Google ci-dessous.</p>
            )}
            <div ref={fallbackContainerRef} className="google-signin-button" hidden={status !== "ready" || !!pendingIdToken} />
            {pendingIdToken && (
                <div className="google-consent" role="group" aria-label="Création du compte avec Google">
                    {consentMessage && <span className="form-message form-message-error">{consentMessage}</span>}
                    <TermsConsentCheckbox checked={loginConsent} id="google-terms-consent" onChange={setLoginConsent} />
                    <button
                        disabled={!loginConsent || isSending}
                        onClick={() => void sendIdToken(pendingIdToken, true)}
                        type="button"
                    >
                        Créer mon compte avec Google
                    </button>
                </div>
            )}
        </div>
    );
}

export default GoogleSignInButton;
