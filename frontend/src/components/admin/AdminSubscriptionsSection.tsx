import { useEffect, useState } from "react";
import { proRequest } from "../pro/proApi";
import { PLAN_LABELS, formatDay } from "../subscription/types";
import type { Subscription } from "../subscription/types";

/** Abonnement vu par un administrateur (`AdminSubscriptionResponse`, backend). */
interface AdminSubscription {
    userId: number;
    userEmail: string;
    subscription: Subscription;
}

const REQUESTED = "REQUESTED";

const TABS = [
    { value: REQUESTED, label: "Demandes Pro à traiter" },
    { value: "ACTIVE,CANCELED", label: "Abonnements en cours" },
];

/**
 * Abonnements (BILLING-01, ADR-0014). Premium est activé tout de suite, sans paiement branché ; l'offre Pro est une
 * demande que l'équipe valide ici (décision du 21/10/2026) : activation avec une date de fin facultative, ou refus
 * motivé, visible de l'utilisateur. Les demandes en attente s'affichent en premier, avec leur nombre ; un
 * administrateur peut aussi retirer un accès en cours. `onPendingCountChange` : nombre de demandes en attente, pour la
 * pastille de l'onglet « Professionnels ».
 */
function AdminSubscriptionsSection({ onPendingCountChange }: { onPendingCountChange?: (count: number) => void } = {}) {
    const [tab, setTab] = useState(TABS[0].value);
    const [items, setItems] = useState<AdminSubscription[]>([]);
    const [pendingCount, setPendingCount] = useState<number | null>(null);
    const [endsOn, setEndsOn] = useState<Record<number, string>>({});
    const [message, setMessage] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        void proRequest<AdminSubscription[]>(`/api/v1/admin/subscriptions?status=${tab}&limit=200`).then((result) => {
            if (result.ok) {
                setItems(result.data);
            } else {
                setMessage(result.message);
            }
        });
    }, [tab, refreshKey]);

    // Nombre de demandes en attente, quel que soit l'onglet affiché (rechargé après chaque décision).
    useEffect(() => {
        void proRequest<AdminSubscription[]>(`/api/v1/admin/subscriptions?status=${REQUESTED}&limit=200`)
            .then((result) => {
                if (result.ok) {
                    setPendingCount(result.data.length);
                    onPendingCountChange?.(result.data.length);
                }
            });
    }, [refreshKey, onPendingCountChange]);

    async function decide(item: AdminSubscription, action: "approve" | "reject" | "revoke") {
        let body: unknown = {};
        if (action === "approve") {
            body = { endsOn: endsOn[item.subscription.id] || null };
        } else {
            const reason = window.prompt(action === "reject"
                ? "Motif du refus (visible de l'utilisateur) :"
                : "Motif du retrait d'accès :");
            if (reason === null || (action === "reject" && reason.trim() === "")) {
                return;
            }
            body = { reason };
        }
        const result = await proRequest(`/api/v1/admin/subscriptions/${item.subscription.id}/${action}`, "POST", body);
        setMessage(result.ok ? `${item.userEmail} : ${action === "approve" ? "offre activée"
            : action === "reject" ? "demande refusée" : "accès retiré"}.` : result.message);
        setRefreshKey((key) => key + 1);
    }

    return (
        <>
            <h3>Abonnements et demandes d'offre Pro</h3>
            <p className="activities-status">
                Premium est activé tout de suite (pas de paiement branché). Une demande d'offre Pro doit être activée ou
                refusée ici : le compte ne reçoit l'espace professionnel qu'après activation.
            </p>
            <nav className="admin-tabs">
                {TABS.map((item) => (
                    <button className={item.value === tab ? "admin-tab admin-tab-active" : "admin-tab"} key={item.value}
                        onClick={() => setTab(item.value)} type="button">
                        {item.label}
                        {item.value === REQUESTED && pendingCount !== null && (
                            <span className="admin-tab-count"> ({pendingCount})</span>
                        )}
                    </button>
                ))}
            </nav>
            {message && <p className="activities-status" role="status">{message}</p>}
            {items.length === 0 ? (
                <p className="activities-status">
                    {tab === REQUESTED ? "Aucune demande d'offre Pro en attente." : "Aucun abonnement en cours."}
                </p>
            ) : (
                <ul className="favorites-list">
                    {items.map((item) => (
                        <li className="favorites-list-item contributions-list-item" key={item.subscription.id}>
                            <div className="favorites-list-item-info">
                                <span className="favorites-list-item-title">
                                    {item.userEmail} · {PLAN_LABELS[item.subscription.plan]}
                                </span>
                                <span className="favorites-list-item-meta">
                                    {item.subscription.status === "REQUESTED"
                                        ? `Demandé le ${formatDay(item.subscription.requestedAt)}`
                                        : item.subscription.accessEndsAt
                                            ? `Accès jusqu'au ${formatDay(item.subscription.accessEndsAt)}`
                                            : "Sans échéance"}
                                    {item.subscription.status === "CANCELED" ? " · résilié" : ""}
                                </span>
                            </div>
                            <div className="contributions-list-item-actions">
                                {item.subscription.status === "REQUESTED" ? (
                                    <>
                                        <label>
                                            Fin d'accès (facultative){" "}
                                            <input type="date" value={endsOn[item.subscription.id] ?? ""}
                                                onChange={(event) => setEndsOn({
                                                    ...endsOn, [item.subscription.id]: event.target.value,
                                                })} />
                                        </label>
                                        <button className="admin-approve-button" onClick={() => void decide(item, "approve")}
                                            type="button">
                                            Activer
                                        </button>
                                        <button className="admin-reject-button" onClick={() => void decide(item, "reject")}
                                            type="button">
                                            Refuser
                                        </button>
                                    </>
                                ) : (
                                    <button className="admin-reject-button" onClick={() => void decide(item, "revoke")}
                                        type="button">
                                        Retirer l'accès
                                    </button>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </>
    );
}

export default AdminSubscriptionsSection;
