import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { PLACE_TYPE_OPTIONS } from "../places/placeTypes";
import { formatDate, proRequest } from "./proApi";
import { PLACE_CHANGE_FIELDS, PLACE_CHANGE_STATUS_LABELS } from "./types";
import type { ManagedPlace, PlaceChange, PlaceChangeField } from "./types";

type ChangeFields = Record<PlaceChangeField, string>;

function fieldsOf(source: ManagedPlace | PlaceChange): ChangeFields {
    return {
        address: source.address ?? "", postalCode: source.postalCode ?? "", city: source.city ?? "",
        description: source.description ?? "", website: source.website ?? "", phone: source.phone ?? "",
        email: source.email ?? "",
    };
}

const FIELD_LABELS = Object.fromEntries(PLACE_CHANGE_FIELDS.map(({ key, label }) => [key, label])) as Record<PlaceChangeField, string>;

/** Champs modifiables d'un lieu, regroupés en sections (adresse, présentation, coordonnées). */
const FIELD_GROUPS: { title: string; fields: { key: PlaceChangeField; wide?: boolean; placeholder?: string }[] }[] = [
    {
        title: "Adresse",
        fields: [{ key: "address", wide: true }, { key: "postalCode", placeholder: "84000" }, { key: "city" }],
    },
    {
        title: "Présentation",
        fields: [{ key: "description", wide: true, placeholder: "Ce qu'on y trouve, l'ambiance, ce qui le rend unique…" }],
    },
    {
        title: "Coordonnées",
        fields: [{ key: "website", wide: true, placeholder: "https://" }, { key: "phone" }, { key: "email" }],
    },
];

function ChangeFieldInputs({ fields, idPrefix, onChange }: {
    fields: ChangeFields;
    idPrefix: string;
    onChange: (key: PlaceChangeField, value: string) => void;
}) {
    return (
        <>
            {FIELD_GROUPS.map((group) => (
                <section className="pro-form-section" key={group.title}>
                    <h4 className="pro-form-section-title">{group.title}</h4>
                    <div className="pro-form-grid">
                        {group.fields.map(({ key, wide, placeholder }) => (
                            <div className={wide ? "form-field pro-field-wide" : "form-field"} key={key}>
                                <label htmlFor={`${idPrefix}-${key}`}>{FIELD_LABELS[key]}</label>
                                {key === "description" ? (
                                    <textarea id={`${idPrefix}-${key}`} maxLength={5000} placeholder={placeholder} rows={4}
                                        value={fields[key]} onChange={(event) => onChange(key, event.target.value)} />
                                ) : (
                                    <input id={`${idPrefix}-${key}`} placeholder={placeholder} required={key === "city"}
                                        inputMode={key === "postalCode" ? "numeric" : undefined} value={fields[key]}
                                        type={key === "website" ? "url" : key === "email" ? "email" : key === "phone" ? "tel" : "text"}
                                        onChange={(event) => onChange(key, event.target.value)} />
                                )}
                            </div>
                        ))}
                    </div>
                </section>
            ))}
        </>
    );
}

/**
 * Lieu créé par le professionnel, encore en brouillon ou en attente de modération (`editable`) : modification directe
 * (`PUT /api/v1/pro/places/{id}`).
 */
export function ProPlaceEditForm({ place, onDone, onCancel }: {
    place: ManagedPlace;
    onDone: (message: string) => void;
    onCancel: () => void;
}) {
    const [name, setName] = useState(place.name);
    const [placeType, setPlaceType] = useState(place.placeType);
    const [fields, setFields] = useState(() => fieldsOf(place));
    const [message, setMessage] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    async function save(event: FormEvent) {
        event.preventDefault();
        setBusy(true);
        const result = await proRequest<ManagedPlace>(`/api/v1/pro/places/${place.id}`, "PUT", {
            name, placeType, ...fields, latitude: place.latitude, longitude: place.longitude,
        });
        setBusy(false);
        if (result.ok) {
            onDone(`« ${result.data.name} » modifié.`);
        } else {
            setMessage(result.message);
        }
    }

    return (
        <form className="pro-form pro-place-panel" onSubmit={(event) => void save(event)}>
            <header className="pro-form-header">
                <span aria-hidden="true" className="pro-form-header-icon">✏️</span>
                <div>
                    <h3>Modifier « {place.name} »</h3>
                    <p>Ce lieu n'est pas encore publié : tes modifications s'appliquent directement.</p>
                </div>
            </header>
            {message && <p className="pro-notice pro-notice-error" role="alert">{message}</p>}
            <section className="pro-form-section">
                <h4 className="pro-form-section-title">Le lieu</h4>
                <div className="pro-form-grid">
                    <div className="form-field">
                        <label htmlFor={`edit-${place.id}-name`}>Nom</label>
                        <input id={`edit-${place.id}-name`} maxLength={255} onChange={(event) => setName(event.target.value)}
                            required type="text" value={name} />
                    </div>
                    <div className="form-field">
                        <label htmlFor={`edit-${place.id}-type`}>Catégorie</label>
                        <select id={`edit-${place.id}-type`} onChange={(event) => setPlaceType(event.target.value)} required
                            value={placeType}>
                            {PLACE_TYPE_OPTIONS.map((option) => (
                                <option key={option.value} value={option.value}>{option.label}</option>
                            ))}
                        </select>
                    </div>
                </div>
            </section>
            <ChangeFieldInputs fields={fields} idPrefix={`edit-${place.id}`}
                onChange={(key, value) => setFields((current) => ({ ...current, [key]: value }))} />
            <div className="pro-form-actions">
                <button className="pro-button pro-button-ghost" onClick={onCancel} type="button">Annuler</button>
                <button className="pro-button pro-button-primary" disabled={busy} type="submit">
                    {busy ? "Enregistrement…" : "Enregistrer"}
                </button>
            </div>
        </form>
    );
}

