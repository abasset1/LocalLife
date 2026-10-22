import { Link } from "react-router-dom";
import { LEGAL_DOCUMENTS } from "../legal/legalDocuments";

interface TermsConsentCheckboxProps {
    checked: boolean;
    onChange: (checked: boolean) => void;
    id: string;
}

/**
 * Case de consentement exigée à la création d'un compte (contrat RGPD : `acceptTerms: true`
 * sur `POST /api/v1/auth/register` et sur `POST /api/v1/auth/google` quand il crée le compte).
 * Jamais cochée par défaut.
 */
function TermsConsentCheckbox({ checked, onChange, id }: TermsConsentCheckboxProps) {
    return (
        <div className="terms-consent">
            <input checked={checked} id={id} onChange={(event) => onChange(event.target.checked)} type="checkbox" />
            <label htmlFor={id}>
                J'ai au moins 15 ans et j'accepte les{" "}
                <Link rel="noopener" target="_blank" to={LEGAL_DOCUMENTS.terms.path}>conditions d'utilisation</Link> et la{" "}
                <Link rel="noopener" target="_blank" to={LEGAL_DOCUMENTS.privacy.path}>politique de confidentialité</Link>
            </label>
        </div>
    );
}

export default TermsConsentCheckbox;
