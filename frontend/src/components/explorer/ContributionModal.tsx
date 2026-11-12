import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import type { Category, UserLimits } from "./types";

/**
 * Passage hebdomadaire d'une contribution (food truck, marché…) : envoyé comme `recurrence`
 * (`daysOfWeek` en codes RFC 5545, horaires `HH:mm`) ; les dates du formulaire en deviennent les bornes.
 */
export interface ContributionRecurrence {
    enabled: boolean;
    days: string[];
    startTime: string;
    endTime: string;
}

/** Lieu publié géré par l'auteur, proposable comme lieu hôte de l'activité. */
export interface HostPlaceOption {
    id: number;
    name: string;
}

export const EMPTY_RECURRENCE: ContributionRecurrence = { enabled: false, days: [], startTime: "", endTime: "" };

interface ContributionModalProps {
    /** `edit` : modification d'une contribution existante (titre et bouton adaptés). */
    mode: "create" | "edit";
    /** Correction d'une activité refusée par son contributeur : elle repartira en validation. */
    requiresRevalidation: boolean;
    categories: Category[];
    title: string;
    onTitleChange: (value: string) => void;
    description: string;
    onDescriptionChange: (value: string) => void;
    /** Codes des catégories du référentiel cochées (`categoryCodes` côté API), au moins une. */
    selectedCategoryCodes: string[];
    onSelectedCategoryCodesChange: (codes: string[]) => void;
    address: string;
    onAddressChange: (value: string) => void;
    startDate: string;
    onStartDateChange: (value: string) => void;
    endDate: string;
    onEndDateChange: (value: string) => void;
    recurrence: ContributionRecurrence;
    url: string;
    onUrlChange: (value: string) => void;
    phone: string;
    onPhoneChange: (value: string) => void;
    email: string;
    onEmailChange: (value: string) => void;
    /** Lieux publiés gérés (compte pro) ; liste vide : le champ « Proposé par » n'est pas affiché. */
    hostPlaces?: HostPlaceOption[];
    /** Limites de l'offre : compteur des contributions du mois (Premium) ; `null` si inconnues. */
    contributionLimits?: UserLimits | null;
    hostPlaceId?: number | null;
    onHostPlaceIdChange?: (value: number | null) => void;
    submitStatus: "idle" | "success" | "error";
    submitError: string | null;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    onClose: () => void;
}

/**
 * LL-EF-001/LL-EF-016 : formulaire de proposition d'activité, extrait de
 * App.tsx lors de la refonte Explorer (LL-EF-017) — mêmes champs et même
 * logique de validation (déléguée au backend), classes `explorer-*` dédiées
 * pour ne pas affecter le formulaire équivalent d'AdminPage (`.contribution-form`).
 */
