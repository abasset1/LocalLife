import { useEffect, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import type { GeolocationStatus } from "./LocationPrompt";
import { formatPostalCodes, useCitySuggestions } from "./useCitySuggestions";
import type { CitySuggestion } from "./useCitySuggestions";

interface CityPromptModalProps {
    /** Commune choisie (avec la saisie, pour son code postal) : la fenêtre se referme. */
    onSelectCity: (city: CitySuggestion, query: string) => void;
    /** « Autour de moi » : la position obtenue remplace la ville. */
    onUseMyLocation: () => void;
    geolocationStatus: GeolocationStatus;
    /** « Se connecter » : un compte peut enregistrer sa ville dans ses préférences. */
    onLogin: () => void;
}

const LISTBOX_ID = "city-prompt-suggestions";

const GEOLOCATION_MESSAGES: Partial<Record<GeolocationStatus, string>> = {
    denied: "Localisation refusée : saisis ta ville.",
    unavailable: "Position introuvable pour le moment : saisis ta ville ou réessaie.",
    unsupported: "La localisation n'est pas disponible sur cet appareil : saisis ta ville.",
};

/**
 * Visiteur sans compte ni session : la ville est demandée avant tout chargement d'activités, dans une
 * fenêtre centrée qui ne se ferme qu'une fois une commune choisie (ou la position obtenue). Saisie avec
 * suggestions de communes ; « Valider » retient la commune mise en évidence, sinon la première proposée.
 */
function CityPromptModal({ onSelectCity, onUseMyLocation, geolocationStatus, onLogin }: CityPromptModalProps) {
    const [query, setQuery] = useState("");
    const [highlighted, setHighlighted] = useState(-1);
    const [error, setError] = useState<string | null>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const suggestions = useCitySuggestions(query, true);
    const isOpen = suggestions.length > 0;
    const activeIndex = highlighted < suggestions.length ? highlighted : -1;
    const isLocating = geolocationStatus === "locating";
    const geolocationMessage = GEOLOCATION_MESSAGES[geolocationStatus];

    useEffect(() => {
        inputRef.current?.focus();
    }, []);

    function choose(city: CitySuggestion) {
        setError(null);
        onSelectCity(city, query);
    }

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (isOpen) {
            choose(suggestions[activeIndex >= 0 ? activeIndex : 0]);
            return;
        }
        setError(query.trim().length < 2
            ? "Saisis le nom ou le code postal de ta ville."
            : "Aucune ville trouvée : vérifie l'orthographe ou essaie le code postal.");
    }

    function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
        if (!isOpen) {
            return;
        }
        if (event.key === "ArrowDown") {
            event.preventDefault();
            setHighlighted((activeIndex + 1) % suggestions.length);
        } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setHighlighted(activeIndex <= 0 ? suggestions.length - 1 : activeIndex - 1);
        }
    }

    return (
        <div className="explorer-modal-overlay city-prompt-overlay">
            <div aria-labelledby="city-prompt-title" aria-modal="true" className="city-prompt-dialog" role="dialog">
                <h2 className="city-prompt-title" id="city-prompt-title">Où veux-tu sortir ?</h2>
                <p className="city-prompt-text">
                    Indique ta ville pour découvrir les activités du mois dans un rayon de 20 km.
                </p>
                <form className="city-prompt-form" onSubmit={handleSubmit}>
                    <label className="city-prompt-label" htmlFor="city-prompt-input">Ta ville</label>
                    <div className="city-prompt-field">
                        <input
                            aria-activedescendant={activeIndex >= 0 ? `${LISTBOX_ID}-${activeIndex}` : undefined}
                            aria-autocomplete="list"
                            aria-controls={LISTBOX_ID}
                            aria-expanded={isOpen}
                            autoComplete="off"
                            className="city-prompt-input"
                            enterKeyHint="go"
                            id="city-prompt-input"
                            onChange={(event) => {
                                setQuery(event.target.value);
                                setHighlighted(-1);
                                setError(null);
                            }}
                            onKeyDown={handleKeyDown}
                            placeholder="Ex. Avignon ou 84000"
                            ref={inputRef}
                            role="combobox"
                            type="text"
                            value={query}
                        />
                        {isOpen && (
                            <ul aria-label="Villes proposées" className="explorer-search-suggestions" id={LISTBOX_ID} role="listbox">
                                {suggestions.map((city, index) => (
                                    <li
                                        aria-selected={index === activeIndex}
                                        className={index === activeIndex ? "is-highlighted" : undefined}
                                        id={`${LISTBOX_ID}-${index}`}
                                        key={`${city.name}-${city.postalCodes[0] ?? index}`}
                                        onClick={() => choose(city)}
                                        onMouseDown={(event) => event.preventDefault()}
                                        onMouseEnter={() => setHighlighted(index)}
                                        role="option"
                                    >
                                        <span aria-hidden="true">📍</span>
                                        <span className="explorer-suggestion-name">{city.name}</span>
                                        <span className="explorer-suggestion-meta">
                                            {formatPostalCodes(city.postalCodes, query)}
                                            {city.department ? ` · ${city.department}` : ""}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                    {(error ?? geolocationMessage) && (
                        <p className="city-prompt-error" role="alert">{error ?? geolocationMessage}</p>
                    )}
                    <div className="city-prompt-actions">
                        <button className="city-prompt-submit" disabled={!query.trim()} type="submit">
                            Valider
                        </button>
                        {geolocationStatus !== "denied" && geolocationStatus !== "unsupported" && (
                            <button className="explorer-chip" disabled={isLocating} onClick={onUseMyLocation} type="button">
                                {isLocating ? "Localisation…" : "⌖ Autour de moi"}
                            </button>
                        )}
                    </div>
                </form>
                <p className="city-prompt-login">
                    Déjà un compte ?{" "}
                    <button className="city-prompt-login-link" onClick={onLogin} type="button">Se connecter</button>
                </p>
            </div>
        </div>
    );
}

export default CityPromptModal;
