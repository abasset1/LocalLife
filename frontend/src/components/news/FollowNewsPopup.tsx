import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { followNewsDismissKey, followNewsPopupKey, readFlag, writeFlag } from "./newsStorage";

interface FollowNewsPopupProps {
    userId: number | null;
    /** `limits.canFollow` du compte : la popup n'est montrée que s'il vaut `false` (membre gratuit). */
    canFollow: boolean | null;
    /** Une autre fenêtre est ouverte (accueil de première connexion, fiche, formulaire) : la popup attend. */
    blocked: boolean;
}

/**
 * Popup montrée une seule fois à un membre gratuit, par compte et par appareil : annonce la cloche « Suivre » et
 * Premium gratuit pour le moment. « Découvrir » ouvre la page « Nouveautés » (et ferme aussi le bandeau de la carte) ;
 * « Plus tard », ✕, Échap ou un clic à côté la ferment en laissant le bandeau comme rappel. Marquée vue dès son
 * affichage : un rechargement de la page ne la remontre pas.
 */
function FollowNewsPopup({ userId, canFollow, blocked }: FollowNewsPopupProps) {
    const [shownFor, setShownFor] = useState<number | null>(null);
    const [closedFor, setClosedFor] = useState<number | null>(null);
    const primaryRef = useRef<HTMLAnchorElement>(null);

    const eligible = userId !== null && canFollow === false && closedFor !== userId;
    const visible = eligible && (shownFor === userId || (!blocked && !readFlag(followNewsPopupKey(userId))));

    useEffect(() => {
        if (visible && userId !== null && shownFor !== userId) {
            writeFlag(followNewsPopupKey(userId));
            setShownFor(userId);
        }
    }, [visible, userId, shownFor]);

    useEffect(() => {
        if (!visible) {
            return;
        }
        primaryRef.current?.focus();
        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") {
                setClosedFor(userId);
            }
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [visible, userId]);

    if (!visible || userId === null) {
        return null;
    }

    function close() {
        setClosedFor(userId);
    }

    return (
        <div className="explorer-modal-overlay news-popup-overlay" onClick={close} role="presentation">
            <div
                aria-labelledby="news-popup-title"
                aria-modal="true"
                className="news-popup"
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <button aria-label="Fermer" className="news-popup-close" onClick={close} type="button">✕</button>
                <p className="news-badge">Nouveau</p>
                <div aria-hidden="true" className="news-bell">🔔</div>
                <h2 id="news-popup-title">Suis tes activités et tes lieux préférés</h2>
                <p className="news-popup-lead">
                    Touche 🔔 « Suivre » sur une activité ou un lieu : LocalLife te prévient quand ses dates changent,
                    qu'il est annulé ou qu'un lieu programme une nouvelle activité.
                </p>
                <p className="news-free">🎁 Premium est gratuit pour le moment</p>
                <div className="news-popup-actions">
                    <Link
                        className="pp-button pp-button-primary news-cta"
                        onClick={() => writeFlag(followNewsDismissKey(userId))}
                        ref={primaryRef}
                        to="/nouveautes"
                    >
                        Découvrir
                    </Link>
                    <button className="pp-button pp-button-secondary" onClick={close} type="button">Plus tard</button>
                </div>
            </div>
        </div>
    );
}

export default FollowNewsPopup;
