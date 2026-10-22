import { useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, describeErrorResponse } from "../../api/apiClient";
import { LEGAL_DOCUMENTS } from "../../legal/legalDocuments";
import { Notice, ProfileCard } from "./ProfileUi";

export const EXPORT_FILE_NAME = "locallife-mes-donnees.json";

/**
 * Carte « Tes données » (contrat RGPD) : export de toutes les données du compte
 * (`GET /api/v1/users/me/export`, enregistré en `locallife-mes-donnees.json`), durées de
 * conservation et liens vers les textes légaux.
 */
function MyDataCard() {
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [done, setDone] = useState(false);

    async function handleExport() {
        setBusy(true);
        setError(null);
        setDone(false);
        try {
            const response = await apiFetch("/api/v1/users/me/export");
            if (!response.ok) {
                setError(await describeErrorResponse(response, "Impossible de préparer tes données"));
                return;
            }
            const blob = new Blob([await response.text()], { type: "application/json" });
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = EXPORT_FILE_NAME;
            document.body.appendChild(link);
            link.click();
            link.remove();
            // Libéré après le clic : le navigateur a déjà pris en charge le téléchargement.
            setTimeout(() => URL.revokeObjectURL(url), 0);
            setDone(true);
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setBusy(false);
        }
    }

    return (
        <ProfileCard icon="📄" title="Tes données" titleId="profile-data-title">
            <p className="pp-hint">
                Télécharge un fichier JSON avec toutes les données de ton compte : profil, préférences, alertes,
                favoris, contributions, abonnement et historique.
            </p>
            <div className="pp-actions">
                <button className="pp-button pp-button-secondary" disabled={busy} onClick={() => void handleExport()}
                    type="button">
                    {busy ? "Préparation…" : "Télécharger mes données"}
                </button>
                {done && <Notice kind="success">Fichier {EXPORT_FILE_NAME} téléchargé.</Notice>}
                {error && <Notice kind="error">{error}</Notice>}
            </div>
            <p className="pp-hint">
                Conservation : un compte jamais confirmé est supprimé après 30 jours. Un compte inactif depuis 3 ans
                reçoit un e-mail d'avertissement, puis est supprimé 30 jours plus tard.
            </p>
            <p className="pp-hint">
                <Link to={LEGAL_DOCUMENTS.privacy.path}>{LEGAL_DOCUMENTS.privacy.title}</Link>
                {" · "}
                <Link to={LEGAL_DOCUMENTS.terms.path}>{LEGAL_DOCUMENTS.terms.title}</Link>
                {" · "}
                <Link to={LEGAL_DOCUMENTS.legalNotice.path}>{LEGAL_DOCUMENTS.legalNotice.title}</Link>
            </p>
        </ProfileCard>
    );
}

export default MyDataCard;
