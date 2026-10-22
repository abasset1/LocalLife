import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { proRequest } from "../pro/proApi";
import type { ProResult } from "../pro/proApi";
import { CONFIRM_PASSWORD_HEADER, useConfirmDialog } from "./useConfirmDialog";

type Role = "USER" | "PRO" | "ADMIN";
type AccountStatus = "ACTIVE" | "SUSPENDED";

/** `AccountRepository.Account` (backend) : aucun secret. */
export interface Account {
    id: number;
    username: string;
    email: string;
    role: Role;
    emailVerified: boolean;
    createdAt: string | null;
    status: AccountStatus;
    statusReason: string | null;
    statusChangedAt: string | null;
}

interface AccountPage {
    items: Account[];
    total: number;
    page: number;
    size: number;
}

/** `AccountAuditRepository.Event` (backend), dates en UTC. */
interface AuditEvent {
    id: number;
    occurredAt: string;
    actorUserId: number | null;
    actorEmail: string | null;
    targetUserId: number | null;
    /** `ROLE_CHANGE`, `SUSPEND`, `REACTIVATE`, et les actions plus récentes (affichées telles quelles). */
    action: string;
    oldValue: string | null;
    newValue: string | null;
    reason: string | null;
    outcome: "SUCCESS" | "REFUSED";
    detail: string | null;
}

interface AuditPage {
    items: AuditEvent[];
    total: number;
}

const PAGE_SIZE = 20;

const ROLE_LABELS: Record<Role, string> = { USER: "Utilisateur", PRO: "Professionnel", ADMIN: "Administrateur" };
const STATUS_LABELS: Record<AccountStatus, string> = { ACTIVE: "Actif", SUSPENDED: "Suspendu" };
const ACTION_LABELS: Record<string, string> = {
    ROLE_CHANGE: "Changement de rôle",
    SUSPEND: "Suspension",
    REACTIVATE: "Réactivation",
};

function formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }) : "—";
}

/** Date UTC du journal (sans fuseau dans la réponse) affichée comme telle. */
function formatUtc(value: string): string {
    return `${value.replace("T", " ").slice(0, 16)} UTC`;
}

/**
 * Console générale des comptes (sprint « Rôles et comptes », WEB-01) : recherche, filtres, pagination, et les seules
 * actions que l'API autorise — attribuer ou retirer le rôle PRO, suspendre (motif obligatoire) ou réactiver un compte
 * non administrateur, le déconnecter partout ou supprimer son compte. Suspendre et supprimer exigent le mot de passe
 * de l'administrateur (`X-Confirm-Password`, 428 sinon). L'API reste seule juge : un refus est affiché tel qu'elle l'explique. Le rôle PRO est un rôle
 * d'accès, distinct de l'abonnement Pro (Professionnels → Abonnements).
 */
