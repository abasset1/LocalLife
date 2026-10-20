import { adminWebUrl, shouldOfferAdminLink } from '../adminLink';

describe('adminWebUrl', () => {
  it("pointe vers la console du site, sur le domaine de l'API", () => {
    expect(adminWebUrl('https://locallife-beta.duckdns.org')).toBe('https://locallife-beta.duckdns.org/admin');
    expect(adminWebUrl('http://192.168.1.20:8080/')).toBe('http://192.168.1.20:8080/admin');
  });

  it('refuse une URL invalide ou un autre protocole', () => {
    expect(adminWebUrl('pas une url')).toBeNull();
    expect(adminWebUrl('javascript:alert(1)')).toBeNull();
  });
});

describe('shouldOfferAdminLink', () => {
  it("ne propose le lien qu'à un administrateur confirmé par le serveur", () => {
    expect(shouldOfferAdminLink('ADMIN')).toBe(true);
    expect(shouldOfferAdminLink('PRO')).toBe(false);
    expect(shouldOfferAdminLink('USER')).toBe(false);
    expect(shouldOfferAdminLink(undefined)).toBe(false);
  });
});
