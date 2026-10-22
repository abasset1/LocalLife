import { useState } from "react";
import type { FormEvent } from "react";
import { apiFetch } from "../../api/apiClient";
import { saveToken } from "../../auth/authStorage";
import { Notice, ProfileCard } from "./ProfileUi";

async function errorMessage(response: Response, fallback: string): Promise<string> {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    return body?.message ?? fallback;
}

/**
 * Changer son mot de passe (`POST /api/v1/users/me/password`), ou en définir un pour un compte créé avec Google. Les
 * autres sessions sont fermées par le serveur ; celle-ci reçoit un nouveau jeton.
 */
export function PasswordCard({ hasPassword }: { hasPassword: boolean }) {
    const [current, setCurrent] = useState("");
    const [next, setNext] = useState("");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<{ kind: "success" | "error"; text: string } | null>(null);

    async function submit(event: FormEvent) {
        event.preventDefault();
        setBusy(true);
        setMessage(null);
        try {
            const response = await apiFetch("/api/v1/users/me/password", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(hasPassword ? { currentPassword: current, newPassword: next } : { newPassword: next }),
            });
            if (!response.ok) {
                setMessage({ kind: "error", text: await errorMessage(response, "Le mot de passe n'a pas pu être changé.") });
                return;
            }
            const { token } = (await response.json()) as { token: string };
            saveToken(token);
            setCurrent("");
            setNext("");
            setMessage({ kind: "success", text: "Mot de passe enregistré. Tes autres sessions ont été déconnectées." });
        } catch {
            setMessage({ kind: "error", text: "Impossible de contacter le serveur, réessaie plus tard." });
        } finally {
            setBusy(false);
        }
    }

    return (
        <ProfileCard icon="🔒" title={hasPassword ? "Mot de passe" : "Définir un mot de passe"} titleId="profile-password-title">
            {!hasPassword && (
                <p className="pp-hint">Tu te connectes avec Google. Un mot de passe te permettra aussi de te connecter par e-mail.</p>
            )}
            <form className="pp-form" onSubmit={(event) => void submit(event)}>
                {hasPassword && (
                    <div className="pp-field">
                        <label className="pp-label" htmlFor="profile-current-password">Mot de passe actuel</label>
                        <input autoComplete="current-password" className="pp-input" id="profile-current-password"
                            onChange={(event) => setCurrent(event.target.value)} required type="password" value={current} />
                    </div>
                )}
                <div className="pp-field">
                    <label className="pp-label" htmlFor="profile-new-password">Nouveau mot de passe (8 caractères au moins)</label>
                    <input autoComplete="new-password" className="pp-input" id="profile-new-password" minLength={8}
                        onChange={(event) => setNext(event.target.value)} required type="password" value={next} />
                </div>
                <div className="pp-actions">
                    <button className="pp-button pp-button-primary" disabled={busy} type="submit">
                        {busy ? "Enregistrement…" : "Enregistrer le mot de passe"}
                    </button>
                    {message && <Notice kind={message.kind}>{message.text}</Notice>}
                </div>
            </form>
        </ProfileCard>
    );
}

/**
 * Supprimer son compte (`POST /api/v1/users/me/deletion`) : définitif. Les activités publiées restent, sans auteur ;
 * le reste (contributions non publiées, favoris, alertes, préférences) est supprimé.
 */
export function DeleteAccountCard({ hasPassword, isAdmin, onDeleted }: {
    hasPassword: boolean;
    isAdmin: boolean;
    onDeleted: () => void;
}) {
    const [open, setOpen] = useState(false);
    const [secret, setSecret] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    if (isAdmin) {
        return null;
    }

    async function submit(event: FormEvent) {
        event.preventDefault();
        if (!window.confirm("Supprimer définitivement ton compte ? Cette action est irréversible.")) {
            return;
        }
        setBusy(true);
        setError(null);
        try {
            const response = await apiFetch("/api/v1/users/me/deletion", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(hasPassword ? { password: secret } : { confirmation: secret }),
            });
            if (response.status === 204) {
                onDeleted();
                return;
            }
            setError(await errorMessage(response, "Le compte n'a pas pu être supprimé."));
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setBusy(false);
        }
    }

    return (
        <ProfileCard icon="🗑️" title="Supprimer mon compte" titleId="profile-delete-title">
            <p className="pp-hint">
                Définitif. Tes activités déjà publiées restent visibles, sans ton nom ; tes contributions non publiées,
                favoris, alertes et préférences sont supprimés, et ton abonnement prend fin.
            </p>
            {!open ? (
                <button className="pp-button pp-button-danger" onClick={() => setOpen(true)} type="button">
                    Supprimer mon compte…
                </button>
            ) : (
                <form className="pp-form" onSubmit={(event) => void submit(event)}>
                    <div className="pp-field">
                        <label className="pp-label" htmlFor="profile-delete-secret">
                            {hasPassword ? "Ton mot de passe" : "Saisis SUPPRIMER pour confirmer"}
                        </label>
                        <input autoComplete={hasPassword ? "current-password" : "off"} className="pp-input"
                            id="profile-delete-secret" onChange={(event) => setSecret(event.target.value)} required
                            type={hasPassword ? "password" : "text"} value={secret} />
                    </div>
                    <div className="pp-actions">
                        <button className="pp-button pp-button-danger" disabled={busy} type="submit">
                            {busy ? "Suppression…" : "Supprimer définitivement"}
                        </button>
                        <button className="pp-button pp-button-secondary" onClick={() => setOpen(false)} type="button">
                            Annuler
                        </button>
                    </div>
                    {error && <Notice kind="error">{error}</Notice>}
                </form>
            )}
        </ProfileCard>
    );
}
