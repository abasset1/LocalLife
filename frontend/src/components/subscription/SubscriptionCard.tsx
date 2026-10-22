import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { proRequest } from "../pro/proApi";
import { Notice, ProfileCard } from "../profile/ProfileUi";
import { PLAN_DESCRIPTIONS, PLAN_LABELS, formatDay } from "./types";
import type { Plan, SubscriptionOverview } from "./types";

const CHOICES: Exclude<Plan, "FREE">[] = ["PREMIUM", "PRO"];

/**
 * « Mon abonnement » (BILLING-01, ADR-0014) : offre actuelle et date de fin d'accès, passage à Premium ou Pro,
 * résiliation. Pas de paiement branché pour l'instant : Premium est activé tout de suite ; Pro est une demande examinée
 * par l'équipe LocalLife (décision du 21/10/2026), que l'on peut retirer tant qu'elle est en attente. Pendant une offre
 * Pro, passer à Premium est refusé (409, message du serveur affiché tel quel).
 */
function SubscriptionCard() {
    const [overview, setOverview] = useState<SubscriptionOverview | null>(null);
    const [message, setMessage] = useState<{ kind: "success" | "error"; text: string } | null>(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        void proRequest<SubscriptionOverview>("/api/v1/users/me/subscription").then((result) => {
            if (result.ok) {
                setOverview(result.data);
            } else {
                setMessage({ kind: "error", text: result.message });
            }
        });
    }, []);

    async function act(path: string, body?: unknown, success?: string) {
        setBusy(true);
        const result = await proRequest<SubscriptionOverview>(`/api/v1/users/me/subscription/${path}`, "POST", body);
        setBusy(false);
        if (result.ok) {
            setOverview(result.data);
            setMessage(success ? { kind: "success", text: success } : null);
        } else {
            setMessage({ kind: "error", text: result.message });
        }
    }

    if (!overview) {
        return (
            <ProfileCard icon="⭐" title="Mon abonnement" titleId="profile-subscription-title">
                {message ? <Notice kind="error">{message.text}</Notice> : <p className="pp-hint">Chargement…</p>}
            </ProfileCard>
        );
    }

    const { plan, current, pending } = overview;
    const lastRejected = overview.history.find((item) => item.status === "REJECTED");

    return (
        <ProfileCard icon="⭐" subtitle="La carte et la liste restent gratuites, quelle que soit l'offre."
            title="Mon abonnement" titleId="profile-subscription-title">
            <p>
                Offre actuelle : <strong>{PLAN_LABELS[plan]}</strong>
            </p>
            <p className="pp-hint">{PLAN_DESCRIPTIONS[plan]}</p>
            {current?.status === "ACTIVE" && (
                <p className="pp-hint">
                    {current.accessEndsAt
                        ? `Accès jusqu'au ${formatDay(current.accessEndsAt)} inclus.`
                        : "Sans échéance, jusqu'à résiliation."}
                </p>
            )}
            {current?.status === "CANCELED" && current.accessEndsAt && (
                <p className="pp-hint">Résilié : accès conservé jusqu'au {formatDay(current.accessEndsAt)}.</p>
            )}
            {plan === "PRO" && <Link to="/pro">Ouvrir l'espace professionnel →</Link>}

            {pending && (
                <div className="pp-actions">
                    <Notice kind="info">
                        Demande de passage à {PLAN_LABELS[pending.plan]} envoyée le {formatDay(pending.requestedAt)} :
                        l'équipe LocalLife va l'examiner.
                    </Notice>
                    <button className="pp-button pp-button-secondary" disabled={busy} type="button"
                        onClick={() => void act("withdraw", undefined, "Demande retirée.")}>
                        Retirer ma demande
                    </button>
                </div>
            )}
            <div className="pp-actions">
                {CHOICES.filter((choice) => !(choice === plan && current?.status === "ACTIVE"))
                    .filter((choice) => pending?.plan !== choice)
                    .map((choice) => (
                        <button className="pp-button pp-button-primary" disabled={busy} key={choice} type="button"
                            title={PLAN_DESCRIPTIONS[choice]}
                            onClick={() => void act("subscribe", { plan: choice }, choice === "PRO"
                                ? "Demande d'offre Pro envoyée : l'équipe LocalLife va l'examiner."
                                : `Offre ${PLAN_LABELS[choice]} activée.`)}>
                            {choice === "PRO" ? "Demander l'offre Pro" : `Passer à ${PLAN_LABELS[choice]}`}
                        </button>
                    ))}
                {current?.status === "ACTIVE" && (
                    <button className="pp-button pp-button-danger" disabled={busy} type="button"
                        onClick={() => {
                            if (window.confirm(current.accessEndsAt
                                ? `Résilier ? Ton accès ${PLAN_LABELS[current.plan]} durera jusqu'au ${formatDay(current.accessEndsAt)}.`
                                : current.plan === "PRO"
                                    // Offre Pro sans échéance : l'accès dure jusqu'au 1er du mois suivant (22/10/2026).
                                    ? "Résilier ? Ton accès Pro durera jusqu'à la fin du mois en cours."
                                    : `Résilier ? Ton accès ${PLAN_LABELS[current.plan]} s'arrêtera tout de suite.`)) {
                                void act("cancel", undefined, "Abonnement résilié.");
                            }
                        }}>
                        Résilier
                    </button>
                )}
            </div>
            {lastRejected?.decisionReason && !pending && (
                <p className="pp-hint">Dernière demande refusée : {lastRejected.decisionReason}</p>
            )}
            <p className="pp-hint">Paiement en ligne bientôt disponible ; pour l'instant, Premium est activé sans paiement et
                une demande Pro est validée par l'équipe.</p>
            {message && <Notice kind={message.kind}>{message.text}</Notice>}
        </ProfileCard>
    );
}

export default SubscriptionCard;
