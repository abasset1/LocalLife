import { useState } from "react";
import type { KeyboardEvent } from "react";

export const MAX_ALERT_KEYWORDS = 10;
const MIN_KEYWORD_LENGTH = 2;
const MAX_KEYWORD_LENGTH = 60;

/** Forme de comparaison d'un mot-clé : minuscules, sans accents ni ponctuation (comme le serveur). */
export function comparableKeyword(keyword: string): string {
    return keyword
        .toLowerCase()
        .normalize("NFD")
        .replace(/\p{M}+/gu, "")
        .replace(/œ/g, "oe")
        .replace(/æ/g, "ae")
        .replace(/[^\p{L}\p{N}]+/gu, " ")
        .trim();
}

/**
 * Ajoute à `current` les mots-clés saisis dans `text` (séparés par des virgules), sans doublon : renvoie la nouvelle
 * liste, ou un message d'erreur.
 */
export function addKeywords(current: string[], text: string): { keywords: string[] } | { error: string } {
    const next = [...current];
    for (const part of text.split(",")) {
        const keyword = part.trim().replace(/\s+/g, " ");
        if (keyword === "") {
            continue;
        }
        if (keyword.length < MIN_KEYWORD_LENGTH || keyword.length > MAX_KEYWORD_LENGTH) {
            return { error: `Un mot-clé fait de ${MIN_KEYWORD_LENGTH} à ${MAX_KEYWORD_LENGTH} caractères.` };
        }
        const comparable = comparableKeyword(keyword);
        if (comparable === "") {
            return { error: "Un mot-clé doit contenir au moins une lettre ou un chiffre." };
        }
        if (!next.some((existing) => comparableKeyword(existing) === comparable)) {
            next.push(keyword);
        }
    }
    if (next.length > MAX_ALERT_KEYWORDS) {
        return { error: `Une alerte a ${MAX_ALERT_KEYWORDS} mots-clés au plus.` };
    }
    return { keywords: next };
}

/**
 * Saisie des mots-clés d'une alerte : un champ (Entrée, virgule ou « Ajouter » pour valider) et les mots-clés en
 * pastilles, chacune avec ✕ pour la retirer.
 */
function AlertKeywordsInput({ id, keywords, onChange, disabled = false, label }: {
    id: string;
    /** Nom accessible du champ quand aucun `<label>` ne le désigne. */
    label?: string;
    keywords: string[];
    onChange: (keywords: string[]) => void;
    disabled?: boolean;
}) {
    const [text, setText] = useState("");
    const [error, setError] = useState<string | null>(null);

    function commit(): boolean {
        if (text.trim() === "") {
            return true;
        }
        const result = addKeywords(keywords, text);
        if ("error" in result) {
            setError(result.error);
            return false;
        }
        setError(null);
        setText("");
        onChange(result.keywords);
        return true;
    }

    function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
        if (event.key === "Enter" || event.key === ",") {
            event.preventDefault();
            commit();
        }
    }

    return (
        <div className="pp-keywords">
            {keywords.length > 0 && (
                <ul aria-label="Mots-clés choisis" className="pp-keyword-list">
                    {keywords.map((keyword) => (
                        <li className="pp-keyword" key={keyword}>
                            {keyword}
                            <button
                                aria-label={`Retirer le mot-clé ${keyword}`}
                                disabled={disabled}
                                onClick={() => onChange(keywords.filter((value) => value !== keyword))}
                                type="button"
                            >
                                ✕
                            </button>
                        </li>
                    ))}
                </ul>
            )}
            <div className="pp-keyword-entry">
                <input
                    aria-describedby={`${id}-hint`}
                    aria-label={label}
                    className="pp-input"
                    disabled={disabled || keywords.length >= MAX_ALERT_KEYWORDS}
                    id={id}
                    maxLength={MAX_KEYWORD_LENGTH * 3}
                    onBlur={() => void commit()}
                    onChange={(event) => {
                        setText(event.target.value);
                        setError(null);
                    }}
                    onKeyDown={handleKeyDown}
                    placeholder="jazz, marché de Noël, randonnée…"
                    type="text"
                    value={text}
                />
                <button
                    className="pp-button pp-button-secondary"
                    disabled={disabled || text.trim() === ""}
                    onClick={() => void commit()}
                    type="button"
                >
                    Ajouter
                </button>
            </div>
            <p className="pp-hint" id={`${id}-hint`}>
                Une activité est retenue si l'un de ces mots apparaît dans son titre ou sa description (majuscules et
                accents ignorés). {keywords.length} / {MAX_ALERT_KEYWORDS}
            </p>
            {error && <p className="pp-keyword-error" role="alert">{error}</p>}
        </div>
    );
}

export default AlertKeywordsInput;
