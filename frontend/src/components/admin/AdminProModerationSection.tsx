import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { formatDate, proRequest } from "../pro/proApi";
import { PLACE_CHANGE_FIELDS, PLACE_CHANGE_STATUS_LABELS } from "../pro/types";
import type { AdminPlaceChange, PlaceChangeReview, PlaceManager } from "../pro/types";

/** Food truck en attente de validation (`AdminFoodTruckResponse`, backend). */
interface PendingFoodTruck {
    foodTruck: { id: number; name: string; category: string; description: string | null; contact: string | null };
    ownerId: number | null;
    ownerEmail: string | null;
}

/**
 * Gestionnaires d'un lieu, avec « Retirer » : le retrait exige un motif, envoyé par e-mail au professionnel
 * (`POST /api/v1/admin/places/{placeId}/managers/{userId}/revoke`).
 */
export function AdminPlaceManagersList({ placeId, managers, onRevoked }: {
    placeId: number;
    managers: PlaceManager[];
    onRevoked: (message: string) => void;
}) {
    async function revoke(manager: PlaceManager) {
        const reason = window.prompt(`Motif du retrait de ${manager.email} (envoyé au professionnel) :`);
        if (reason === null || reason.trim() === "") {
            return;
        }
        const result = await proRequest(`/api/v1/admin/places/${placeId}/managers/${manager.userId}/revoke`, "POST",
            { reason: reason.trim() });
        onRevoked(result.ok ? `${manager.email} ne gère plus ce lieu.` : result.message);
    }

    if (managers.length === 0) {
        return <p className="admin-activity-description">Aucun gestionnaire.</p>;
    }
    return (
        <ul className="favorites-list">
            {managers.map((manager) => (
                <li className="favorites-list-item contributions-list-item" key={manager.userId}>
                    <div className="favorites-list-item-info">
                        <span className="favorites-list-item-title">{manager.email}</span>
                        <span className="favorites-list-item-meta">Gestionnaire depuis le {formatDate(manager.grantedAt)}</span>
                    </div>
                    <button className="admin-reject-button" onClick={() => void revoke(manager)} type="button">
                        Retirer
                    </button>
                </li>
            ))}
        </ul>
    );
}

/**
 * Modération de l'espace professionnel : food trucks à valider avant leur première publication, propositions de
 * modification de lieux publiés (fiche actuelle et proposition côte à côte) et gestionnaires d'un lieu. Refuser ou
 * retirer exige un motif ; le serveur refuse (403) de valider sa propre proposition ou son propre food truck.
 */
