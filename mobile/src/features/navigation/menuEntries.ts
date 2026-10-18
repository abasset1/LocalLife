/**
 * Rubriques du menu rétractable (FRONT-REV-02, point C), sans dépendance à React Native pour rester
 * testables. Toutes les rubriques des anciens onglets et de l'écran « Compte » y sont, rien n'est retiré.
 */

export interface MenuEntry {
  label: string;
  href: string;
  icon: 'map' | 'list' | 'add' | 'account' | 'heart' | 'edit' | 'bell' | 'settings';
  /** Ancien onglet : on y bascule (état conservé) plutôt que d'empiler un écran. */
  main?: boolean;
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
    ],
  },
  {
    title: 'Compte',
    entries: [{ label: 'Compte', href: '/compte', icon: 'account', main: true }],
  },
];

/** Rubrique de l'écran affiché (`usePathname` d'expo-router : « / », « /liste »…). */
export function isCurrentEntry(entry: MenuEntry, pathname: string): boolean {
  return entry.href === '/' ? pathname === '/' || pathname === '' : pathname === entry.href;
}