/**
 * Lieu publié géré par le professionnel (`changeProposable`) : il ne se modifie pas directement, le professionnel
 * propose une modification (adresse, coordonnées, description) qu'un modérateur valide ou refuse. Une nouvelle
 * proposition remplace celle encore en attente ; l'historique montre la décision et son motif.
 */
export function ProPlaceChangesPanel({ place, onClose }: { place: ManagedPlace; onClose: () => void }) {
    const [changes, setChanges] = useState<PlaceChange[]>([]);
    const [refreshKey, setRefreshKey] = useState(0);
    const [fields, setFields] = useState(() => fieldsOf(place));
    const [message, setMessage] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        void proRequest<PlaceChange[]>(`/api/v1/pro/places/${place.id}/changes`).then((result) => {
            if (result.ok) {
                setChanges(result.data);
            } else {
                setMessage(result.message);
            }
        });
    }, [place.id, refreshKey]);

    async function propose(event: FormEvent) {
        event.preventDefault();
        setBusy(true);
        const result = await proRequest<PlaceChange>(`/api/v1/pro/places/${place.id}/changes`, "POST", fields);
        setBusy(false);
        setMessage(result.ok
            ? "Proposition envoyée : un modérateur la vérifie avant de mettre la fiche à jour."
            : result.message);
        if (result.ok) {
            setRefreshKey((key) => key + 1);
        }
    }

    async function withdraw(change: PlaceChange) {
        const result = await proRequest<PlaceChange>(`/api/v1/pro/place-changes/${change.id}/withdraw`, "POST");
        setMessage(result.ok ? "Proposition retirée." : result.message);
        setRefreshKey((key) => key + 1);
    }

    const hasPending = changes.some((change) => change.status === "PENDING");

    return (
        <section aria-labelledby={`place-changes-${place.id}`} className="pro-form pro-place-panel">
            <header className="pro-form-header">
                <span aria-hidden="true" className="pro-form-header-icon">📝</span>
                <div>
                    <h3 id={`place-changes-${place.id}`}>Proposer une modification de « {place.name} »</h3>
                    <p>Ce lieu est publié : tes modifications sont vérifiées par un modérateur avant d'apparaître.</p>
                </div>
            </header>
            <p className="pro-notice">
                Le nom et la catégorie ne se modifient pas ici.
                {hasPending ? " Une nouvelle proposition remplace celle en attente." : ""}
            </p>
            {message && <p className="pro-notice pro-notice-info" role="status">{message}</p>}
            <form className="pro-form-body" onSubmit={(event) => void propose(event)}>
                <ChangeFieldInputs fields={fields} idPrefix={`change-${place.id}`}
                    onChange={(key, value) => setFields((current) => ({ ...current, [key]: value }))} />
                <div className="pro-form-actions">
                    <button className="pro-button pro-button-ghost" onClick={onClose} type="button">Fermer</button>
                    <button className="pro-button pro-button-primary" disabled={busy} type="submit">Envoyer la proposition</button>
                </div>
            </form>

            <h4 className="pro-form-section-title">Mes propositions</h4>
            {changes.length === 0 ? (
                <p className="pro-hint">Aucune proposition pour ce lieu.</p>
            ) : (
                <ul className="favorites-list">
                    {changes.map((change) => (
                        <li className="favorites-list-item contributions-list-item" key={change.id}>
                            <div className="favorites-list-item-info">
                                <span className="favorites-list-item-title">
                                    {PLACE_CHANGE_STATUS_LABELS[change.status]} · {formatDate(change.createdAt)}
                                </span>
                                <span className="favorites-list-item-meta">
                                    {[change.address, change.postalCode, change.city].filter(Boolean).join(" ")}
                                </span>
                                {change.decisionReason && (
                                    <span className="favorites-list-item-meta">Motif : {change.decisionReason}</span>
                                )}
                            </div>
                            {change.status === "PENDING" && (
                                <div className="contributions-list-item-actions">
                                    <button className="contribution-action" onClick={() => void withdraw(change)}
                                        type="button">
                                        Retirer
                                    </button>
                                </div>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