function AdminProModerationSection() {
    const [trucks, setTrucks] = useState<PendingFoodTruck[]>([]);
    const [truckMessage, setTruckMessage] = useState<string | null>(null);
    const [truckRefresh, setTruckRefresh] = useState(0);
    const [changes, setChanges] = useState<AdminPlaceChange[]>([]);
    const [changeReview, setChangeReview] = useState<PlaceChangeReview | null>(null);
    const [changeReason, setChangeReason] = useState("");
    const [changeMessage, setChangeMessage] = useState<string | null>(null);
    const [changeRefresh, setChangeRefresh] = useState(0);
    const [busy, setBusy] = useState(false);
    const [managerPlaceId, setManagerPlaceId] = useState("");
    const [managers, setManagers] = useState<{ placeId: number; items: PlaceManager[] } | null>(null);
    const [managerMessage, setManagerMessage] = useState<string | null>(null);

    useEffect(() => {
        void proRequest<PendingFoodTruck[]>("/api/v1/admin/foodtrucks?status=PENDING").then((result) => {
            if (result.ok) {
                setTrucks(result.data);
            } else {
                setTruckMessage(result.message);
            }
        });
    }, [truckRefresh]);

    useEffect(() => {
        void proRequest<AdminPlaceChange[]>("/api/v1/admin/place-changes?status=PENDING").then((result) => {
            if (result.ok) {
                setChanges(result.data);
            } else {
                setChangeMessage(result.message);
            }
        });
    }, [changeRefresh]);

    async function reviewFoodTruck(item: PendingFoodTruck, action: "approve" | "reject") {
        let body: unknown;
        if (action === "reject") {
            const reason = window.prompt(`Motif du refus de « ${item.foodTruck.name} » (visible du professionnel) :`);
            if (reason === null || reason.trim() === "") {
                return;
            }
            body = { reason: reason.trim() };
        }
        const result = await proRequest(`/api/v1/admin/foodtrucks/${item.foodTruck.id}/${action}`, "POST", body);
        setTruckMessage(result.ok
            ? `« ${item.foodTruck.name} » ${action === "approve" ? "validé et publié" : "refusé"}.`
            : result.message);
        setTruckRefresh((key) => key + 1);
    }

    async function openChange(item: AdminPlaceChange) {
        const result = await proRequest<PlaceChangeReview>(`/api/v1/admin/place-changes/${item.change.id}`);
        if (result.ok) {
            setChangeReview(result.data);
            setChangeReason("");
            setChangeMessage(null);
        } else {
            setChangeMessage(result.message);
        }
    }

    async function decideChange(action: "approve" | "reject") {
        if (!changeReview) {
            return;
        }
        const change = changeReview.proposal.change;
        setBusy(true);
        const result = await proRequest(`/api/v1/admin/place-changes/${change.id}/${action}`, "POST",
            action === "reject" ? { reason: changeReason.trim() } : undefined);
        setBusy(false);
        if (result.ok) {
            setChangeMessage(`Proposition pour « ${change.placeName} » ${action === "approve" ? "validée" : "refusée"}.`);
            setChangeReview(null);
            setChangeRefresh((key) => key + 1);
        } else {
            setChangeMessage(result.message);
        }
    }

    async function loadManagers(event: FormEvent) {
        event.preventDefault();
        const placeId = Number(managerPlaceId);
        if (!placeId) {
            return;
        }
        const result = await proRequest<PlaceManager[]>(`/api/v1/admin/places/${placeId}/managers`);
        setManagers(result.ok ? { placeId, items: result.data } : null);
        setManagerMessage(result.ok ? null : result.message);
    }

    async function refreshChangeReviewManagers(message: string) {
        setChangeMessage(message);
        if (changeReview) {
            await openChange(changeReview.proposal);
            setChangeMessage(message);
        }
    }

    return (
        <>
            <h3>Food trucks à valider</h3>
            <p className="activities-status">
                Un food truck est vérifié une fois avant sa première publication ; ensuite, son propriétaire le publie
                directement. Refuser exige un motif, envoyé au professionnel.
            </p>
            {truckMessage && <p className="activities-status" role="status">{truckMessage}</p>}
            {trucks.length === 0 ? (
                <p className="activities-status">Aucun food truck en attente de validation.</p>
            ) : (
                <ul className="favorites-list">
                    {trucks.map((item) => (
                        <li className="favorites-list-item contributions-list-item" key={item.foodTruck.id}>
                            <div className="favorites-list-item-info">
                                <span className="favorites-list-item-title">🚚 {item.foodTruck.name}</span>
                                <span className="favorites-list-item-meta">
                                    {item.foodTruck.category}{item.ownerEmail ? ` · ${item.ownerEmail}` : ""}
                                    {item.foodTruck.contact ? ` · ${item.foodTruck.contact}` : ""}
                                </span>
                                {item.foodTruck.description && (
                                    <span className="favorites-list-item-meta">{item.foodTruck.description}</span>
                                )}
                            </div>
                            <div className="contributions-list-item-actions">
                                <button className="admin-approve-button" type="button"
                                    onClick={() => void reviewFoodTruck(item, "approve")}>
                                    Valider
                                </button>
                                <button className="admin-reject-button" type="button"
                                    onClick={() => void reviewFoodTruck(item, "reject")}>
                                    Refuser
                                </button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            <h3>Propositions de modification de lieux</h3>
            {changeMessage && <p className="activities-status" role="status">{changeMessage}</p>}
            {changeReview ? (
                <div className="admin-activity-card">
                    <div className="admin-activity-header">
                        <h3>{changeReview.proposal.change.placeName}</h3>
                        <span>{PLACE_CHANGE_STATUS_LABELS[changeReview.proposal.change.status]}</span>
                    </div>
                    <p className="admin-activity-description">
                        Proposée par {changeReview.proposal.requesterEmail ?? "compte supprimé"} le
                        {" "}{formatDate(changeReview.proposal.change.createdAt)}
                    </p>
                    <table className="admin-change-compare">
                        <thead>
                            <tr><th>Champ</th><th>Actuel</th><th>Proposé</th></tr>
                        </thead>
                        <tbody>
                            {PLACE_CHANGE_FIELDS.map(({ key, label }) => {
                                const current = changeReview.current[key] ?? "";
                                const proposed = changeReview.proposal.change[key] ?? "";
                                return (
                                    <tr key={key}>
                                        <th scope="row">{label}</th>
                                        <td>{current || "—"}</td>
                                        <td className={current !== proposed ? "admin-change-differs" : undefined}>
                                            {proposed || "—"}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                    <h4>Gestionnaires du lieu</h4>
                    <AdminPlaceManagersList managers={changeReview.managers} placeId={changeReview.current.id}
                        onRevoked={(text) => void refreshChangeReviewManagers(text)} />
                    {changeReview.proposal.change.status === "PENDING" && (
                        <>
                            <div className="form-field">
                                <label htmlFor="change-reason">Motif (obligatoire pour refuser)</label>
                                <textarea id="change-reason" maxLength={2000} value={changeReason}
                                    onChange={(event) => setChangeReason(event.target.value)} />
                            </div>
                            <div className="admin-activity-actions">
                                <button className="admin-approve-button" disabled={busy} type="button"
                                    onClick={() => void decideChange("approve")}>
                                    Valider
                                </button>
                                <button className="admin-reject-button" disabled={busy || changeReason.trim() === ""}
                                    onClick={() => void decideChange("reject")} type="button">
                                    Refuser
                                </button>
                            </div>
                        </>
                    )}
                    <button className="contribution-action" onClick={() => setChangeReview(null)} type="button">Fermer</button>
                </div>
            ) : changes.length === 0 ? (
                <p className="activities-status">Aucune proposition en attente.</p>
            ) : (
                <ul className="favorites-list">
                    {changes.map((item) => (
                        <li className="favorites-list-item contributions-list-item" key={item.change.id}>
                            <div className="favorites-list-item-info">
                                <span className="favorites-list-item-title">{item.change.placeName}</span>
                                <span className="favorites-list-item-meta">
                                    {item.requesterEmail ?? "compte supprimé"} · {formatDate(item.change.createdAt)}
                                </span>
                            </div>
                            <button className="contribution-action" onClick={() => void openChange(item)} type="button">
                                Examiner
                            </button>
                        </li>
                    ))}
                </ul>
            )}

            <h3>Gestionnaires d'un lieu</h3>
            <form className="contribution-form" onSubmit={(event) => void loadManagers(event)}>
                <div className="form-field">
                    <label htmlFor="managers-place-id">Identifiant du lieu</label>
                    <input id="managers-place-id" min={1} onChange={(event) => setManagerPlaceId(event.target.value)}
                        required type="number" value={managerPlaceId} />
                </div>
                <button className="admin-approve-button" type="submit">Afficher les gestionnaires</button>
            </form>
            {managerMessage && <p className="activities-status" role="status">{managerMessage}</p>}
            {managers && (
                <AdminPlaceManagersList managers={managers.items} placeId={managers.placeId}
                    onRevoked={(text) => {
                        setManagerMessage(text);
                        void proRequest<PlaceManager[]>(`/api/v1/admin/places/${managers.placeId}/managers`)
                            .then((result) => {
                                if (result.ok) {
                                    setManagers({ placeId: managers.placeId, items: result.data });
                                }
                            });
                    }} />
            )}
        </>
    );
}

export default AdminProModerationSection;
