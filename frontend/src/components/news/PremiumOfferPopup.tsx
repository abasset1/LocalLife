import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { proRequest } from "../pro/proApi";
import type { SubscriptionOverview } from "../subscription/types";
import {
    followNewsPopupKey,
    premiumOfferClosedKey,
    premiumOfferOptOutKey,
    readFlag,
    readSessionFlag,
    writeFlag,
    writeSessionFlag,
} from "./newsStorage";

/** Durée pendant laquelle la popup est proposée après une connexion. */
export const PREMIUM_OFFER_DAYS_AFTER_LOGIN = 7;
const DAY_MS = 24 * 60 * 60 * 1000;

interface PremiumOfferPopupProps {
    userId: number | null;
    /** `limits.canFollow` du compte : la popup n'est montrée que s'il vaut `false` (compte gratuit). */
    canFollow: boolean | null;
    /** Date de la connexion (émission du jeton, en secondes) ; `undefined` si inconnue. */
    loggedInAt: number | undefined;
    /** Une autre fenêtre est ouverte (accueil, fiche, formulaire) : la popup attend. */
    blocked: boolean;
    /** Horloge, remplaçable dans les tests. */
    now?: () => number;
}

const BENEFITS = [
    { icon: "🔔", text: "Suis tes activités et tes lieux préférés : prévenu au moindre changement" },
    { icon: "🔎", text: "Des alertes par mots-clés : seulement ce qui t'intéresse" },
    { icon: "❤️", text: "Favoris illimités et jusqu'à 10 alertes" },
    { icon: "✍️", text: "Propose tes propres activités sur la carte" },
];

/**
 * Popup qui propose Premium aux comptes gratuits. Elle s'affiche à chaque visite pendant
 * {@link PREMIUM_OFFER_DAYS_AFTER_LOGIN} jours après chaque connexion ; « Plus tard », ✕, Échap ou un clic à côté la
 * ferment pour la visite en cours. « Ne plus me proposer » l'arrête définitivement pour ce compte sur cet appareil.
 * Elle laisse passer d'abord la popup de nouveauté « Suivre » (jamais deux popups à la suite). « Passer à Premium »
 * l'active tout de suite (gratuit pour le moment, comme sur la page « Nouveautés »).
 */
function PremiumOfferPopup({ userId, canFollow, loggedInAt, blocked, now = Date.now }: PremiumOfferPopupProps) {
    const [closedFor, setClosedFor] = useState<number | null>(null);
    const [shownFor, setShownFor] = useState<number | null>(null);
    const [isSubscribing, setIsSubscribing] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [subscribedFor, setSubscribedFor] = useState<number | null>(null);
    const primaryRef = useRef<HTMLButtonElement>(null);
    /**
     * Popup de nouveauté déjà vue avant cette visite, par compte : relu une seule fois, sans quoi l'offre suivrait
     * aussitôt la nouveauté, qui se marque vue dès son affichage.
     */
    const newsSeenBeforeVisit = useRef(new Map<number, boolean>());
    if (userId !== null && !newsSeenBeforeVisit.current.has(userId)) {
        newsSeenBeforeVisit.current.set(userId, readFlag(followNewsPopupKey(userId)));
    }

    const isRecentLogin = loggedInAt !== undefined
        && now() - loggedInAt * 1000 < PREMIUM_OFFER_DAYS_AFTER_LOGIN * DAY_MS;
    const eligible = userId !== null
        && closedFor !== userId
        && (subscribedFor === userId || (canFollow === false && isRecentLogin
            && !readFlag(premiumOfferOptOutKey(userId))
            && !readSessionFlag(premiumOfferClosedKey(userId))
            // La popup de nouveauté passe en premier ; l'offre attend la visite suivante.
            && newsSeenBeforeVisit.current.get(userId) === true));
    const visible = eligible && (shownFor === userId || !blocked);

    useEffect(() => {
        if (visible && userId !== null && shownFor !== userId) {
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
                close();
            }
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [visible, userId]);

    if (!visible || userId === null) {
        return null;
    }

    function close() {
        if (userId !== null) {
            writeSessionFlag(premiumOfferClosedKey(userId));
        }
        setClosedFor(userId);
    }

    function optOut() {
        if (userId !== null) {
            writeFlag(premiumOfferOptOutKey(userId));
        }
        setClosedFor(userId);
    }

    async function subscribe() {
        setIsSubscribing(true);
        setError(null);
        const result = await proRequest<SubscriptionOverview>("/api/v1/users/me/subscription/subscribe", "POST",
            { plan: "PREMIUM" });
        setIsSubscribing(false);
        if (result.ok) {
            setSubscribedFor(userId);
        } else {
            setError(result.message);
        }
    }

    const isSubscribed = subscribedFor === userId;

    return (
        <div className="explorer-modal-overlay news-popup-overlay" onClick={close} role="presentation">
            <div
                aria-labelledby="premium-offer-title"
                aria-modal="true"
                className="news-popup premium-offer"
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <button aria-label="Fermer" className="news-popup-close" onClick={close} type="button">✕</button>
                {isSubscribed ? (
                    <>
                        <div aria-hidden="true" className="news-bell">🎉</div>
                        <h2 id="premium-offer-title">Bienvenue dans Premium !</h2>
                        <p className="news-popup-lead">
                            Tout est débloqué. Ouvre une activité ou un lieu et touche 🔔 « Suivre » pour commencer.
                        </p>
                        <div className="news-popup-actions">
                            <button className="pp-button pp-button-primary news-cta" onClick={() => window.location.reload()}
                                type="button">
                                C'est parti
                            </button>
                            <Link className="pp-button pp-button-secondary" to="/nouveautes">Voir les nouveautés</Link>
                        </div>
                    </>
                ) : (
                    <>
                        <div aria-hidden="true" className="news-bell">✨</div>
                        <h2 id="premium-offer-title">Passe à Premium</h2>
                        <p className="news-popup-lead">Ne rate plus rien de ce qui se passe près de chez toi.</p>
                        <ul className="premium-offer-benefits">
                            {BENEFITS.map((benefit) => (
                                <li key={benefit.text}>
                                    <span aria-hidden="true">{benefit.icon}</span>
                                    {benefit.text}
                                </li>
                            ))}
                        </ul>
                        <p className="news-free">🎁 Gratuit pour le moment</p>
                        <div className="news-popup-actions">
                            <button
                                className="pp-button pp-button-primary news-cta"
                                disabled={isSubscribing}
                                onClick={() => void subscribe()}
                                ref={primaryRef}
                                type="button"
                            >
                                {isSubscribing ? "Activation…" : "Passer à Premium"}
                            </button>
                            <button className="pp-button pp-button-secondary" onClick={close} type="button">
                                Plus tard
                            </button>
                        </div>
                        {error && <p className="news-error" role="alert">{error}</p>}
                        <button className="premium-offer-opt-out" onClick={optOut} type="button">
                            Ne plus me proposer
                        </button>
                    </>
                )}
            </div>
        </div>
    );
}

export default PremiumOfferPopup;
