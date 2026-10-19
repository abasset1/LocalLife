import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { proRequest } from "../pro/proApi";

/** Configuration SMTP effective (`MailDiagnostics.MailConfig`, backend), sans le mot de passe. */
interface MailConfig {
    host: string | null;
    port: number;
    username: string | null;
    passwordSet: boolean;
    auth: boolean;
    startTls: boolean;
    ssl: boolean;
    from: string;
    warning: string | null;
}

interface MailTestResult {
    ok: boolean;
    step: "connexion" | "envoi";
    message: string;
}

/**
 * E-mails : configuration SMTP effective et envoi d'un e-mail de test, avec l'explication d'un échec (identifiants,
 * serveur injoignable, chiffrement, expéditeur refusé). Les envois réels (confirmation d'adresse, mot de passe oublié,
 * alertes) ne signalent leurs échecs que dans les journaux.
 */
function AdminMailSection() {
    const [config, setConfig] = useState<MailConfig | null>(null);
    const [to, setTo] = useState("");
    const [result, setResult] = useState<MailTestResult | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        void proRequest<MailConfig>("/api/v1/admin/mail/config").then((response) => {
            if (response.ok) {
                setConfig(response.data);
            } else {
                setError(response.message);
            }
        });
    }, []);

    async function sendTest(event: FormEvent) {
        event.preventDefault();
        setBusy(true);
        setResult(null);
        const response = await proRequest<MailTestResult>("/api/v1/admin/mail/test", "POST",
            { to: to.trim() === "" ? null : to.trim() });
        setBusy(false);
        if (response.ok) {
            setResult(response.data);
        } else {
            setError(response.message);
        }
    }

    const yesNo = (value: boolean) => (value ? "oui" : "non");

    return (
        <section aria-labelledby="admin-mail-title">
            <h2 id="admin-mail-title">E-mails</h2>
            {config && (
                <div className="admin-activity-card">
                    <p className="admin-activity-description">
                        Serveur : <strong>{config.host}:{config.port}</strong> · authentification {yesNo(config.auth)}
                        {" "}(utilisateur {config.username || "—"}, mot de passe {config.passwordSet ? "renseigné" : "absent"})
                        {" "}· STARTTLS {yesNo(config.startTls)} · SSL {yesNo(config.ssl)}
                    </p>
                    <p className="admin-activity-description">Expéditeur (MAIL_FROM) : {config.from}</p>
                    {config.warning && (
                        <p className="activities-status activities-status-error">⚠️ {config.warning}</p>
                    )}
                </div>
            )}
            <form className="contribution-form" onSubmit={(event) => void sendTest(event)}>
                <div className="form-field">
                    <label htmlFor="mail-test-to">Destinataire du test (vide : ton adresse)</label>
                    <input id="mail-test-to" onChange={(event) => setTo(event.target.value)} type="email" value={to} />
                </div>
                <button className="admin-approve-button" disabled={busy} type="submit">
                    {busy ? "Envoi…" : "Envoyer un e-mail de test"}
                </button>
            </form>
            {result && (
                <p className={result.ok ? "activities-status" : "activities-status activities-status-error"} role="status">
                    {result.ok ? "✅ " : `❌ Échec à l'étape « ${result.step} » : `}{result.message}
                </p>
            )}
            {error && <p className="activities-status activities-status-error">{error}</p>}
        </section>
    );
}

export default AdminMailSection;
