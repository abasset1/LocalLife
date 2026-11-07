import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { proRequest } from "../pro/proApi";

const MAX_LENGTH = 2000;
const MIN_LENGTH = 5;

type Kind = "SUGGESTION" | "BUG";

const KINDS: { value: Kind; label: string; placeholder: string }[] = [
    { value: "SUGGESTION", label: "💡 Une idée", placeholder: "Qu'est-ce qui rendrait Neiri plus utile pour toi ?" },
    { value: "BUG", label: "🐞 Un problème", placeholder: "Que s'est-il passé ? Qu'attendais-tu à la place ?" },
];

interface FeedbackModalProps {
    onClose: () => void;
}

/**
 * Formulaire « Une idée, un problème ? » (utilisateur connecté) : type de message et texte libre, envoyés à
 * `POST /api/v1/feedback` avec la page d'où il est envoyé. Les administrateurs sont prévenus par e-mail.
 */
function FeedbackModal({ onClose }: FeedbackModalProps) {
    const [kind, setKind] = useState<Kind>("SUGGESTION");
    const [message, setMessage] = useState("");
    const [isSending, setIsSending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isSent, setIsSent] = useState(false);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    // Page d'où le formulaire a été ouvert : aide à reproduire une anomalie.
    const [pageUrl] = useState(() => `${window.location.pathname}${window.location.search}`);

    const onCloseRef = useRef(onClose);
    onCloseRef.current = onClose;

    useEffect(() => {
        textareaRef.current?.focus();
        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") {
                onCloseRef.current();
            }
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, []);

    async function submit(event: FormEvent) {
        event.preventDefault();
        if (message.trim().length < MIN_LENGTH) {
            setError("Écris quelques mots pour décrire ton idée ou le problème.");
            return;
        }
        setIsSending(true);
        setError(null);
        const result = await proRequest<{ id: number }>("/api/v1/feedback", "POST", { kind, message, pageUrl });
        setIsSending(false);
        if (result.ok) {
            setIsSent(true);
        } else {
            setError(result.message);
        }
    }

    const placeholder = KINDS.find((option) => option.value === kind)?.placeholder;

    return (
        <div className="explorer-modal-overlay" onClick={onClose} role="presentation">
            <div
                aria-labelledby="feedback-modal-title"
                aria-modal="true"
                className="explorer-modal-dialog"
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className="explorer-modal-header">
                    <h2 id="feedback-modal-title">Une idée, un problème ?</h2>
                    <button aria-label="Fermer" className="explorer-modal-close-button" onClick={onClose} type="button">
                        ✕
                    </button>
                </div>
                {isSent ? (
                    <div className="explorer-form feedback-sent" role="status">
                        <p className="feedback-sent-title">🙏 Merci !</p>
                        <p>Ton message a bien été envoyé à l'équipe Neiri. On le lit avec attention.</p>
                        <div className="explorer-modal-actions">
                            <button onClick={onClose} type="button">Fermer</button>
                        </div>
                    </div>
                ) : (
                    <form className="explorer-form" onSubmit={(event) => void submit(event)}>
                        <div aria-label="Type de message" className="feedback-kinds" role="radiogroup">
                            {KINDS.map((option) => (
                                <button
                                    aria-checked={kind === option.value}
                                    className={kind === option.value ? "feedback-kind feedback-kind-active" : "feedback-kind"}
                                    key={option.value}
                                    onClick={() => setKind(option.value)}
                                    role="radio"
                                    type="button"
                                >
                                    {option.label}
                                </button>
                            ))}
                        </div>
                        <div className="explorer-form-field">
                            <label htmlFor="feedback-message">Ton message</label>
                            <textarea
                                id="feedback-message"
                                maxLength={MAX_LENGTH}
                                onChange={(event) => setMessage(event.target.value)}
                                placeholder={placeholder}
                                ref={textareaRef}
                                rows={6}
                                value={message}
                            />
                            <span className="feedback-counter">{message.length} / {MAX_LENGTH}</span>
                        </div>
                        <div className="explorer-modal-actions">
                            <button disabled={isSending} type="submit">{isSending ? "Envoi…" : "Envoyer"}</button>
                        </div>
                        {error && <span className="explorer-form-message explorer-form-message-error" role="alert">{error}</span>}
                    </form>
                )}
            </div>
        </div>
    );
}

export default FeedbackModal;
