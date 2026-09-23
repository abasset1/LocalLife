import type { FormEvent } from "react";

interface ContributionModalProps {
    title: string;
    onTitleChange: (value: string) => void;
    description: string;
    onDescriptionChange: (value: string) => void;
    category: string;
    onCategoryChange: (value: string) => void;
    address: string;
    onAddressChange: (value: string) => void;
    startDate: string;
    onStartDateChange: (value: string) => void;
    endDate: string;
    onEndDateChange: (value: string) => void;
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
    title,
    onTitleChange,
    description,
    onDescriptionChange,
    category,
    onCategoryChange,
    address,
    onAddressChange,
    startDate,
    onStartDateChange,
    endDate,
    onEndDateChange,
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
                    <h2 id="explorer-contribution-modal-title">Proposer une activité</h2>
                    <button aria-label="Fermer" className="explorer-modal-close-button" onClick={onClose} type="button">
                        ✕
                    </button>
                </div>
                <form className="explorer-form" onSubmit={onSubmit}>
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
                    <div className="explorer-form-field">
                        <label htmlFor="explorer-activity-category">Catégorie</label>
                        <input
                            id="explorer-activity-category"
                            onChange={(event) => onCategoryChange(event.target.value)}
                            required
                            type="text"
                            value={category}
                        />
                    </div>
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
                    <div className="explorer-form-field">
                        <label htmlFor="explorer-activity-start-date">Date de début</label>
                        <input
                            id="explorer-activity-start-date"
                            onChange={(event) => onStartDateChange(event.target.value)}
                            type="date"
                            value={startDate}
                        />
                    </div>
                    <div className="explorer-form-field">
                        <label htmlFor="explorer-activity-end-date">Date de fin</label>
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
                        <button type="submit">Proposer l'activité</button>
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