function ContributionModal({
    mode,
    requiresRevalidation,
    categories,
    title,
    onTitleChange,
    description,
    onDescriptionChange,
    selectedCategoryCodes,
    onSelectedCategoryCodesChange,
    address,
    onAddressChange,
    startDate,
    onStartDateChange,
    endDate,
    onEndDateChange,
    recurrence,
    url,
    onUrlChange,
    phone,
    onPhoneChange,
    email,
    onEmailChange,
    hostPlaces = [],
    contributionLimits = null,
    hostPlaceId = null,
    onHostPlaceIdChange,
    submitStatus,
    submitError,
    onSubmit,
    onClose,
}: ContributionModalProps) {
    function toggleCategory(code: string) {
        onSelectedCategoryCodesChange(
            selectedCategoryCodes.includes(code)
                ? selectedCategoryCodes.filter((selected) => selected !== code)
                : [...selectedCategoryCodes, code],
        );
    }

    // Limite mensuelle (Premium) : une modification ne compte pas, seule une nouvelle proposition est bloquée.
    const monthlyLimit = mode === "create" ? contributionLimits?.monthlyContributions ?? null : null;
    const isAtMonthlyLimit = monthlyLimit !== null && monthlyLimit > 0
        && (contributionLimits?.contributionsThisMonth ?? 0) >= monthlyLimit;

    return (
        <div className="explorer-modal-overlay" onClick={onClose} role="presentation">
            <div
                aria-labelledby="explorer-contribution-modal-title"
                aria-modal="true"
                className="explorer-modal-dialog explorer-modal-dialog-form"
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <form className="pro-form pro-form-modal" onSubmit={onSubmit}>
                    <header className="pro-form-header pro-form-modal-header">
                        <span aria-hidden="true" className="pro-form-header-icon">{mode === "edit" ? "✏️" : "📅"}</span>
                        <div>
                            <h2 id="explorer-contribution-modal-title">
                                {mode === "edit" ? "Modifier l'activité" : "Proposer une activité"}
                            </h2>
                            <p>Un événement, un marché, un concert… partage-le avec la communauté.</p>
                        </div>
                        <button aria-label="Fermer" className="pro-form-modal-close" onClick={onClose} type="button">
                            ✕
                        </button>
                    </header>

                    <div className="pro-form-modal-body">
                        {mode === "create" && (
                            <p className="pro-notice pro-notice-info">
                                Ton activité sera soumise à validation par un modérateur avant d'apparaître sur la
                                carte : elle sera publiée d'ici 2 à 3 jours.
                            </p>
                        )}
                        {monthlyLimit !== null && monthlyLimit > 0 && (
                            <p className="pro-notice">
                                Contributions ce mois-ci : {contributionLimits?.contributionsThisMonth ?? 0} / {monthlyLimit}
                            </p>
                        )}
                        {isAtMonthlyLimit && (
                            <p className="pro-notice pro-notice-error" role="alert">
                                Tu as atteint la limite de {monthlyLimit} contributions ce mois-ci avec ton offre. Les
                                propositions refusées ne comptent pas. <Link to="/profile">L'offre Pro permet des
                                contributions illimitées.</Link>
                            </p>
                        )}
                        {mode === "edit" && requiresRevalidation && (
                            <p className="pro-notice pro-notice-warning">
                                Après modification, cette activité sera de nouveau soumise à validation : elle
                                n'apparaîtra sur la carte qu'une fois validée par un modérateur.
                            </p>
                        )}

                        <section className="pro-form-section">
                            <h3 className="pro-form-section-title">L'activité</h3>
                            <div className="form-field">
                                <label htmlFor="explorer-activity-title">Titre</label>
                                <input
                                    id="explorer-activity-title"
                                    onChange={(event) => onTitleChange(event.target.value)}
                                    placeholder="Marché nocturne, concert, vide-grenier…"
                                    required
                                    type="text"
                                    value={title}
                                />
                            </div>
                            <div className="form-field">
                                <label htmlFor="explorer-activity-description">Description</label>
                                <textarea
                                    id="explorer-activity-description"
                                    onChange={(event) => onDescriptionChange(event.target.value)}
                                    placeholder="Ce qui s'y passe, pour qui, ce qu'il faut savoir…"
                                    required
                                    rows={4}
                                    value={description}
                                />
                            </div>
                            <fieldset className="pro-fieldset">
                                <legend className="pro-field-label">
                                    Catégories <span className="pro-optional">· au moins une</span>
                                </legend>
                                <div className="pro-day-chips">
                                    {categories.map((item) => (
                                        <label className="pro-day-chip" key={item.code}>
                                            <input
                                                checked={selectedCategoryCodes.includes(item.code)}
                                                onChange={() => toggleCategory(item.code)}
                                                type="checkbox"
                                            />
                                            <span>{item.label}</span>
                                        </label>
                                    ))}
                                </div>
                            </fieldset>
                        </section>

                        <section className="pro-form-section">
                            <h3 className="pro-form-section-title">Où et quand</h3>
                            <div className="form-field">
                                <label htmlFor="explorer-activity-address">Adresse</label>
                                <input
                                    id="explorer-activity-address"
                                    onChange={(event) => onAddressChange(event.target.value)}
                                    placeholder="Ex : 10 rue de la République, Marseille"
                                    required
                                    type="text"
                                    value={address}
                                />
                            </div>
                            {hostPlaces.length > 0 && onHostPlaceIdChange && (
                                <div className="form-field">
                                    <label htmlFor="explorer-activity-host-place">
                                        Proposé par un de mes lieux <span className="pro-optional">(facultatif)</span>
                                    </label>
                                    <select
                                        id="explorer-activity-host-place"
                                        onChange={(event) => onHostPlaceIdChange(
                                            event.target.value === "" ? null : Number(event.target.value))}
                                        value={hostPlaceId ?? ""}
                                    >
                                        <option value="">Aucun</option>
                                        {hostPlaces.map((place) => (
                                            <option key={place.id} value={place.id}>{place.name}</option>
                                        ))}
                                        {/* Lieu enregistré qui n'est plus publié : gardé tant qu'il n'est pas changé. */}
                                        {hostPlaceId !== null && !hostPlaces.some((place) => place.id === hostPlaceId) && (
                                            <option value={hostPlaceId}>Lieu actuel (n° {hostPlaceId})</option>
                                        )}
                                    </select>
                                </div>
                            )}
                            {/*
                              Plus de jours ni d'horaires hebdomadaires dans le formulaire, ni en création ni en
                              modification. Une contribution déjà récurrente garde son passage hebdomadaire tel quel :
                              seules ses dates (« À partir du », « Jusqu'au ») sont modifiables.
                            */}
                            <div className="pro-form-grid">
                                <div className="form-field">
                                    <label htmlFor="explorer-activity-start-date">
                                        {recurrence.enabled ? "À partir du (facultatif, aujourd'hui par défaut)" : "Date de début"}
                                    </label>
                                    <input
                                        id="explorer-activity-start-date"
                                        onChange={(event) => onStartDateChange(event.target.value)}
                                        type="date"
                                        value={startDate}
                                    />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="explorer-activity-end-date">
                                        {recurrence.enabled ? "Jusqu'au (facultatif)" : "Date de fin"}
                                    </label>
                                    <input
                                        id="explorer-activity-end-date"
                                        onChange={(event) => onEndDateChange(event.target.value)}
                                        type="date"
                                        value={endDate}
                                    />
                                </div>
                            </div>
                        </section>

                        <section className="pro-form-section">
                            <h3 className="pro-form-section-title">
                                Contact <span className="pro-optional">(facultatif)</span>
                            </h3>
                            <div className="pro-form-grid">
                                <div className="form-field pro-field-wide">
                                    <label htmlFor="explorer-activity-url">Site web (facultatif)</label>
                                    <input
                                        id="explorer-activity-url"
                                        onChange={(event) => onUrlChange(event.target.value)}
                                        placeholder="https://…"
                                        type="text"
                                        value={url}
                                    />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="explorer-activity-phone">Téléphone (facultatif)</label>
                                    <input
                                        id="explorer-activity-phone"
                                        onChange={(event) => onPhoneChange(event.target.value)}
                                        type="tel"
                                        value={phone}
                                    />
                                </div>
                                <div className="form-field">
                                    <label htmlFor="explorer-activity-email">Email (facultatif)</label>
                                    <input
                                        id="explorer-activity-email"
                                        onChange={(event) => onEmailChange(event.target.value)}
                                        type="email"
                                        value={email}
                                    />
                                </div>
                            </div>
                        </section>
                    </div>

                    {/* Pied fixe : le bouton d'envoi reste visible quelle que soit la hauteur de la fenêtre. */}
                    <footer className="pro-form-modal-footer">
                        {submitStatus === "success" && (
                            <p className="pro-notice pro-notice-success" role="status">Activité proposée !</p>
                        )}
                        {submitStatus === "error" && (
                            <p className="pro-notice pro-notice-error" role="alert">{submitError}</p>
                        )}
                        <div className="pro-form-modal-actions">
                            <button className="pro-button pro-button-ghost" onClick={onClose} type="button">Annuler</button>
                            <button className="pro-button pro-button-primary" disabled={isAtMonthlyLimit} type="submit">
                                {mode === "edit" ? "Enregistrer les modifications" : "Proposer l'activité"}
                            </button>
                        </div>
                    </footer>
                </form>
            </div>
        </div>
    );
}

export default ContributionModal;
