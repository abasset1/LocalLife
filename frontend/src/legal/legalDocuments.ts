/**
 * Textes légaux publiés sur le site (contrat RGPD du 22/10/2026) : politique de
 * confidentialité, conditions d'utilisation, mentions légales. Le contenu est encore à
 * rédiger ; seule la date de version est fixée ici, côté client.
 */
export const LEGAL_VERSION = "2026-10-22";

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
