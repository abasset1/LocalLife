import { Link } from "react-router-dom";
import { LEGAL_CONTENT, splitPlaceholders } from "../legal/legalContent";
import type { LegalBlock } from "../legal/legalContent";
import { formatLegalVersion, LEGAL_DOCUMENTS } from "../legal/legalDocuments";
import type { LegalDocumentKey } from "../legal/legalDocuments";

/** Texte d'un paragraphe ou d'une puce ; les champs « [À compléter …] » sont surlignés. */
function LegalText({ text }: { text: string }) {
    return (
        <>
            {splitPlaceholders(text).map((part, index) =>
                part.placeholder ? (
                    <mark className="legal-placeholder" key={index}>{part.text}</mark>
                ) : (
                    <span key={index}>{part.text}</span>
                ),
            )}
        </>
    );
}

function Block({ block }: { block: LegalBlock }) {
    if (Array.isArray(block)) {
        return (
            <ul>
                {block.map((item, index) => (
                    <li key={index}><LegalText text={item} /></li>
                ))}
            </ul>
        );
    }
    return <p><LegalText text={block} /></p>;
}

/** Page d'un texte légal (`/confidentialite`, `/conditions`, `/mentions-legales`). */
function LegalPage({ document }: { document: LegalDocumentKey }) {
    const { title } = LEGAL_DOCUMENTS[document];
    const others = (Object.keys(LEGAL_DOCUMENTS) as LegalDocumentKey[]).filter((key) => key !== document);

    return (
        <main className="auth-shell">
            <article className="auth-form legal-page">
                <h1>{title}</h1>
                {LEGAL_CONTENT[document].map((section) => (
                    <section className="legal-section" key={section.title}>
                        <h2>{section.title}</h2>
                        {section.blocks.map((block, index) => (
                            <Block block={block} key={index} />
                        ))}
                    </section>
                ))}
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
