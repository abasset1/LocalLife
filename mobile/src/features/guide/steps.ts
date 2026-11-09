import { ALL_OPEN, hasOpenOffer, type LaunchFeatures } from '@/features/launch/launchFeatures';

/**
 * Contenu du guide de l'application (écran `/guide`), même parcours que le guide du site web
 * (`frontend/src/pages/GuidePage.tsx`), adapté au mobile. Sans dépendance à React Native pour rester
 * testable ; l'écran ne fait qu'animer ces scènes.
 */

/** Durée d'affichage d'une étape en lecture automatique (ms). */
export const GUIDE_STEP_DURATION_MS = 9000;

export interface GuideRow {
  emoji: string;
  title: string;
  meta: string;
}

/** Scène animée d'une étape : une maquette simplifiée de l'écran présenté. */
export type GuideScene =
  /** Grand pictogramme qui flotte, entouré de pictogrammes qui apparaissent un à un. */
  | { kind: 'hero'; emoji: string; orbit: string[] }
  /**
   * Carte : les marqueurs tombent un à un. Avec plusieurs couches, la carte passe de l'une à l'autre
   * (le bouton de la couche affichée est mis en avant).
   */
  | { kind: 'map'; layers: { label: string; pins: string[] }[]; preview?: GuideRow }
  /** Champ qui se remplit lettre par lettre, puis lignes de résultats. */
  | { kind: 'search'; query: string; rows: GuideRow[] }
  /** Groupes de puces qui se sélectionnent l'une après l'autre. */
  | { kind: 'chips'; groups: { label: string; chips: string[]; selected: number[] }[] }
  /** Lignes (cartes) qui glissent l'une après l'autre. */
  | { kind: 'rows'; header?: string; rows: GuideRow[] }
  /** Étapes successives d'un parcours (ex. : envoyée → en attente → publiée). */
  | { kind: 'flow'; stages: string[] };

export interface GuideStep {
  id: string;
  /** Étape montrée seulement si la fonction présentée est ouverte (lancement progressif, 05/11/2026). */
  available?: (features: LaunchFeatures) => boolean;
  title: string;
  text: string;
  tips: string[];
  scene: GuideScene;
}

