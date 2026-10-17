/** Dernière demande de position déclenchée par « Autour de moi ». */
export type GeolocationStatus = "idle" | "locating" | "denied" | "unavailable" | "unsupported";

interface LocationPromptProps {
    /** Un lieu de recherche est connu (ville choisie ou enregistrée, position obtenue). */
    hasLocation: boolean;
    status: GeolocationStatus;
    /** Invitation refermée par l'utilisateur : seuls les échecs de localisation restent signalés. */
    isDismissed: boolean;
    onChooseCity: () => void;
    onUseMyLocation: () => void;
    onDismiss: () => void;
}

const FAILURE_MESSAGES: Record<"denied" | "unavailable" | "unsupported", string> = {
    denied: "Localisation refusée. Choisis ta ville pour voir les activités proches.",
    unavailable: "Position introuvable pour le moment. Réessaie ou choisis ta ville.",
    unsupported: "La localisation n'est pas disponible sur cet appareil. Choisis ta ville.",
};

/**
 * Spec P0-A : invitation explicite à choisir un lieu (« Choisir ma ville » / « Autour de moi ») tant
 * qu'aucun n'est connu — la carte montre alors une vue d'ensemble, jamais présentée comme la position
 * de l'utilisateur — et explication discrète d'un échec de localisation, avec une autre méthode.
 */
function LocationPrompt({ hasLocation, status, isDismissed, onChooseCity, onUseMyLocation, onDismiss }: LocationPromptProps) {
    const isFailure = status === "denied" || status === "unavailable" || status === "unsupported";
    if (!isFailure && (hasLocation || isDismissed)) {
        return null;
    }

    const isLocating = status === "locating";

    return (
        <div aria-live="polite" className={`explorer-location-prompt${isFailure ? " is-warning" : ""}`} role="status">
            <p className="explorer-location-prompt-text">
                {isFailure ? (
                    FAILURE_MESSAGES[status]
                ) : (
                    <>
                        <strong>Où veux-tu sortir ?</strong> La carte montre une vue d'ensemble, pas ta position.
                    </>
                )}
            </p>
            <div className="explorer-location-prompt-actions">
                <button className="explorer-chip explorer-chip-active" onClick={onChooseCity} type="button">
                    📍 Choisir ma ville
                </button>
                {status !== "denied" && status !== "unsupported" && (
                    <button className="explorer-chip" disabled={isLocating} onClick={onUseMyLocation} type="button">
                        {isLocating ? "Localisation…" : status === "unavailable" ? "⌖ Réessayer" : "⌖ Autour de moi"}
                    </button>
                )}
            </div>
            <button aria-label="Fermer" className="explorer-location-prompt-close" onClick={onDismiss} type="button">
                ×
            </button>
        </div>
    );
}

export default LocationPrompt;
