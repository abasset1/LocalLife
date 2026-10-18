import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { placeTypeLabel } from "../places/placeTypes";
import { formatDate, proRequest } from "../pro/proApi";
import { CLAIM_STATUS_LABELS } from "../pro/types";
import type { AdminClaim, ClaimReview, ClaimStatus } from "../pro/types";

interface UserAccount {
    id: number;
    username: string;
    email: string;
    role: "USER" | "PRO" | "ADMIN";
}

const QUEUE_TABS: { value: string; label: string }[] = [
    { value: "SUBMITTED,IN_REVIEW", label: "À traiter" },
    { value: "INFO_REQUESTED", label: "Informations demandées" },
    { value: "APPROVED", label: "Approuvées" },
    { value: "REJECTED", label: "Refusées" },
];

const DECIDABLE = new Set<ClaimStatus>(["SUBMITTED", "IN_REVIEW"]);

/**
 * Professionnels (ADR-0012) : attribution du rôle PRO et modération des demandes de gestion de lieux. Le dossier
 * d'une demande montre son historique, les demandes concurrentes sur le même lieu et ses gestionnaires actuels ;
 * refuser ou demander des informations exige un motif, visible du demandeur.
 */
function AdminProSection() {
    const [email, setEmail] = useState("");
    const [account, setAccount] = useState<UserAccount | null>(null);
    const [accountMessage, setAccountMessage] = useState<string | null>(null);
    const [queue, setQueue] = useState(QUEUE_TABS[0].value);
    const [claims, setClaims] = useState<AdminClaim[]>([]);
    const [review, setReview] = useState<ClaimReview | null>(null);
    const [reason, setReason] = useState("");
    const [message, setMessage] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        void proRequest<AdminClaim[]>(`/api/v1/admin/place-claims?status=${queue}`).then((result) => {
            if (result.ok) {
                setClaims(result.data);
            } else {
                setMessage(result.message);
            }
        });
    }, [queue, refreshKey]);

    async function findAccount(event: FormEvent) {
        event.preventDefault();
        const result = await proRequest<UserAccount>(`/api/v1/admin/users?email=${encodeURIComponent(email.trim())}`);
        setAccount(result.ok ? result.data : null);
        setAccountMessage(result.ok ? null : result.status === 404 ? "Aucun compte avec cet email." : result.message);
    }

    async function changeRole(role: "USER" | "PRO") {
        if (!account) {
            return;
        }
        const result = await proRequest<UserAccount>(`/api/v1/admin/users/${account.id}/role`, "PATCH", { role });
        if (result.ok) {
            setAccount(result.data);
            setAccountMessage(role === "PRO" ? "Compte professionnel activé." : "Rôle professionnel retiré.");
        } else {
            setAccountMessage(result.message);
        }
    }

    async function open(claim: AdminClaim) {
        const result = await proRequest<ClaimReview>(`/api/v1/admin/place-claims/${claim.id}`);
        if (result.ok) {
            setReview(result.data);
            setReason("");
            setMessage(null);
        } else {
            setMessage(result.message);
        }
    }

    async function decide(action: "start-review" | "approve" | "reject" | "request-info") {
        if (!review) {
            return;
        }
        setBusy(true);
        const result = await proRequest<AdminClaim>(`/api/v1/admin/place-claims/${review.claim.id}/${action}`, "POST",
            { reason: reason.trim() === "" ? null : reason.trim() });
        setBusy(false);
        if (result.ok) {
            setMessage(`Demande « ${review.claim.placeName} » : ${CLAIM_STATUS_LABELS[result.data.status]}.`);
            setRefreshKey((key) => key + 1);
            if (action === "start-review") {
                void open(result.data);
            } else {
                setReview(null);
            }
        } else {
            setMessage(result.message);
        }
    }

    return (
        <section aria-labelledby="admin-pro-title">
            <h2 id="admin-pro-title">Professionnels</h2>

            <h3>Comptes professionnels</h3>
            <form className="contribution-form" onSubmit={(event) => void findAccount(event)}>
                <div className="form-field">
                    <label htmlFor="pro-account-email">Email du compte</label>
                    <input id="pro-account-email" onChange={(event) => setEmail(event.target.value)} required
                        type="email" value={email} />
                </div>
                <button className="admin-approve-button" type="submit">Rechercher</button>
            </form>
            {account && (
                <div className="admin-activity-card">
                    <div className="admin-activity-header">
                        <h3>{account.username}</h3>
                        <span>{account.role}</span>
                    </div>
                    <p className="admin-activity-description">{account.email}</p>
                    {account.role !== "ADMIN" && (
                        <div className="admin-activity-actions">
                            {account.role === "USER" ? (
                                <button className="admin-approve-button" onClick={() => void changeRole("PRO")} type="button">
                                    Activer le compte professionnel
                                </button>
                            ) : (
                                <button className="admin-reject-button" onClick={() => void changeRole("USER")} type="button">
                                    Retirer le rôle professionnel
                                </button>
                            )}
                        </div>
                    )}
                </div>
            )}
            {accountMessage && <p className="activities-status" role="status">{accountMessage}</p>}

            <h3>Demandes de gestion de lieux</h3>
            <nav className="admin-tabs">
                {QUEUE_TABS.map((tab) => (
                    <button className={tab.value === queue ? "admin-tab admin-tab-active" : "admin-tab"} key={tab.value}
                        onClick={() => { setQueue(tab.value); setReview(null); }} type="button">
                        {tab.label}
                    </button>
                ))}
            </nav>
            {message && <p className="activities-status" role="status">{message}</p>}

            {review ? (
                <div className="admin-activity-card">
                    <div className="admin-activity-header">
                        <h3>{review.place.name}</h3>
                        <span>{CLAIM_STATUS_LABELS[review.claim.status]}</span>
                    </div>
                    <p className="admin-activity-description">
                        {placeTypeLabel(review.place.placeType)} · {review.place.address ?? ""} {review.place.city ?? ""}
                    </p>
                    <p className="admin-activity-description">
                        Demandeur : {review.claim.requesterEmail ?? "compte supprimé"} · envoyée le {formatDate(review.claim.submittedAt)}
                    </p>
                    <p className="admin-activity-description"><strong>Lien déclaré :</strong> {review.claim.relationship ?? "—"}</p>
                    <p className="admin-activity-description"><strong>Justificatifs :</strong> {review.claim.evidence ?? "—"}</p>
                    {review.managers.length > 0 && (
                        <p className="admin-activity-description">
                            Déjà géré par : {review.managers.map((manager) => manager.email).join(", ")}
                        </p>
                    )}
                    {review.concurrentClaims.length > 0 && (
                        <p className="admin-activity-description activities-status-error">
                            Autres demandes en cours sur ce lieu : {review.concurrentClaims
                                .map((other) => `${other.requesterEmail ?? "?"} (${CLAIM_STATUS_LABELS[other.status]})`).join(", ")}
                        </p>
                    )}
                    <details>
                        <summary>Historique</summary>
                        <ul>
                            {review.events.map((event, index) => (
                                <li key={index}>
                                    {new Date(event.occurredAt).toLocaleString("fr-FR")} — {CLAIM_STATUS_LABELS[event.toStatus]}
                                    {event.reason ? ` : ${event.reason}` : ""}
                                </li>
                            ))}
                        </ul>
                    </details>
                    {DECIDABLE.has(review.claim.status) && (
                        <>
                            <div className="form-field">
                                <label htmlFor="claim-reason">Motif (obligatoire pour refuser ou demander des informations)</label>
                                <textarea id="claim-reason" maxLength={2000} onChange={(event) => setReason(event.target.value)}
                                    value={reason} />
                            </div>
                            <div className="admin-activity-actions">
                                {review.claim.status === "SUBMITTED" && (
                                    <button className="contribution-action" disabled={busy} type="button"
                                        onClick={() => void decide("start-review")}>
                                        Prendre en charge
                                    </button>
                                )}
                                <button className="admin-approve-button" disabled={busy} onClick={() => void decide("approve")}
                                    type="button">
                                    Approuver
                                </button>
                                <button className="contribution-action" disabled={busy || reason.trim() === ""} type="button"
                                    onClick={() => void decide("request-info")}>
                                    Demander des informations
                                </button>
                                <button className="admin-reject-button" disabled={busy || reason.trim() === ""} type="button"
                                    onClick={() => void decide("reject")}>
                                    Refuser
                                </button>
                            </div>
                        </>
                    )}
                    <button className="contribution-action" onClick={() => setReview(null)} type="button">Fermer</button>
                </div>
            ) : claims.length === 0 ? (
                <p className="activities-status">Aucune demande.</p>
            ) : (
                <ul className="favorites-list">
                    {claims.map((claim) => (
                        <li className="favorites-list-item contributions-list-item" key={claim.id}>
                            <div className="favorites-list-item-info">
                                <span className="favorites-list-item-title">{claim.placeName}</span>
                                <span className="favorites-list-item-meta">
                                    {CLAIM_STATUS_LABELS[claim.status]} · {claim.requesterEmail ?? "compte supprimé"}
                                    {claim.submittedAt ? ` · envoyée le ${formatDate(claim.submittedAt)}` : ""}
                                </span>
                            </div>
                            <button className="contribution-action" onClick={() => void open(claim)} type="button">
                                Examiner
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

export default AdminProSection;