export const GUIDE_STEPS: readonly GuideStep[] = [
  {
    id: 'bienvenue',
    title: 'Bienvenue sur Neiri',
    text:
      'Neiri rassemble sur une seule carte les sorties près de chez vous : concerts, expositions, marchés, ' +
      'sport, activités en famille, lieux à visiter et food trucks.',
    tips: ['Découvrir est gratuit, sans compte', 'Un compte débloque favoris, alertes et contributions'],
    scene: { kind: 'hero', emoji: '📍', orbit: ['🎭', '🎵', '🏃', '🍽️', '🎨', '🚚'] },
  },
  {
    id: 'carte',
    title: 'Explorez la carte',
    text:
      'Déplacez-vous et zoomez : la carte affiche les activités de la zone. Les nombres regroupent plusieurs ' +
      'activités proches ; touchez un marqueur ou un groupe pour afficher son aperçu, puis sa fiche.',
    tips: [
      'Dans un groupe, « ‹ › » passe d’une activité à l’autre',
      'Tirez l’aperçu vers le haut pour plus de détails, vers le bas pour le fermer',
      '« Voir la liste » montre les mêmes résultats, triés',
      'Le bouton ◎ à droite recentre la carte sur vous',
    ],
    scene: {
      kind: 'map',
      layers: [{ label: 'Activités', pins: ['📍', '📍', '📍', '📍'] }],
      preview: { emoji: '🎵', title: 'Concert au port', meta: 'Sam. 20:30 · Marseille' },
    },
  },
  {
    id: 'recherche',
    title: 'Choisissez une ville',
    text:
      'Tapez le nom d’une ville ou un code postal en haut de la carte : elle se recentre dessus. La vue Liste ' +
      'présente les mêmes activités, triées.',
    tips: ['La barre du bas passe de la Carte à la Liste ; le menu ☰ donne accès à toutes les rubriques'],
    scene: {
      kind: 'search',
      query: 'Aix-en-Provence',
      rows: [
        { emoji: '🏙️', title: 'Aix-en-Provence', meta: '13100 · Bouches-du-Rhône' },
        { emoji: '🎄', title: 'Marché de Noël', meta: 'Aix-en-Provence · 1er déc.' },
        { emoji: '🎁', title: 'Village des créateurs', meta: 'Aix-en-Provence · 7 déc.' },
      ],
    },
  },
  {
    id: 'filtres',
    title: 'Affinez avec les filtres',
    text:
      'Sous la recherche, les filtres affichent leur valeur : période (« Toutes dates », « Ce week-end »…), ' +
      'catégories, rayon et « Gratuit ». Touchez-en un pour le modifier, « Réinitialiser » pour tout retirer.',
    tips: ['Vos filtres se combinent', 'La période choisie vaut aussi pour la Liste'],
    scene: {
      kind: 'chips',
      groups: [
        { label: 'Catégories', chips: ['Culture', 'Musique', 'Sport', 'Famille'], selected: [1, 3] },
        { label: 'Période', chips: ['Aujourd’hui', 'Ce week-end', '7 jours'], selected: [1] },
        { label: 'Rayon', chips: ['5 km', '10 km', '25 km'], selected: [1] },
      ],
    },
  },
  {
    id: 'couches',
    available: (features) => features.places || features.foodTrucks,
    title: 'Activités, food trucks, Night et lieux',
    text:
      'Le bouton Affichage, à droite de la carte, choisit ce qu’elle montre : « Activités », « Food trucks », ' +
      '« Night » ou « Lieux » à visiter (musées, monuments, équipements…).',
    tips: ['L’aperçu d’un lieu liste ses activités à venir'],
    scene: {
      kind: 'map',
      layers: [
        { label: 'Activités', pins: ['📅', '📅', '📅'] },
        { label: '🏛 Lieux', pins: ['🏛', '🏰', '🏟'] },
        { label: '🚚 Food trucks', pins: ['🚚', '🚚'] },
      ],
    },
  },
  {
    id: 'favoris',
    title: 'Gardez vos coups de cœur',
    text:
      'La fiche d’une activité donne les dates, l’adresse, la description et le lien de l’organisateur. Le cœur ' +
      'l’ajoute à « Mes favoris », que vous retrouvez dans le menu.',
    tips: [
      '5 favoris avec l’offre gratuite, illimités ensuite',
      '🔔 « Suivre » : prévenu des mises à jour d’une activité ou d’un lieu (Premium et Pro)',
    ],
    scene: {
      kind: 'rows',
      header: '❤️ Mes favoris',
      rows: [
        { emoji: '🎭', title: 'Festival de théâtre de rue', meta: 'Du 12 au 14 juillet · Avignon' },
        { emoji: '🎨', title: 'Exposition « Lumières »', meta: 'Jusqu’au 30 nov. · Marseille' },
        { emoji: '🏃', title: 'Course des collines', meta: 'Dim. 9:00 · Aubagne' },
      ],
    },
  },
  {
    id: 'alertes',
    title: 'Soyez prévenu des nouveautés',
    text:
      'Créez une alerte sur vos catégories préférées (et les food trucks) autour d’une ville : chaque matin, ' +
      'Neiri vous prévient par notification sur ce téléphone, par e-mail, ou les deux.',
    tips: ['Proposée dès votre première connexion', 'Gérez vos alertes dans « Mes alertes »'],
    scene: {
      kind: 'rows',
      header: '🔔 Ce matin, près de Marseille',
      rows: [
        { emoji: '🎵', title: 'Jazz sur le Vieux-Port', meta: 'Nouvelle activité · Musique' },
        { emoji: '🧒', title: 'Atelier famille au musée', meta: 'Nouvelle activité · Famille' },
        { emoji: '🚚', title: 'Food truck place Castellane', meta: 'Présent aujourd’hui' },
      ],
    },
  },
  {
    id: 'contribuer',
    available: (features) => hasOpenOffer(features),
    title: 'Proposez une activité',
    text:
      'Vous organisez un événement ? « Proposer une activité » dans le menu : titre, catégories, dates, ' +
      'adresse. Elle est relue par l’équipe avant d’apparaître sur la carte.',
    tips: ['Suivez-les dans « Mes contributions »', 'Vous pouvez modifier ou annuler vos propositions'],
    scene: { kind: 'flow', stages: ['✍️ Proposée', '⏳ En attente de modération', '✅ Publiée sur la carte'] },
  },
  {
    id: 'compte',
    title: 'Personnalisez votre espace',
    text:
      'La rubrique Compte réunit vos paramètres de recherche (ville, rayon et catégories à l’ouverture de la ' +
      'carte), vos alertes, votre abonnement, votre mot de passe et vos données.',
    tips: ['Revoyez ce guide depuis Compte ou les Paramètres de recherche'],
    scene: {
      kind: 'rows',
      rows: [
        { emoji: '🧭', title: 'Paramètres de recherche', meta: 'Ville, rayon et catégories' },
        { emoji: '🔔', title: 'Mes alertes', meta: 'Notification ou e-mail' },
        { emoji: '⭐', title: 'Mon abonnement', meta: 'Gratuit, Premium ou Pro' },
        { emoji: '🔒', title: 'Mot de passe et compte', meta: 'Sécurité, suppression' },
      ],
    },
  },
  {
    id: 'offres',
    available: (features) => hasOpenOffer(features),
    title: 'Choisissez votre offre',
    text:
      'La carte reste gratuite pour tous. Premium élargit favoris, alertes et contributions, et permet de suivre ' +
      'activités et lieux. Pro ouvre l’Espace ' +
      'professionnel : gérer un lieu, ses food trucks et leur planning.',
    tips: ['Changez d’offre dans « Mon abonnement »', 'L’offre Pro est validée par l’équipe'],
    scene: {
      kind: 'rows',
      rows: [
        { emoji: '🌱', title: 'Gratuit', meta: 'Carte, liste et fiches · 5 favoris · 1 alerte' },
        { emoji: '⭐', title: 'Premium', meta: 'Favoris illimités · 10 alertes · suivis · 2 contributions / mois' },
        { emoji: '🏢', title: 'Pro', meta: 'Contributions illimitées · lieux et food trucks' },
      ],
    },
  },
  {
    id: 'fin',
    title: 'À vous de jouer !',
    text:
      'Vous connaissez l’essentiel de Neiri. Retrouvez ce guide à tout moment dans le menu (rubrique ' +
      '« Aide »), dans Compte ou dans les Paramètres de recherche.',
    tips: [],
    scene: { kind: 'hero', emoji: '🚀', orbit: ['🎉', '✨', '🎊', '⭐', '🎈', '💫'] },
  },
];

/** Étapes du guide pour les fonctions ouvertes. */
export function guideStepsFor(features: LaunchFeatures = ALL_OPEN): readonly GuideStep[] {
  return GUIDE_STEPS.filter((step) => !step.available || step.available(features));
}

/** Étape suivante ou précédente, bornée au guide. */
export function clampStep(index: number, steps: readonly GuideStep[] = GUIDE_STEPS): number {
  return Math.max(0, Math.min(steps.length - 1, index));
}

/** Prochaine étape en lecture automatique, ou `null` à la dernière (le guide s'y arrête). */
export function nextAutoplayStep(index: number, steps: readonly GuideStep[] = GUIDE_STEPS): number | null {
  return index >= steps.length - 1 ? null : index + 1;
}
