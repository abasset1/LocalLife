import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { placeTypeLabel } from "../places/placeTypes";
import { formatDate, proRequest } from "./proApi";
import { CLAIM_STATUS_LABELS } from "./types";
import type { Claim, SimilarResult } from "./types";

/** Lieu visé par une demande. */
export interface ClaimTarget {
    id: number;
    name: string;
    city: string | null;
}

const EDITABLE = new Set(["DRAFT", "INFO_REQUESTED"]);
const WITHDRAWABLE = new Set(["DRAFT", "SUBMITTED", "INFO_REQUESTED"]);

/**
 * Parcours A (ADR-0012) : demander la gestion d'un lieu existant. Le professionnel retrouve le lieu, explique son
 * lien avec lui et joint des justificatifs (texte ou liens) ; la demande est examinée par LocalLife. Rien ne change
 * pour le lieu avant l'approbation.
 */
function ProClaimsSection({ initialTarget }: { initialTarget: ClaimTarget | null }) {
    const [claims, setClaims] = useState<Claim[]>([]);
    const [refreshKey, setRefreshKey] = useState(0);
    const [searchName, setSearchName] = useState("");
    const [searchCity, setSearchCity] = useState("");
    const [results, setResults] = useState<SimilarResult | null>(null);
    const [target, setTarget] = useState<ClaimTarget | null>(initialTarget);
    const [editing, setEditing] = useState<Claim | null>(null);
    const [relationship, setRelationship] = useState("");
    const [evidence, setEvidence] = useState("");
    const [message, setMessage] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => setTarget(initialTarget), [initialTarget]);

    useEffect(() => {
        void proRequest<Claim[]>("/api/v1/pro/claims").then((result) => {
            if (result.ok) {
                setClaims(result.data);
            } else {
                setMessage(result.message);
            }
        });
    }, [refreshKey]);

    async function search(event: FormEvent) {
        event.preventDefault();
        const params = new URLSearchParams({ name: searchName, city: searchCity });
        const result = await proRequest<SimilarResult>(`/api/v1/pro/places/similar?${params}`);
        if (result.ok) {
            setResults(result.data);
            setMessage(null);
        } else {
            setMessage(result.message);
        }
    }

    function startClaim(next: ClaimTarget) {
        setTarget(next);
        setEditing(null);
        setRelationship("");
        setEvidence("");
        setMessage(null);
    }

    function startEdit(claim: Claim) {
        setEditing(claim);
        setTarget(null);
        setRelationship(claim.relationship ?? "");
        setEvidence(claim.evidence ?? "");
        setMessage(null);
    }

    /** Enregistre la demande (brouillon), puis l'envoie si demandé. */
    async function save(event: { preventDefault(): void }, send: boolean) {
        event.preventDefault();
        setBusy(true);
        const text = { relationship, evidence };
        const saved = editing
            ? await proRequest<Claim>(`/api/v1/pro/claims/${editing.id}`, "PATCH", text)
            : await proRequest<Claim>(`/api/v1/pro/places/${target!.id}/claims`, "POST", text);
        let result = saved;
        if (saved.ok && send) {
            result = await proRequest<Claim>(`/api/v1/pro/claims/${saved.data.id}/submit`, "POST");
        }
        setBusy(false);
        if (result.ok) {
            setMessage(send ? "Demande envoyée : LocalLife l'examinera." : "Brouillon enregistré.");
            setTarget(null);
            setEditing(null);
            setRefreshKey((key) => key + 1);
        } else {
            setMessage(result.message);
            if (saved.ok) {
                setRefreshKey((key) => key + 1);
            }
        }
    }

    async function act(claim: Claim, action: "submit" | "withdraw") {
        if (action === "withdraw" && !window.confirm(`Retirer la demande pour « ${claim.placeName} » ?`)) {
            return;
        }
        const result = await proRequest<Claim>(`/api/v1/pro/claims/${claim.id}/${action}`, "POST");
        setMessage(result.ok ? null : result.message);
        setRefreshKey((key) => key + 1);
    }

    const formTitle = editing ? editing.placeName : target?.name;

    return (
        <section aria-labelledby="pro-claims-title">
            <h2 id="pro-claims-title">Demander la gestion d'un lieu existant</h2>
            <p className="activities-status">
                Ton lieu est déjà sur LocalLife ? Retrouve-le et demande à le gérer. La fiche reste inchangée tant que
                LocalLife n'a pas approuvé ta demande.
            </p>

            {!formTitle && (
                <form className="contribution-form" onSubmit={(event) => void search(event)}>
                    <div className="form-field">
                        <label htmlFor="claim-search-name">Nom du lieu</label>
                        <input id="claim-search-name" onChange={(event) => setSearchName(event.target.value)}
                            required type="text" value={searchName} />
                    </div>
                    <div className="form-field">
                        <label htmlFor="claim-search-city">Commune</label>
                        <input id="claim-search-city" onChange={(event) => setSearchCity(event.target.value)}
                            required type="text" value={searchCity} />
                    </div>
                    <button className="admin-approve-button" type="submit">Rechercher</button>
                </form>
            )}

            {!formTitle && results && (
                results.candidates.length === 0 ? (
                    <p className="activities-status">Aucun lieu trouvé : tu peux le créer depuis « Créer un lieu ».</p>
                ) : (
                    <ul className="favorites-list">
                        {results.candidates.map(({ place }) => (
                            <li className="favorites-list-item contributions-list-item" key={place.id}>
                                <div className="favorites-list-item-info">
                                    <span className="favorites-list-item-title">{place.name}</span>
                                    <span className="favorites-list-item-meta">
                                        {placeTypeLabel(place.placeType)} · {place.address ?? ""} {place.city ?? ""}
                                    </span>
                                </div>
                                <button className="contribution-action" type="button"
                                    onClick={() => startClaim({ id: place.id, name: place.name, city: place.city })}>
                                    Demander la gestion de ce lieu
                                </button>
                            </li>
                        ))}
                    </ul>
                )
            )}

            {formTitle && (
                <form className="contribution-form" onSubmit={(event) => void save(event, true)}>
                    <h3>Demande pour « {formTitle} »</h3>
                    {editing?.decisionReason && editing.status === "INFO_REQUESTED" && (
                        <p className="activities-status">Demande de LocalLife : {editing.decisionReason}</p>
                    )}
                    <div className="form-field">
                        <label htmlFor="claim-relationship">Ton lien avec le lieu</label>
                        <textarea id="claim-relationship" maxLength={2000} required
                            onChange={(event) => setRelationship(event.target.value)}
                            placeholder="Ex. : je suis le gérant de l'établissement depuis 2019."
                            value={relationship} />
                    </div>
                    <div className="form-field">
                        <label htmlFor="claim-evidence">Justificatifs</label>
                        <textarea id="claim-evidence" maxLength={4000}
                            onChange={(event) => setEvidence(event.target.value)}
                            placeholder="Numéro SIRET, lien vers le site officiel, adresse email professionnelle…"
                            value={evidence} />
                        <small>Visibles de toi et de l'équipe LocalLife seulement, effacés 12 mois après la décision.</small>
                    </div>
                    <div className="modal-actions">
                        <button className="contribution-action" disabled={busy} type="button"
                            onClick={(event) => void save(event, false)}>
                            Enregistrer le brouillon
                        </button>
                        <button className="admin-approve-button" disabled={busy} type="submit">Envoyer la demande</button>
                        <button className="contribution-action" type="button"
                            onClick={() => { setTarget(null); setEditing(null); }}>
                            Annuler
                        </button>
                    </div>
                </form>
            )}

            {message && <p className="activities-status" role="status">{message}</p>}

            <h3>Mes demandes</h3>
            {claims.length === 0 ? (
                <p className="activities-status">Aucune demande pour l'instant.</p>
            ) : (
                <ul className="favorites-list">
                    {claims.map((claim) => (
                        <li className="favorites-list-item contributions-list-item" key={claim.id}>
                            <div className="favorites-list-item-info">
                                <span className="favorites-list-item-title">{claim.placeName}</span>
                                <span className="favorites-list-item-meta">
                                    <span className={`contribution-status claim-status-${claim.status.toLowerCase()}`}>
                                        {CLAIM_STATUS_LABELS[claim.status]}
                                    </span>
                                    {claim.placeCity ? ` · ${claim.placeCity}` : ""}
                                    {` · mise à jour le ${formatDate(claim.updatedAt)}`}
                                </span>
                                {claim.decisionReason && <span className="favorites-list-item-meta">Motif : {claim.decisionReason}</span>}
                            </div>
                            <div className="contributions-list-item-actions">
                                {EDITABLE.has(claim.status) && (
                                    <button className="contribution-action" onClick={() => startEdit(claim)} type="button">
                                        Compléter
                                    </button>
                                )}
                                {EDITABLE.has(claim.status) && claim.relationship && (
                                    <button className="contribution-action" onClick={() => void act(claim, "submit")} type="button">
                                        Envoyer
                                    </button>
                                )}
                                {WITHDRAWABLE.has(claim.status) && (
                                    <button className="contribution-action contribution-action-danger" type="button"
                                        onClick={() => void act(claim, "withdraw")}>
                                        Retirer
                                    </button>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

export default ProClaimsSection;
