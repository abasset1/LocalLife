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

const TABS = [
    { value: "REQUESTED", label: "Demandes" },
    { value: "ACTIVE,CANCELED", label: "En cours" },
];

/**
 * Abonnements (BILLING-01, ADR-0014) : sans paiement pour l'instant, un administrateur active les demandes de
 * passage à Premium ou Pro (avec une date de fin facultative), les refuse avec un motif, ou retire un accès.
 */
function AdminSubscriptionsSection() {
    const [tab, setTab] = useState(TABS[0].value);
    const [items, setItems] = useState<AdminSubscription[]>([]);
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
            <h3>Abonnements</h3>
            <p className="activities-status">Pas de paiement pour l'instant : l'activation est manuelle.</p>
            <nav className="admin-tabs">
                {TABS.map((item) => (
                    <button className={item.value === tab ? "admin-tab admin-tab-active" : "admin-tab"} key={item.value}
                        onClick={() => setTab(item.value)} type="button">
                        {item.label}
                    </button>
                ))}
            </nav>
            {message && <p className="activities-status" role="status">{message}</p>}
            {items.length === 0 ? (
                <p className="activities-status">Rien à afficher.</p>
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
