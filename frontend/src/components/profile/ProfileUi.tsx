import type { ReactNode } from "react";

/**
 * Briques visuelles de la page profil, alignées sur l'identité de l'écran
 * Explorer (vert profond, corail, fonds sable/papier, puces arrondies) — voir
 * les variables `--ll-*` et la section « Page profil » de styles.css.
 */

interface ProfileCardProps {
    icon: string;
    title: string;
    subtitle?: ReactNode;
    children: ReactNode;
    /** Identifiant du titre, pour `aria-labelledby` de la section. */
    titleId: string;
}

/** Carte de section : pastille d'icône, titre, sous-titre, contenu. */
export function ProfileCard({ icon, title, subtitle, children, titleId }: ProfileCardProps) {
    return (
        <section aria-labelledby={titleId} className="pp-card">
            <header className="pp-card-header">
                <span aria-hidden="true" className="pp-card-icon">
                    {icon}
                </span>
                <div>
                    <h2 className="pp-card-title" id={titleId}>
                        {title}
                    </h2>
                    {subtitle && <p className="pp-card-subtitle">{subtitle}</p>}
                </div>
            </header>
            {children}
        </section>
    );
}

interface ChipProps {
    checked: boolean;
    onChange: () => void;
    children: ReactNode;
    /** `radio` pour un choix unique (rayon), `checkbox` pour un choix multiple (catégories). */
    type?: "checkbox" | "radio";
    name?: string;
}

/**
 * Puce sélectionnable : vraie case à cocher (ou bouton radio) masquée
 * visuellement mais focusable — l'accessibilité clavier et lecteur d'écran
 * reste celle d'un contrôle natif.
 */
export function Chip({ checked, onChange, children, type = "checkbox", name }: ChipProps) {
    return (
        <label className="pp-chip">
            <input checked={checked} className="pp-chip-input" name={name} onChange={onChange} type={type} />
            <span className="pp-chip-label">{children}</span>
        </label>
    );
}

/** Groupe de puces avec sa légende. */
export function ChipGroup({ legend, hint, children }: { legend: string; hint?: string; children: ReactNode }) {
    return (
        <fieldset className="pp-fieldset">
            <legend className="pp-label">{legend}</legend>
            <div className="pp-chips">{children}</div>
            {hint && <p className="pp-hint">{hint}</p>}
        </fieldset>
    );
}

/** Message de retour (succès / erreur / information), annoncé aux lecteurs d'écran. */
export function Notice({ kind, children }: { kind: "success" | "error" | "info"; children: ReactNode }) {
    return (
        <p className={`pp-notice pp-notice-${kind}`} role={kind === "error" ? "alert" : "status"}>
            {children}
        </p>
    );
}
