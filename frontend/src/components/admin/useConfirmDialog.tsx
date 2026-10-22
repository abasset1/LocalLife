import { useCallback, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";

/** En-tête attendu par le serveur pour les actions sensibles (sinon 428). */
export const CONFIRM_PASSWORD_HEADER = "X-Confirm-Password";

export interface ConfirmDialogOptions {
    title: string;
    message?: ReactNode;
    /** Motif à saisir (envoyé au serveur, souvent à l'utilisateur concerné). */
    reason?: { label: string; minLength?: number; maxLength?: number };
    /** Demande le mot de passe de l'administrateur (`X-Confirm-Password`). */
    password?: boolean;
    confirmLabel?: string;
    danger?: boolean;
}

export interface ConfirmDialogResult {
    reason: string;
    password: string;
}

interface OpenDialog extends ConfirmDialogOptions {
    resolve: (result: ConfirmDialogResult | null) => void;
}

function ConfirmDialogView({ dialog, onClose }: {
    dialog: OpenDialog;
    onClose: (result: ConfirmDialogResult | null) => void;
}) {
    const [reason, setReason] = useState("");
    const [password, setPassword] = useState("");
    const minLength = dialog.reason?.minLength ?? 1;
    const reasonMissing = dialog.reason !== undefined && reason.trim().length < minLength;
    const passwordMissing = dialog.password === true && password === "";

    function submit(event: FormEvent) {
        event.preventDefault();
        if (!reasonMissing && !passwordMissing) {
            onClose({ reason: reason.trim(), password });
        }
    }

    return (
        <div className="modal-overlay admin-confirm-overlay" onClick={() => onClose(null)} role="presentation">
            <div aria-labelledby="confirm-dialog-title" aria-modal="true" className="modal-dialog"
                onClick={(event) => event.stopPropagation()} role="dialog">
                <div className="modal-header">
                    <h2 id="confirm-dialog-title">{dialog.title}</h2>
                    <button aria-label="Fermer" className="modal-close-button" onClick={() => onClose(null)} type="button">
                        ✕
                    </button>
                </div>
                <form className="contribution-form" onSubmit={submit}>
                    {dialog.message && <div className="activities-status">{dialog.message}</div>}
                    {dialog.reason && (
                        <div className="form-field">
                            <label htmlFor="confirm-dialog-reason">{dialog.reason.label}</label>
                            <textarea autoFocus id="confirm-dialog-reason" maxLength={dialog.reason.maxLength ?? 2000}
                                onChange={(event) => setReason(event.target.value)} required value={reason} />
                            {minLength > 1 && <span className="pp-hint">Au moins {minLength} caractères.</span>}
                        </div>
                    )}
                    {dialog.password && (
                        <div className="form-field">
                            <label htmlFor="confirm-dialog-password">Ton mot de passe (confirmation)</label>
                            <input autoComplete="current-password" autoFocus={!dialog.reason} id="confirm-dialog-password"
                                onChange={(event) => setPassword(event.target.value)} required type="password"
                                value={password} />
                        </div>
                    )}
                    <div className="modal-actions admin-confirm-actions">
                        <button className="contribution-action" onClick={() => onClose(null)} type="button">Annuler</button>
                        <button className={dialog.danger === false ? "admin-approve-button" : "admin-reject-button"}
                            disabled={reasonMissing || passwordMissing} type="submit">
                            {dialog.confirmLabel ?? "Confirmer"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

/**
 * Fenêtre de confirmation des actions d'administration : motif obligatoire et/ou mot de passe de l'administrateur
 * (en-tête `X-Confirm-Password`). `ask` renvoie la saisie, ou `null` si l'administrateur annule ; `dialog` est à
 * rendre dans le composant.
 */
export function useConfirmDialog(): {
    ask: (options: ConfirmDialogOptions) => Promise<ConfirmDialogResult | null>;
    dialog: ReactNode;
} {
    const [open, setOpen] = useState<OpenDialog | null>(null);
    const counter = useRef(0);

    const ask = useCallback((options: ConfirmDialogOptions) => new Promise<ConfirmDialogResult | null>((resolve) => {
        counter.current += 1;
        setOpen({ ...options, resolve });
    }), []);

    const dialog = open ? (
        <ConfirmDialogView dialog={open} key={counter.current} onClose={(result) => {
            open.resolve(result);
            setOpen(null);
        }} />
    ) : null;

    return { ask, dialog };
}

/** En-têtes JSON + confirmation par mot de passe. */
export function confirmHeaders(password: string, json = false): HeadersInit {
    return json
        ? { "Content-Type": "application/json", [CONFIRM_PASSWORD_HEADER]: password }
        : { [CONFIRM_PASSWORD_HEADER]: password };
}