function AdminAccountsSection() {
    const [query, setQuery] = useState("");
    const [role, setRole] = useState("");
    const [status, setStatus] = useState("");
    const [filters, setFilters] = useState({ query: "", role: "", status: "" });
    const [page, setPage] = useState(0);
    const [accounts, setAccounts] = useState<AccountPage | null>(null);
    const [loading, setLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
    const [suspending, setSuspending] = useState<Account | null>(null);
    const [reason, setReason] = useState("");
    const [busy, setBusy] = useState(false);
    const [audit, setAudit] = useState<AuditPage | null>(null);
    const [auditTarget, setAuditTarget] = useState<Account | null>(null);
    const { ask, dialog } = useConfirmDialog();

    const load = useCallback(async () => {
        setLoading(true);
        setListError(null);
        const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
        if (filters.query.trim()) {
            params.set("q", filters.query.trim());
        }
        if (filters.role) {
            params.set("role", filters.role);
        }
        if (filters.status) {
            params.set("status", filters.status);
        }
        const result = await proRequest<AccountPage>(`/api/v1/admin/accounts?${params.toString()}`);
        setLoading(false);
        if (result.ok) {
            setAccounts(result.data);
        } else {
            setListError(result.message);
        }
    }, [filters, page]);

    const loadAudit = useCallback(async () => {
        const params = new URLSearchParams({ size: "20" });
        if (auditTarget) {
            params.set("targetUserId", String(auditTarget.id));
        }
        const result = await proRequest<AuditPage>(`/api/v1/admin/accounts/audit?${params.toString()}`);
        if (result.ok) {
            setAudit(result.data);
        }
    }, [auditTarget]);

    useEffect(() => {
        void load();
    }, [load]);

    useEffect(() => {
        void loadAudit();
    }, [loadAudit]);

    function applyFilters(event: FormEvent) {
        event.preventDefault();
        setPage(0);
        setFilters({ query, role, status });
    }

    async function afterAction(result: ProResult<Account>, success: string) {
        setBusy(false);
        setMessage(result.ok ? { text: success, error: false } : { text: result.message, error: true });
        if (result.ok) {
            setSuspending(null);
            setReason("");
        }
        await load();
        await loadAudit();
    }

    async function changeRole(account: Account, target: "USER" | "PRO") {
        const question = target === "PRO"
            ? `Attribuer le rôle professionnel à ${account.email} ? (sans effet sur son abonnement)`
            : `Retirer le rôle professionnel de ${account.email} ?`;
        if (!window.confirm(question)) {
            return;
        }
        setBusy(true);
        const result = await proRequest<Account>(`/api/v1/admin/accounts/${account.id}/role`, "PATCH", { role: target });
        await afterAction(result, target === "PRO" ? `${account.email} est professionnel.` : `${account.email} n'est plus professionnel.`);
    }

    async function suspend(event: FormEvent) {
        event.preventDefault();
        if (!suspending) {
            return;
        }
        const confirmation = await ask({
            title: `Suspendre ${suspending.email}`,
            message: "Il ne pourra plus se connecter. Confirme avec ton mot de passe.",
            password: true,
            confirmLabel: "Suspendre",
        });
        if (!confirmation) {
            return;
        }
        setBusy(true);
        const result = await proRequest<Account>(`/api/v1/admin/accounts/${suspending.id}/suspend`, "POST", {
            reason: reason.trim(),
        }, { [CONFIRM_PASSWORD_HEADER]: confirmation.password });
        await afterAction(result, `${suspending.email} est suspendu.`);
    }

    async function logoutEverywhere(account: Account) {
        if (!window.confirm(`Déconnecter ${account.email} de tous ses appareils ?`)) {
            return;
        }
        setBusy(true);
        const result = await proRequest<Account>(`/api/v1/admin/accounts/${account.id}/logout-everywhere`, "POST");
        await afterAction(result, `${account.email} est déconnecté partout.`);
    }

    async function deleteAccount(account: Account) {
        const confirmation = await ask({
            title: `Supprimer le compte ${account.email}`,
            message: "Suppression définitive du compte. Ses activités publiées restent, sans auteur.",
            reason: { label: "Motif (journalisé)", minLength: 3, maxLength: 500 },
            password: true,
            confirmLabel: "Supprimer le compte",
        });
        if (!confirmation) {
            return;
        }
        setBusy(true);
        const result = await proRequest<Account>(`/api/v1/admin/accounts/${account.id}/deletion`, "POST",
            { reason: confirmation.reason }, { [CONFIRM_PASSWORD_HEADER]: confirmation.password });
        await afterAction(result, `Le compte ${account.email} est supprimé.`);
    }

    async function reactivate(account: Account) {
        if (!window.confirm(`Réactiver ${account.email} ?`)) {
            return;
        }
        setBusy(true);
        const result = await proRequest<Account>(`/api/v1/admin/accounts/${account.id}/reactivate`, "POST", {});
        await afterAction(result, `${account.email} est réactivé.`);
    }

    const pageCount = accounts ? Math.max(1, Math.ceil(accounts.total / PAGE_SIZE)) : 1;

    return (
        <section aria-labelledby="admin-accounts-title" className="admin-accounts">
            <h2 id="admin-accounts-title">Comptes</h2>
            <p className="admin-import-hint">
                Rôle d'accès (utilisateur, professionnel, administrateur) et statut du compte. L'offre commerciale
                (Gratuit, Premium, Pro) se gère dans Professionnels → Abonnements. Les administrateurs ne se
                modifient pas depuis l'application.
            </p>

            <form className="admin-import-form" onSubmit={applyFilters}>
                <label>
                    Recherche{" "}
                    <input
                        className="admin-accounts-search"
                        onChange={(event) => setQuery(event.target.value)}
                        placeholder="e-mail, nom ou identifiant"
                        type="search"
                        value={query}
                    />
                </label>
                <label>
                    Rôle{" "}
                    <select onChange={(event) => setRole(event.target.value)} value={role}>
                        <option value="">Tous</option>
                        <option value="USER">Utilisateur</option>
                        <option value="PRO">Professionnel</option>
                        <option value="ADMIN">Administrateur</option>
                    </select>
                </label>
                <label>
                    Statut{" "}
                    <select onChange={(event) => setStatus(event.target.value)} value={status}>
                        <option value="">Tous</option>
                        <option value="ACTIVE">Actif</option>
                        <option value="SUSPENDED">Suspendu</option>
                    </select>
                </label>
                <button className="admin-import-button" type="submit">
                    Rechercher
                </button>
            </form>

            {message && (
                <p
                    className={`admin-import-message ${message.error ? "admin-import-message-error" : "admin-import-message-success"}`}
                    role={message.error ? "alert" : "status"}
                >
                    {message.text}
                </p>
            )}
            {listError && (
                <p className="admin-import-message admin-import-message-error" role="alert">
                    {listError}
                </p>
            )}
            {loading && !accounts && <p className="admin-import-hint" role="status">Chargement des comptes…</p>}
            {accounts && accounts.total === 0 && <p className="admin-import-hint">Aucun compte ne correspond.</p>}

            {accounts && accounts.total > 0 && (
                <>
                    <div className="admin-import-table-wrapper">
                        <table className="admin-import-table admin-accounts-table">
                            <thead>
                                <tr>
                                    <th scope="col">Compte</th>
                                    <th scope="col">Rôle</th>
                                    <th scope="col">E-mail confirmé</th>
                                    <th scope="col">Statut</th>
                                    <th scope="col">Créé le</th>
                                    <th scope="col">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {accounts.items.map((account) => (
                                    <tr key={account.id}>
                                        <th scope="row">
                                            <strong>{account.username}</strong>
                                            <br />
                                            <span className="admin-accounts-email">{account.email}</span>
                                        </th>
                                        <td>{ROLE_LABELS[account.role]}</td>
                                        <td>{account.emailVerified ? "oui" : "non"}</td>
                                        <td>
                                            <span className={`admin-account-status admin-account-status-${account.status.toLowerCase()}`}>
                                                {STATUS_LABELS[account.status]}
                                            </span>
                                            {account.status === "SUSPENDED" && account.statusReason && (
                                                <div className="admin-accounts-reason">Motif : {account.statusReason}</div>
                                            )}
                                        </td>
                                        <td>{formatDateTime(account.createdAt)}</td>
                                        <td className="admin-accounts-actions">
                                            {account.role !== "ADMIN" && (
                                                <>
                                                    <button
                                                        className="admin-import-button"
                                                        disabled={busy}
                                                        onClick={() => void changeRole(account, account.role === "PRO" ? "USER" : "PRO")}
                                                        type="button"
                                                    >
                                                        {account.role === "PRO" ? "Retirer PRO" : "Attribuer PRO"}
                                                    </button>
                                                    {account.status === "ACTIVE" ? (
                                                        <button
                                                            className="admin-import-button"
                                                            disabled={busy}
                                                            onClick={() => {
                                                                setSuspending(account);
                                                                setReason("");
                                                            }}
                                                            type="button"
                                                        >
                                                            Suspendre
                                                        </button>
                                                    ) : (
                                                        <button
                                                            className="admin-import-button"
                                                            disabled={busy}
                                                            onClick={() => void reactivate(account)}
                                                            type="button"
                                                        >
                                                            Réactiver
                                                        </button>
                                                    )}
                                                    <button
                                                        className="admin-import-button"
                                                        disabled={busy}
                                                        onClick={() => void logoutEverywhere(account)}
                                                        type="button"
                                                    >
                                                        Déconnecter partout
                                                    </button>
                                                    <button
                                                        className="admin-import-button admin-danger-button"
                                                        disabled={busy}
                                                        onClick={() => void deleteAccount(account)}
                                                        type="button"
                                                    >
                                                        Supprimer le compte
                                                    </button>
                                                </>
                                            )}
                                            <button
                                                className="admin-import-button"
                                                onClick={() => setAuditTarget(account)}
                                                type="button"
                                            >
                                                Historique
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="admin-import-form admin-discovery-pager">
                        <button
                            className="admin-import-button"
                            disabled={page === 0}
                            onClick={() => setPage((current) => current - 1)}
                            type="button"
                        >
                            Précédent
                        </button>
                        <span>
                            Page {page + 1} / {pageCount} — {accounts.total} compte(s)
                        </span>
                        <button
                            className="admin-import-button"
                            disabled={page + 1 >= pageCount}
                            onClick={() => setPage((current) => current + 1)}
                            type="button"
                        >
                            Suivant
                        </button>
                    </div>
                </>
            )}

            {suspending && (
                <form className="admin-import-card admin-accounts-suspend" onSubmit={(event) => void suspend(event)}>
                    <h3>Suspendre {suspending.email}</h3>
                    <p className="admin-import-hint">
                        Le compte ne pourra plus se connecter ni utiliser l'application connectée, dès sa prochaine
                        requête. Ses données sont conservées ; la suspension est réversible.
                    </p>
                    <label htmlFor="admin-suspend-reason">Motif (obligatoire, journalisé)</label>
                    <textarea
                        id="admin-suspend-reason"
                        maxLength={500}
                        minLength={3}
                        onChange={(event) => setReason(event.target.value)}
                        required
                        rows={3}
                        value={reason}
                    />
                    <div className="admin-import-form">
                        <button className="admin-import-button" disabled={busy || reason.trim().length < 3} type="submit">
                            Confirmer la suspension
                        </button>
                        <button className="admin-import-button" onClick={() => setSuspending(null)} type="button">
                            Annuler
                        </button>
                    </div>
                </form>
            )}

            <h3 className="admin-discovery-subtitle">
                Journal des actions{auditTarget ? ` — ${auditTarget.email}` : " (toutes)"}
            </h3>
            {auditTarget && (
                <button className="admin-import-button" onClick={() => setAuditTarget(null)} type="button">
                    Voir tout le journal
                </button>
            )}
            {audit && audit.items.length === 0 && <p className="admin-import-hint">Aucune action enregistrée.</p>}
            {audit && audit.items.length > 0 && (
                <div className="admin-import-table-wrapper">
                    <table className="admin-import-table">
                        <thead>
                            <tr>
                                <th scope="col">Date</th>
                                <th scope="col">Action</th>
                                <th scope="col">Compte</th>
                                <th scope="col">Avant → après</th>
                                <th scope="col">Résultat</th>
                                <th scope="col">Par</th>
                                <th scope="col">Motif / détail</th>
                            </tr>
                        </thead>
                        <tbody>
                            {audit.items.map((event) => (
                                <tr key={event.id}>
                                    <td>{formatUtc(event.occurredAt)}</td>
                                    <td>{ACTION_LABELS[event.action] ?? event.action}</td>
                                    <td>#{event.targetUserId ?? "?"}</td>
                                    <td>
                                        {event.oldValue ?? "—"} → {event.newValue ?? "—"}
                                    </td>
                                    <td className={event.outcome === "SUCCESS" ? "admin-sync-status-success" : "admin-sync-status-failed"}>
                                        {event.outcome === "SUCCESS" ? "Effectuée" : "Refusée"}
                                    </td>
                                    <td>{event.actorEmail ?? "—"}</td>
                                    <td className="admin-sync-message">
                                        {[event.reason, event.detail].filter(Boolean).join(" — ")}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
            {dialog}
        </section>
    );
}

export default AdminAccountsSection;
