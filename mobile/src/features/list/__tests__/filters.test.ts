import { distanceKm, formatDistance, matchesQuery, normalize, periodRange } from '../filters';

// Mois indexés à partir de 0 : new Date(2026, 9, 14) = mercredi 14 octobre 2026 (heure locale).
const WEDNESDAY = new Date(2026, 9, 14, 10, 0);
const SATURDAY = new Date(2026, 9, 17, 10, 0);
const SUNDAY = new Date(2026, 9, 18, 10, 0);

describe('periodRange', () => {
  it('sans période : aucun filtre de date', () => {
    expect(periodRange('all', WEDNESDAY)).toEqual({});
  });

  it("aujourd'hui : la date du jour seule", () => {
    expect(periodRange('today', WEDNESDAY)).toEqual({ date: '2026-10-14' });
  });

  it('7 jours : du jour au sixième jour suivant', () => {
    expect(periodRange('week', WEDNESDAY)).toEqual({ date: '2026-10-14', dateTo: '2026-10-20' });
  });

  it('en semaine : le prochain week-end', () => {
    expect(periodRange('weekend', WEDNESDAY)).toEqual({ date: '2026-10-17', dateTo: '2026-10-18' });
  });

  it('le samedi : ce samedi et le dimanche', () => {
    expect(periodRange('weekend', SATURDAY)).toEqual({ date: '2026-10-17', dateTo: '2026-10-18' });
  });

  it('le dimanche : le jour même seulement', () => {
    expect(periodRange('weekend', SUNDAY)).toEqual({ date: '2026-10-18' });
  });

  it('change de mois correctement', () => {
    expect(periodRange('week', new Date(2026, 9, 29))).toEqual({ date: '2026-10-29', dateTo: '2026-11-04' });
  });
});

describe('distanceKm / formatDistance', () => {
  it('Marseille – Aix-en-Provence ≈ 25 km', () => {
    const km = distanceKm({ latitude: 43.2965, longitude: 5.3698 }, { latitude: 43.5297, longitude: 5.4474 });
    expect(km).toBeGreaterThan(24);
    expect(km).toBeLessThan(28);
  });

  it('formate en mètres, en kilomètres décimaux puis entiers', () => {
    expect(formatDistance(0.004)).toBe('10 m');
    expect(formatDistance(0.347)).toBe('350 m');
    expect(formatDistance(2.43)).toBe('2,4 km');
    expect(formatDistance(18.4)).toBe('18 km');
  });
});

describe('matchesQuery', () => {
  const activity = { title: 'Fête du quartier', category: 'Musique', city: 'Marseille', description: null };

  it('ignore la casse et les accents', () => {
    expect(normalize('  FÊTE ')).toBe('fete');
    expect(matchesQuery(activity, 'fete')).toBe(true);
  });

  it('exige chaque mot, dans le titre, la catégorie ou la commune', () => {
    expect(matchesQuery(activity, 'musique marseille')).toBe(true);
    expect(matchesQuery(activity, 'musique lyon')).toBe(false);
  });

  it('une recherche vide garde tout', () => {
    expect(matchesQuery(activity, '   ')).toBe(true);
  });
});
