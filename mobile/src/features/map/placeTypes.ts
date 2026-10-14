/**
 * Libellés français des types de lieux permanents (ADR-0011, phase B, liste
 * blanche du groupe 1). Même liste que `PlaceTypeWhitelist` (backend) et
 * `frontend/src/components/places/placeTypes.ts`.
 */
export const PLACE_TYPE_LABELS: Record<string, string> = {
  Museum: 'Musée',
  Theater: 'Théâtre',
  Cinema: 'Cinéma',
  Library: 'Bibliothèque',
  InterpretationCentre: 'Centre d’interprétation',
  Castle: 'Château',
  Palace: 'Palais',
  Tower: 'Tour',
  Dungeon: 'Donjon',
  Fort: 'Fort',
  Bridge: 'Pont',
  Aqueduct: 'Aqueduc',
  Ruins: 'Ruines',
  RomanPath: 'Voie romaine',
  Mill: 'Moulin',
  WashHouse: 'Lavoir',
  CoveredMarket: 'Halle',
  IndustrialSite: 'Site industriel',
  Mine: 'Mine',
  RemembranceSite: 'Lieu de mémoire',
  ParkAndGarden: 'Parc et jardin',
  Church: 'Église',
  Chapel: 'Chapelle',
  Abbey: 'Abbaye',
  Cathedral: 'Cathédrale',
  Basilica: 'Basilique',
  Collegiate: 'Collégiale',
  Convent: 'Couvent',
  Monastery: 'Monastère',
  Synagogue: 'Synagogue',
  Temple: 'Temple',
  Commanderie: 'Commanderie',
  SwimmingPool: 'Piscine',
  Stadium: 'Stade',
  TennisComplex: 'Tennis',
  BoulesPitch: 'Boulodrome',
  TrackRollerOrSkateBoard: 'Skatepark',
  FitnessPath: 'Parcours de santé',
  PlayArea: 'Aire de jeux',
  Beach: 'Plage et plan d’eau',
  PointOfView: 'Point de vue',
  TechnicalHeritage: 'Patrimoine technique',
  ArcheologicalSite: 'Site archéologique',
  DefenceSite: 'Site défensif',
  NaturalHeritage: 'Patrimoine naturel',
  CulturalSite: 'Site culturel',
};

/** Libellé français d'un type ; le nom DATAtourisme s'il est inconnu. */
export function placeTypeLabel(type: string | null | undefined): string {
  if (!type) return 'Lieu';
  return PLACE_TYPE_LABELS[type] ?? type;
}
