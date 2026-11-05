import { ALL_OPEN, hasOpenOffer, type LaunchFeatures } from '@/features/launch/launchFeatures';

/**
 * Rubriques du menu rétractable (FRONT-REV-02, point C), sans dépendance à React Native pour rester
 * testables. Toutes les rubriques des anciens onglets et de l'écran « Compte » y sont, rien n'est retiré.
 */

export interface MenuEntry {
  label: string;
  href: string;
  icon: 'map' | 'list' | 'add' | 'account' | 'heart' | 'edit' | 'bell' | 'settings' | 'pro' | 'star' | 'help' | 'feedback';
  /** Ancien onglet : on y bascule (état conservé) plutôt que d'empiler un écran. */
  main?: boolean;
  /** Réservée aux comptes avec accès professionnel (`hasProAccess` de `GET /users/me`). */
  pro?: boolean;
  /** Réservée aux anciens comptes Pro qui possèdent encore des food trucks (consultation, archivage). */
  formerProOwner?: boolean;
  /**
   * Fonctions à ouvrir pour afficher la rubrique (lancement progressif, 05/11/2026) ; `offer` : Premium ou Pro.
   * Toutes doivent être ouvertes.
   */
  requires?: readonly Requirement[];
}

type Requirement = keyof LaunchFeatures | 'offer';

function isMet(requirement: Requirement, features: LaunchFeatures): boolean {
  return requirement === 'offer' ? hasOpenOffer(features) : features[requirement];
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
    entries: [{ label: 'Proposer une activité', href: '/contribution', icon: 'add', main: true, requires: ['offer'] }],
  },
  {
    title: 'Mon espace',
    authenticated: true,
    entries: [
      { label: 'Mes favoris', href: '/favoris', icon: 'heart' },
      { label: 'Activités et lieux suivis', href: '/suivis', icon: 'bell', requires: ['premium'] },
      { label: 'Mes contributions', href: '/contributions', icon: 'edit' },
      { label: 'Mes alertes', href: '/alertes', icon: 'bell' },
      { label: 'Paramètres de recherche', href: '/parametres', icon: 'settings' },
      { label: 'Espace professionnel', href: '/pro', icon: 'pro', pro: true },
      {
        label: 'Mes food trucks',
        href: '/pro/foodtrucks',
        icon: 'pro',
        formerProOwner: true,
        requires: ['pro', 'foodTrucks'],
      },
      { label: 'Mon abonnement', href: '/abonnement', icon: 'star', requires: ['offer'] },
    ],
  },
  {
    title: 'Compte',
    entries: [{ label: 'Compte', href: '/compte', icon: 'account', main: true }],
  },
  {
    title: 'Aide',
    entries: [
      { label: 'Nouveautés', href: '/nouveautes', icon: 'star', requires: ['premium'] },
      { label: "Guide de l'application", href: '/guide', icon: 'help' },
      { label: 'Une idée, un problème ?', href: '/retours', icon: 'feedback' },
    ],
  },
];

/**
 * Rubriques visibles : « Mon espace » une fois connecté, l'espace professionnel avec un accès pro ; sans accès pro,
 * « Mes food trucks » pour qui en possède encore (`ownsFoodTrucks`). Les rubriques d'une fonction pas encore
 * ouverte (`features`, tout ouvert par défaut) sont masquées ; une section vidée disparaît.
 */
export function visibleSections(access: {
  authenticated: boolean;
  proAccess: boolean;
  ownsFoodTrucks?: boolean;
  features?: LaunchFeatures;
}): MenuSection[] {
  const features = access.features ?? ALL_OPEN;
  return MENU_SECTIONS.filter((section) => !section.authenticated || access.authenticated).map((section) => ({
    ...section,
    entries: section.entries.filter(
      (entry) =>
        (!entry.pro || access.proAccess) &&
        (!entry.formerProOwner || (!access.proAccess && access.ownsFoodTrucks === true)) &&
        (entry.requires ?? []).every((requirement) => isMet(requirement, features)),
    ),
  })).filter((section) => section.entries.length > 0);
}

/** Rubrique de l'écran affiché (`usePathname` d'expo-router : « / », « /liste »…). */
export function isCurrentEntry(entry: MenuEntry, pathname: string): boolean {
  return entry.href === '/' ? pathname === '/' || pathname === '' : pathname === entry.href;
}
