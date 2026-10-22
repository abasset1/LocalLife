import { LEGAL_PAGES, siteWebUrl } from '../legalLinks';

describe('siteWebUrl', () => {
  it("ouvre les textes légaux sur l'origine du site (celle de l'API)", () => {
    expect(siteWebUrl('https://locallife-beta.duckdns.org/', LEGAL_PAGES.privacy.path)).toBe(
      'https://locallife-beta.duckdns.org/confidentialite',
    );
    expect(siteWebUrl('http://192.168.1.20:8080', LEGAL_PAGES.terms.path)).toBe('http://192.168.1.20:8080/conditions');
    expect(siteWebUrl('https://exemple.fr/api', LEGAL_PAGES.legalNotice.path)).toBe(
      'https://exemple.fr/mentions-legales',
    );
  });

  it('refuse une URL invalide ou un autre protocole', () => {
    expect(siteWebUrl('pas une url', '/conditions')).toBeNull();
    expect(siteWebUrl('javascript:alert(1)', '/conditions')).toBeNull();
  });
});
