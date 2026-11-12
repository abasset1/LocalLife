import { useState } from "react";
import type { FormEvent } from "react";
import { PLACE_TYPE_OPTIONS, placeTypeLabel } from "../places/placeTypes";
import { proRequest } from "./proApi";
import type { ClaimTarget } from "./ProClaimsSection";
import type { ManagedPlace, SimilarPlace, SimilarResult } from "./types";

const EMPTY = {
    name: "", placeType: "", address: "", postalCode: "", city: "", description: "", website: "", phone: "", email: "",
};

type Step = "form" | "check" | "preview";

const STEPS: { value: Step; label: string }[] = [
    { value: "form", label: "Description" },
    { value: "check", label: "Vérification" },
    { value: "preview", label: "Aperçu" },
];

/**
 * Parcours B (ADR-0012) : créer un lieu absent de Neiri. Recherche préalable des lieux proches (même endroit ou
 * même nom dans la commune) avant tout enregistrement, aperçu, puis brouillon ; l'envoi à la modération se fait
 * depuis « Mes lieux ». Un lieu existant n'est jamais modifié ni réutilisé.
 */
function ProNewPlaceSection({ onClaim, onCreated }: {
    onClaim: (target: ClaimTarget) => void;
    onCreated: () => void;
}) {
    const [fields, setFields] = useState(EMPTY);
    const [step, setStep] = useState<Step>("form");
    const [check, setCheck] = useState<SimilarResult | null>(null);
    const [latitude, setLatitude] = useState("");
    const [longitude, setLongitude] = useState("");
    const [reportedSimilarTo, setReportedSimilarTo] = useState<number | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    function set(name: keyof typeof EMPTY, value: string) {
        setFields((current) => ({ ...current, [name]: value }));
    }

    async function runCheck(event: FormEvent) {
        event.preventDefault();
        setBusy(true);
        const params = new URLSearchParams({
            name: fields.name, address: fields.address, postalCode: fields.postalCode, city: fields.city,
        });
        const result = await proRequest<SimilarResult>(`/api/v1/pro/places/similar?${params}`);
        setBusy(false);
        if (!result.ok) {
            setMessage(result.message);
            return;
        }
        setCheck(result.data);
        setLatitude(result.data.latitude?.toString() ?? "");
        setLongitude(result.data.longitude?.toString() ?? "");
        setReportedSimilarTo(null);
        setMessage(null);
        setStep("check");
    }

    async function saveDraft() {
        setBusy(true);
        const result = await proRequest<ManagedPlace>("/api/v1/pro/places", "POST", {
            ...fields,
            latitude: latitude === "" ? null : Number(latitude),
            longitude: longitude === "" ? null : Number(longitude),
            confirmNotDuplicate: true,
            reportedSimilarTo,
        });
        setBusy(false);
        if (result.ok) {
            setFields(EMPTY);
            setCheck(null);
            setStep("form");
            setMessage(`« ${result.data.name} » enregistré en brouillon. Envoie-le à la modération depuis « Mes lieux ».`);
            onCreated();
        } else {
            setMessage(result.message);
        }
    }

    const hasPosition = latitude !== "" && longitude !== "";

    return (
        <section aria-labelledby="pro-new-place-title">
            <h2 id="pro-new-place-title">Créer un lieu</h2>

            <div className="pro-form">
                <ol aria-label="Étapes" className="pro-steps">
                    {STEPS.map((item, index) => {
                        const current = STEPS.findIndex((candidate) => candidate.value === step);
                        const state = index === current ? "current" : index < current ? "done" : "todo";
                        return (
                            <li aria-current={state === "current" ? "step" : undefined} className={`pro-step pro-step-${state}`}
                                key={item.value}>
                                <span aria-hidden="true" className="pro-step-dot">{state === "done" ? "✓" : index + 1}</span>
                                {item.label}
                            </li>
                        );
                    })}
                </ol>

                {step === "form" && (
                    <form className="pro-form-body" onSubmit={(event) => void runCheck(event)}>
                        <p className="pro-intro">
                            Commence par décrire le lieu : Neiri vérifie d'abord qu'il n'existe pas déjà.
                        </p>
                        <section className="pro-form-section">
                            <h3 className="pro-form-section-title">Le lieu</h3>
                            <div className="pro-form-grid">
                                <div className="form-field">
                                    <label htmlFor="place-name">Nom</label>
                                    <input id="place-name" maxLength={255} onChange={(event) => set("name", event.target.value)}
                                        placeholder="Musée, salle, librairie…" required type="text" value={fields.name} />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="place-type">Catégorie</label>
                                    <select id="place-type" onChange={(event) => set("placeType", event.target.value)} required
                                        value={fields.placeType}>
                                        <option value="">Choisir…</option>
                                        {PLACE_TYPE_OPTIONS.map((option) => (
                                            <option key={option.value} value={option.value}>{option.label}</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="form-field pro-field-wide">
                                    <label htmlFor="place-description">
                                        Description <span className="pro-optional">(facultatif)</span>
                                    </label>
                                    <textarea id="place-description" maxLength={5000} rows={4}
                                        placeholder="Ce qu'on y trouve, l'ambiance, ce qui le rend unique…"
                                        onChange={(event) => set("description", event.target.value)} value={fields.description} />
                                    <small className="pro-hint pro-counter">{fields.description.length} / 5000</small>
                                </div>
                            </div>
                        </section>
                        <section className="pro-form-section">
                            <h3 className="pro-form-section-title">Adresse</h3>
                            <div className="pro-form-grid">
                                <div className="form-field pro-field-wide">
                                    <label htmlFor="place-address">Adresse</label>
                                    <input id="place-address" maxLength={512} onChange={(event) => set("address", event.target.value)}
                                        placeholder="65 rue Joseph Vernet" required type="text" value={fields.address} />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="place-postal-code">Code postal</label>
                                    <input id="place-postal-code" inputMode="numeric" onChange={(event) => set("postalCode", event.target.value)}
                                        pattern="\d{5}" placeholder="84000" type="text" value={fields.postalCode} />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="place-city">Commune</label>
                                    <input id="place-city" maxLength={255} onChange={(event) => set("city", event.target.value)}
                                        required type="text" value={fields.city} />
                                </div>
                            </div>
                        </section>
                        <section className="pro-form-section">
                            <h3 className="pro-form-section-title">Coordonnées <span className="pro-optional">(facultatif)</span></h3>
                            <div className="pro-form-grid">
                                <div className="form-field pro-field-wide">
                                    <label htmlFor="place-website">Site web</label>
                                    <input id="place-website" onChange={(event) => set("website", event.target.value)}
                                        placeholder="https://" type="url" value={fields.website} />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="place-phone">Téléphone</label>
                                    <input id="place-phone" onChange={(event) => set("phone", event.target.value)}
                                        placeholder="04 90 00 00 00" type="tel" value={fields.phone} />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="place-email">Email professionnel</label>
                                    <input id="place-email" onChange={(event) => set("email", event.target.value)}
                                        placeholder="contact@monlieu.fr" type="email" value={fields.email} />
                                </div>
                            </div>
                        </section>
                        <div className="pro-form-actions">
                            <button className="pro-button pro-button-primary" disabled={busy} type="submit">
                                {busy ? "Vérification…" : "Vérifier et continuer"}
                            </button>
                        </div>
                    </form>
                )}

                {step === "check" && check && (
                    <div className="pro-form-body">
                        {check.candidates.length === 0 ? (
                            <p className="pro-notice pro-notice-success">Aucun lieu proche ou de même nom n'existe déjà.</p>
                        ) : (
                            <>
                                <p className="pro-notice pro-notice-warning">
                                    Ces lieux existent déjà. Si l'un d'eux est le tien, demande plutôt à le gérer.
                                </p>
                                <ul className="favorites-list">
                                    {check.candidates.map((candidate: SimilarPlace) => (
                                        <li className="favorites-list-item contributions-list-item" key={candidate.place.id}>
                                            <div className="favorites-list-item-info">
                                                <span className="favorites-list-item-title">{candidate.place.name}</span>
                                                <span className="favorites-list-item-meta">
                                                    {placeTypeLabel(candidate.place.placeType)} · {candidate.place.city ?? ""}
                                                    {candidate.distanceMeters !== null ? ` · à ${candidate.distanceMeters} m` : ""}
                                                </span>
                                                <label className="pro-check">
                                                    <input checked={reportedSimilarTo === candidate.place.id} type="checkbox"
                                                        onChange={(event) => setReportedSimilarTo(
                                                            event.target.checked ? candidate.place.id : null)} />
                                                    Différent du mien mais similaire : le signaler à Neiri
                                                </label>
                                            </div>
                                            <button className="pro-button pro-button-ghost" type="button" onClick={() => onClaim({
                                                id: candidate.place.id, name: candidate.place.name, city: candidate.place.city,
                                            })}>
                                                C'est mon lieu
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            </>
                        )}
                        <section className="pro-form-section">
                            <h3 className="pro-form-section-title">Position sur la carte</h3>
                            {!hasPosition && (
                                <p className="pro-notice pro-notice-error">
                                    L'adresse n'a pas pu être localisée : indique la position du lieu.
                                </p>
                            )}
                            <div className="pro-form-grid">
                                <div className="form-field">
                                    <label htmlFor="place-latitude">Latitude</label>
                                    <input id="place-latitude" onChange={(event) => setLatitude(event.target.value)} step="any"
                                        type="number" value={latitude} />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="place-longitude">Longitude</label>
                                    <input id="place-longitude" onChange={(event) => setLongitude(event.target.value)} step="any"
                                        type="number" value={longitude} />
                                </div>
                            </div>
                        </section>
                        <div className="pro-form-actions">
                            <button className="pro-button pro-button-ghost" onClick={() => setStep("form")} type="button">Modifier</button>
                            <button className="pro-button pro-button-primary" disabled={!hasPosition} onClick={() => setStep("preview")}
                                type="button">
                                {check.candidates.length === 0 ? "Aperçu" : "Mon lieu n'est pas dans la liste : aperçu"}
                            </button>
                        </div>
                    </div>
                )}

                {step === "preview" && (
                    <div className="pro-form-body">
                        <div className="pro-summary">
                            <div className="pro-summary-header">
                                <h3>{fields.name}</h3>
                                <span className="pro-badge">{placeTypeLabel(fields.placeType)}</span>
                            </div>
                            {fields.description && <p className="pro-summary-description">{fields.description}</p>}
                            <dl className="pro-summary-list">
                                <div>
                                    <dt>Adresse</dt>
                                    <dd>{fields.address}, {fields.postalCode} {fields.city}</dd>
                                </div>
                                {fields.website && <div><dt>Site web</dt><dd>{fields.website}</dd></div>}
                                {fields.phone && <div><dt>Téléphone</dt><dd>{fields.phone}</dd></div>}
                                {fields.email && <div><dt>Email</dt><dd>{fields.email}</dd></div>}
                                <div>
                                    <dt>Position</dt>
                                    <dd>{latitude}, {longitude}</dd>
                                </div>
                            </dl>
                        </div>
                        <div className="pro-form-actions">
                            <button className="pro-button pro-button-ghost" onClick={() => setStep("check")} type="button">Retour</button>
                            <button className="pro-button pro-button-primary" disabled={busy} onClick={() => void saveDraft()} type="button">
                                Enregistrer le brouillon
                            </button>
                        </div>
                    </div>
                )}
            </div>

            {message && <p className="activities-status" role="status">{message}</p>}
        </section>
    );
}

export default ProNewPlaceSection;
