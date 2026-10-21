import type { FormEvent } from "react";
import type { Category } from "./types";

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

export const EMPTY_RECURRENCE: ContributionRecurrence = { enabled: false, days: [], startTime: "", endTime: "" };

const RECURRENCE_DAYS: { code: string; label: string }[] = [
    { code: "MO", label: "Lun" },
    { code: "TU", label: "Mar" },
    { code: "WE", label: "Mer" },
    { code: "TH", label: "Jeu" },
    { code: "FR", label: "Ven" },
    { code: "SA", label: "Sam" },
    { code: "SU", label: "Dim" },
];

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
    onRecurrenceChange: (value: ContributionRecurrence) => void;
    url: string;
    onUrlChange: (value: string) => void;
    phone: string;
    onPhoneChange: (value: string) => void;
    email: string;
    onEmailChange: (value: string) => void;
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
    onRecurrenceChange,
    url,
    onUrlChange,
    phone,
    onPhoneChange,
    email,
    onEmailChange,
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

    function toggleRecurrenceDay(code: string) {
        onRecurrenceChange({
            ...recurrence,
            days: recurrence.days.includes(code)
                ? recurrence.days.filter((day) => day !== code)
                : [...recurrence.days, code],
        });
    }

    return (
        <div className="explorer-modal-overlay" onClick={onClose} role="presentation">
            <div
                aria-labelledby="explorer-contribution-modal-title"
                aria-modal="true"
                className="explorer-modal-dialog"
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className="explorer-modal-header">
                    <h2 id="explorer-contribution-modal-title">
                        {mode === "edit" ? "Modifier l'activité" : "Proposer une activité"}
                    </h2>
                    <button aria-label="Fermer" className="explorer-modal-close-button" onClick={onClose} type="button">
                        ✕
                    </button>
                </div>
                <form className="explorer-form" onSubmit={onSubmit}>
                    {mode === "create" && (
                        <p className="explorer-form-notice">
                            ℹ️ Ton activité sera soumise à validation par un modérateur avant d'apparaître sur la
                            carte : elle sera publiée d'ici 2 à 3 jours.
                        </p>
                    )}
                    {mode === "edit" && requiresRevalidation && (
                        <p className="explorer-form-notice">
                            Après modification, cette activité sera de nouveau soumise à validation : elle n'apparaîtra
                            sur la carte qu'une fois validée par un modérateur.
                        </p>
                    )}
                    <div className="explorer-form-field">
                        <label htmlFor="explorer-activity-title">Titre</label>
                        <input
                            id="explorer-activity-title"
                            onChange={(event) => onTitleChange(event.target.value)}
                            required
                            type="text"
                            value={title}
                        />
                    </div>
                    <div className="explorer-form-field">
                        <label htmlFor="explorer-activity-description">Description</label>
                        <input
                            id="explorer-activity-description"
                            onChange={(event) => onDescriptionChange(event.target.value)}
                            required
                            type="text"
                            value={description}
                        />
                    </div>
                    <fieldset className="explorer-form-field explorer-form-categories">
                        <legend>Catégories (au moins une)</legend>
                        <div className="explorer-form-categories-options">
                            {categories.map((item) => (
                                <label className="explorer-category-option" key={item.code}>
                                    <input
                                        checked={selectedCategoryCodes.includes(item.code)}
                                        onChange={() => toggleCategory(item.code)}
                                        type="checkbox"
                                    />
                                    {item.label}
                                </label>
                            ))}
                        </div>
                    </fieldset>
                    <div className="explorer-form-field">
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
                    <label className="explorer-category-option explorer-recurrence-toggle">
                        <input
                            checked={recurrence.enabled}
                            onChange={(event) => {
                                onRecurrenceChange({ ...recurrence, enabled: event.target.checked });
                                // La date de fin d'une activité ponctuelle (souvent le même jour) bornerait la
                                // récurrence à une seule journée : elle est vidée en passant en récurrent.
                                if (event.target.checked) {
                                    onEndDateChange("");
                                }
                            }}
                            type="checkbox"
                        />
                        Revient chaque semaine (food truck, marché…)
                    </label>
                    {recurrence.enabled && (
                        <>
                            <fieldset className="explorer-form-field explorer-form-categories">
                                <legend>Jours de passage (au moins un)</legend>
                                <div className="explorer-form-categories-options">
                                    {RECURRENCE_DAYS.map((day) => (
                                        <label className="explorer-category-option" key={day.code}>
                                            <input
                                                checked={recurrence.days.includes(day.code)}
                                                onChange={() => toggleRecurrenceDay(day.code)}
                                                type="checkbox"
                                            />
                                            {day.label}
                                        </label>
                                    ))}
                                </div>
                            </fieldset>
                            <div className="explorer-form-field">
                                <label htmlFor="explorer-activity-recurrence-start-time">Heure d'arrivée</label>
                                <input
                                    id="explorer-activity-recurrence-start-time"
                                    onChange={(event) => onRecurrenceChange({ ...recurrence, startTime: event.target.value })}
                                    required
                                    type="time"
                                    value={recurrence.startTime}
                                />
                            </div>
                            <div className="explorer-form-field">
                                <label htmlFor="explorer-activity-recurrence-end-time">Heure de départ (facultatif)</label>
                                <input
                                    id="explorer-activity-recurrence-end-time"
                                    onChange={(event) => onRecurrenceChange({ ...recurrence, endTime: event.target.value })}
                                    type="time"
                                    value={recurrence.endTime}
                                />
                            </div>
                        </>
                    )}
                    <div className="explorer-form-field">
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
                    <div className="explorer-form-field">
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
                    <div className="explorer-form-field">
                        <label htmlFor="explorer-activity-url">Site web (facultatif)</label>
                        <input
                            id="explorer-activity-url"
                            onChange={(event) => onUrlChange(event.target.value)}
                            placeholder="https://…"
                            type="text"
                            value={url}
                        />
                    </div>
                    <div className="explorer-form-field">
                        <label htmlFor="explorer-activity-phone">Téléphone (facultatif)</label>
                        <input
                            id="explorer-activity-phone"
                            onChange={(event) => onPhoneChange(event.target.value)}
                            type="tel"
                            value={phone}
                        />
                    </div>
                    <div className="explorer-form-field">
                        <label htmlFor="explorer-activity-email">Email (facultatif)</label>
                        <input
                            id="explorer-activity-email"
                            onChange={(event) => onEmailChange(event.target.value)}
                            type="email"
                            value={email}
                        />
                    </div>
                    <div className="explorer-modal-actions">
                        <button type="submit">
                            {mode === "edit" ? "Enregistrer les modifications" : "Proposer l'activité"}
                        </button>
                    </div>
                    {submitStatus === "success" && (
                        <span className="explorer-form-message explorer-form-message-success">Activité proposée !</span>
                    )}
                    {submitStatus === "error" && (
                        <span className="explorer-form-message explorer-form-message-error">{submitError}</span>
                    )}
                </form>
            </div>
        </div>
    );
}

export default ContributionModal;
