/**
 * Textes légaux (contrat RGPD du 22/10/2026) : rédigés et servis par le site, sous le même domaine que l'API
 * (comme la console d'administration). L'application ne fait qu'ouvrir ces pages.
 */
export const LEGAL_PAGES = {
  privacy: { path: '/confidentialite', label: 'Politique de confidentialité' },
  terms: { path: '/conditions', label: "Conditions d'utilisation" },
  legalNotice: { path: '/mentions-legales', label: 'Mentions légales' },
} as const;

export type LegalPage = keyof typeof LEGAL_PAGES;

/** Adresse d'une page du site, sur l'origine de l'API ; `null` si l'URL configurée n'est pas une adresse web. */
export function siteWebUrl(apiBaseUrl: string, path: string): string | null {
  try {
    const url = new URL(apiBaseUrl);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
    return `${url.origin}${path}`;
  } catch {
    return null;
  }
}
