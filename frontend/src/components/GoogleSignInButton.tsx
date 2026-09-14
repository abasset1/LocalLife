import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { saveToken } from "../auth/authStorage";

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
                };
            };
        };
    }
}

// Variable Vite (préfixe VITE_ obligatoire pour être exposée au bundle client, voir
// https://vite.dev/guide/env-and-mode) : c'est l'identifiant client OAuth, une valeur
// publique par conception pour ce flux (contrairement à un client secret, jamais utilisé
// ici — voir ADR-0002 côté backend) — sans risque à l'exposer dans le bundle.
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;
const GOOGLE_SCRIPT_SRC = "https://accounts.google.com/gsi/client";

// Mémorisé au niveau du module (LoginPage et RegisterPage utilisent toutes deux ce
// composant) : évite d'injecter deux fois la même balise <script> si l'utilisateur
// navigue entre les deux pages au cours d'une même session.
let googleScriptPromise: Promise<void> | null = null;

function loadGoogleScript(): Promise<void> {
    if (googleScriptPromise) {
        return googleScriptPromise;
    }
    googleScriptPromise = new Promise((resolve, reject) => {
        if (document.querySelector(`script[src="${GOOGLE_SCRIPT_SRC}"]`)) {
            resolve();
            return;
        }
        const script = document.createElement("script");
        script.src = GOOGLE_SCRIPT_SRC;
        script.async = true;
        script.defer = true;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error("Impossible de charger le script Google."));
        document.head.appendChild(script);
    });
    return googleScriptPromise;
}

interface GoogleSignInButtonProps {
    onError: (message: string) => void;
}

/**
 * Bouton "Continuer avec Google" (LL-EF-011), rendu par Google Identity
 * Services lui-même plutôt qu'un bouton personnalisé imitant son style :
 * approche recommandée par Google, qui évite tout souci de conformité
 * avec ses règles d'usage de marque. Utilisé à l'identique sur
 * `LoginPage` et `RegisterPage` (le flux Google ne distingue pas
 * connexion et inscription, voir `AuthService#loginOrRegisterWithGoogle`
 * côté backend).
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
function GoogleSignInButton({ onError }: GoogleSignInButtonProps) {
    const navigate = useNavigate();
    const buttonContainerRef = useRef<HTMLDivElement>(null);

    // Effet à exécution unique (montage du composant) : le rendu du bouton par Google
    // Identity Services n'a pas besoin d'être refait tant que le composant reste monté,
    // et onError (fonction recréée à chaque rendu des pages appelantes) ne doit pas
    // déclencher un nouveau chargement du script / une nouvelle inscription du bouton.
    useEffect(() => {
        if (!GOOGLE_CLIENT_ID) {
            return;
        }

        let cancelled = false;

        async function handleCredentialResponse(response: GoogleCredentialResponse) {
            try {
                const apiResponse = await fetch("/api/v1/auth/google", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ idToken: response.credential }),
                });

                if (!apiResponse.ok) {
                    const body = (await apiResponse.json().catch(() => null)) as ApiErrorBody | null;
                    onError(body?.message ?? "Impossible de se connecter avec Google.");
                    return;
                }

                const { token } = (await apiResponse.json()) as { token: string };
                saveToken(token);
                navigate("/");
            } catch {
                onError("Impossible de contacter le serveur, réessaie plus tard.");
            }
        }

        loadGoogleScript()
            .then(() => {
                if (cancelled || !window.google || !buttonContainerRef.current) {
                    return;
                }
                window.google.accounts.id.initialize({
                    client_id: GOOGLE_CLIENT_ID,
                    callback: (response) => void handleCredentialResponse(response),
                });
                window.google.accounts.id.renderButton(buttonContainerRef.current, {
                    theme: "outline",
                    size: "large",
                    text: "continue_with",
                    width: "320",
                });
            })
            .catch(() => onError("Impossible de charger Google Sign-In, réessaie plus tard."));

        return () => {
            cancelled = true;
        };
        // Volontairement vide : voir le commentaire au-dessus de cet effet.
        // eslint-disable-next-line
    }, []);

    if (!GOOGLE_CLIENT_ID) {
        return null;
    }

    return <div ref={buttonContainerRef} className="google-signin-button" />;
}

export default GoogleSignInButton;
