import { ALERTS_NOUN, FAVORITES_NOUN, PLAN_LIMITS, contributionQuota, formatUsage, isAtLimit } from '../limits';

const FREE = { maxLikes: 5, maxAlerts: 1, monthlyContributions: 0, contributionsThisMonth: 0 };
const PREMIUM = { maxLikes: null, maxAlerts: 10, monthlyContributions: 2, contributionsThisMonth: 1 };
const PRO = { maxLikes: null, maxAlerts: 10, monthlyContributions: null, contributionsThisMonth: 4 };

describe('limites des offres', () => {
  it('affiche l’usage d’une limite, rien quand c’est illimité', () => {
    expect(formatUsage(3, 5, FAVORITES_NOUN)).toBe('3 / 5 favoris');
    expect(formatUsage(1, 1, ALERTS_NOUN)).toBe('1 / 1 alerte');
    expect(formatUsage(2, 10, ALERTS_NOUN)).toBe('2 / 10 alertes');
    expect(formatUsage(12, null, FAVORITES_NOUN)).toBeNull();
  });

  it('dit si la limite est atteinte, y compris au-delà (éléments conservés)', () => {
    expect(isAtLimit(4, 5)).toBe(false);
    expect(isAtLimit(5, 5)).toBe(true);
    expect(isAtLimit(7, 5)).toBe(true);
    expect(isAtLimit(100, null)).toBe(false);
    expect(isAtLimit(100, undefined)).toBe(false);
  });

  it('ne montre le quota mensuel de contributions qu’aux comptes Premium', () => {
    expect(contributionQuota(PREMIUM)).toEqual({ text: 'Contributions ce mois-ci : 1 / 2', atLimit: false });
    expect(contributionQuota({ ...PREMIUM, contributionsThisMonth: 2 })?.atLimit).toBe(true);
    expect(contributionQuota(PRO)).toBeNull();
    expect(contributionQuota(FREE)).toBeNull();
    expect(contributionQuota(undefined)).toBeNull();
  });

  it('résume les limites de chaque offre', () => {
    expect(PLAN_LIMITS.FREE).toEqual(['5 favoris', '1 alerte']);
    expect(PLAN_LIMITS.PREMIUM).toContain('2 contributions par mois');
    expect(PLAN_LIMITS.PRO).toContain('Contributions illimitées');
  });
});
