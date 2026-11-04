/** Réseaux sociaux d'un food truck : liens Instagram et Facebook enregistrés par son propriétaire. */
const SOCIAL_HOSTS = {
  instagram: ['instagram.com', 'instagr.am'],
  facebook: ['facebook.com', 'fb.com', 'fb.me'],
} as const;

export type SocialNetwork = keyof typeof SOCIAL_HOSTS;

/** Lien affichable : une adresse https du réseau attendu, sinon `null`. */
export function socialUrl(url: string | null | undefined, network: SocialNetwork): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    const known = SOCIAL_HOSTS[network].some((domain) => host === domain || host.endsWith(`.${domain}`));
    return parsed.protocol === 'https:' && known ? url : null;
  } catch {
    return null;
  }
}

/** Liens Instagram puis Facebook d'un food truck, ceux qui sont valides seulement. */
export function socialLinks(foodTruck: {
  instagramUrl?: string | null;
  facebookUrl?: string | null;
}): { network: SocialNetwork; label: string; url: string }[] {
  const links: { network: SocialNetwork; label: string; url: string }[] = [];
  const instagram = socialUrl(foodTruck.instagramUrl, 'instagram');
  const facebook = socialUrl(foodTruck.facebookUrl, 'facebook');
  if (instagram) links.push({ network: 'instagram', label: 'Instagram', url: instagram });
  if (facebook) links.push({ network: 'facebook', label: 'Facebook', url: facebook });
  return links;
}
