/**
 * Rubriques du menu rétractable (FRONT-REV-02, point C), sans dépendance à React Native pour rester
 * testables. Toutes les rubriques des anciens onglets et de l'écran « Compte » y sont, rien n'est retiré.
 */

export interface MenuEntry {
  label: string;
  href: string;
  icon: 'map' | 'list' | 'add' | 'account' | 'heart' | 'edit' | 'bell' | 'settings' | 'pro' | 'star';
  /** Ancien onglet : on y bascule (état conservé) plutôt que d'empiler un écran. */
  main?: boolean;
  /** Réservée aux comptes avec accès professionnel (`hasProAccess` de `GET /users/me`). */
  pro?: boolean;
  /** Réservée aux anciens comptes Pro qui possèdent encore des food trucks (consultation, archivage). */
  formerProOwner?: boolean;
}

export interface MenuSection {
  title: string;
  entries: readonly MenuEntry[];
  /** Rubriques réservées à un utilisateur connecté. */
  authenticated?: boolean;
}

export const MENU_SECTIONS: readonly MenuSection[] = [
  {
    title: 'Découvrir',
    entries: [
      { label: 'Carte', href: '/', icon: 'map', main: true },
      { label: 'Liste', href: '/liste', icon: 'list', main: true },
    ],
  },
  {
    title: 'Communauté',
    entries: [{ label: 'Proposer une activité', href: '/contribution', icon: 'add', main: true }],
  },
  {
    title: 'Mon espace',
    authenticated: true,
    entries: [
      { label: 'Mes favoris', href: '/favoris', icon: 'heart' },
      { label: 'Mes contributions', href: '/contributions', icon: 'edit' },
      { label: 'Mes alertes', href: '/alertes', icon: 'bell' },
      { label: 'Paramètres de recherche', href: '/parametres', icon: 'settings' },
      { label: 'Espace professionnel', href: '/pro', icon: 'pro', pro: true },
      { label: 'Mes food trucks', href: '/pro/foodtrucks', icon: 'pro', formerProOwner: true },
      { label: 'Mon abonnement', href: '/abonnement', icon: 'star' },
    ],
  },
  {
    title: 'Compte',
    entries: [{ label: 'Compte', href: '/compte', icon: 'account', main: true }],
  },
];

/**
 * Rubriques visibles : « Mon espace » une fois connecté, l'espace professionnel avec un accès pro ; sans accès pro,
 * « Mes food trucks » pour qui en possède encore (`ownsFoodTrucks`).
 */
export function visibleSections(access: {
  authenticated: boolean;
  proAccess: boolean;
  ownsFoodTrucks?: boolean;
}): MenuSection[] {
  return MENU_SECTIONS.filter((section) => !section.authenticated || access.authenticated).map((section) => ({
    ...section,
    entries: section.entries.filter(
      (entry) =>
        (!entry.pro || access.proAccess) &&
        (!entry.formerProOwner || (!access.proAccess && access.ownsFoodTrucks === true)),
    ),
  }));
}

/** Rubrique de l'écran affiché (`usePathname` d'expo-router : « / », « /liste »…). */
export function isCurrentEntry(entry: MenuEntry, pathname: string): boolean {
  return entry.href === '/' ? pathname === '/' || pathname === '' : pathname === entry.href;
}
