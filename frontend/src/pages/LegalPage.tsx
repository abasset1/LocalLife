import { Link } from "react-router-dom";
import { formatLegalVersion, LEGAL_DOCUMENTS } from "../legal/legalDocuments";
import type { LegalDocumentKey } from "../legal/legalDocuments";

/** Page d'un texte légal (`/confidentialite`, `/conditions`, `/mentions-legales`) : contenu en cours de rédaction. */
function LegalPage({ document }: { document: LegalDocumentKey }) {
    const { title } = LEGAL_DOCUMENTS[document];
    const others = (Object.keys(LEGAL_DOCUMENTS) as LegalDocumentKey[]).filter((key) => key !== document);

    return (
        <main className="auth-shell">
            <article className="auth-form legal-page">
                <h1>{title}</h1>
                <p>Document en cours de rédaction.</p>
                <p className="legal-version">Version du {formatLegalVersion()}</p>
                <p className="auth-switch">
                    {others.map((key) => (
                        <span className="legal-page-link" key={key}>
                            <Link to={LEGAL_DOCUMENTS[key].path}>{LEGAL_DOCUMENTS[key].title}</Link>
                        </span>
                    ))}
                </p>
                <p className="auth-switch">
                    <Link to="/">← Retour à LocalLife</Link>
                </p>
            </article>
        </main>
    );
}

export default LegalPage;
