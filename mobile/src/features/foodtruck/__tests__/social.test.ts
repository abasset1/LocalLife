import { socialLinks, socialUrl } from '../social';

describe('socialUrl', () => {
  it('accepte une adresse https du réseau attendu', () => {
    expect(socialUrl('https://www.instagram.com/tacosloco', 'instagram')).toBe('https://www.instagram.com/tacosloco');
    expect(socialUrl('https://m.facebook.com/tacosloco', 'facebook')).toBe('https://m.facebook.com/tacosloco');
  });

  it('refuse un autre site, un autre protocole ou un texte', () => {
    expect(socialUrl('https://evil.example/instagram.com', 'instagram')).toBeNull();
    expect(socialUrl('javascript:alert(1)', 'facebook')).toBeNull();
    expect(socialUrl('https://www.facebook.com/x', 'instagram')).toBeNull();
    expect(socialUrl('@tacosloco', 'instagram')).toBeNull();
    expect(socialUrl(null, 'instagram')).toBeNull();
  });
});

describe('socialLinks', () => {
  it('liste Instagram puis Facebook', () => {
    expect(socialLinks({ instagramUrl: 'https://www.instagram.com/a', facebookUrl: 'https://www.facebook.com/b' }))
      .toEqual([
        { network: 'instagram', label: 'Instagram', url: 'https://www.instagram.com/a' },
        { network: 'facebook', label: 'Facebook', url: 'https://www.facebook.com/b' },
      ]);
    expect(socialLinks({})).toEqual([]);
  });
});
