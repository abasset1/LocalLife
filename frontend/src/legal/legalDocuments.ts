/**
 * Textes légaux publiés sur le site (contrat RGPD du 22/10/2026) : politique de
 * confidentialité, conditions d'utilisation, mentions légales. Leur contenu est dans
 * `legalContent.ts` ; la version doit rester égale à `LegalTerms.VERSION` côté serveur.
 */
export const LEGAL_VERSION = "2026-11-05";

export type LegalDocumentKey = "privacy" | "terms" | "legalNotice";

export const LEGAL_DOCUMENTS: Record<LegalDocumentKey, { path: string; title: string }> = {
    privacy: { path: "/confidentialite", title: "Politique de confidentialité" },
    terms: { path: "/conditions", title: "Conditions d'utilisation" },
    legalNotice: { path: "/mentions-legales", title: "Mentions légales" },
};

/** `2026-10-22` → `22/10/2026` (sans passer par `Date`, pour ne dépendre d'aucun fuseau horaire). */
export function formatLegalVersion(version: string = LEGAL_VERSION): string {
    const [year, month, day] = version.split("-");
    return `${day}/${month}/${year}`;
}
