import {
  canCancelActivity,
  canManageActivity,
  requiresModerationReason,
  showsModerationReason,
} from '../permissions';

describe('canManageActivity', () => {
  const contributor = { userId: 7, role: 'USER' };

  it('lets the creator manage their activity', () => {
    expect(canManageActivity(contributor, 7)).toBe(true);
  });

  it('refuses another user, an anonymous visitor and imported activities', () => {
    expect(canManageActivity(contributor, 8)).toBe(false);
    expect(canManageActivity(null, 7)).toBe(false);
    expect(canManageActivity(contributor, null)).toBe(false);
  });

  it('lets an admin manage any contributed activity, but not an imported one', () => {
    expect(canManageActivity({ userId: 1, role: 'ADMIN' }, 7)).toBe(true);
    expect(canManageActivity({ userId: 1, role: 'ADMIN' }, null)).toBe(false);
  });
});

describe('requiresModerationReason', () => {
  const admin = { userId: 1, role: 'ADMIN' };

  it('demande un motif à un administrateur qui supprime la contribution d’un autre', () => {
    expect(requiresModerationReason(admin, 7)).toBe(true);
    expect(requiresModerationReason(admin, 7, 'ADMIN')).toBe(true);
  });

  it('pas de motif pour sa propre contribution, ni pour un compte qui n’est plus administrateur', () => {
    expect(requiresModerationReason(admin, 1)).toBe(false);
    expect(requiresModerationReason({ userId: 7, role: 'USER' }, 7)).toBe(false);
    // Rôle relu par le serveur : il prime sur celui du jeton.
    expect(requiresModerationReason(admin, 7, 'USER')).toBe(false);
    expect(requiresModerationReason(null, 7)).toBe(false);
  });

  it('ne propose l’annulation que pour une activité en attente ou publiée', () => {
    expect(canCancelActivity('PENDING')).toBe(true);
    expect(canCancelActivity('PUBLISHED')).toBe(true);
    expect(canCancelActivity('CANCELLED')).toBe(false);
    expect(canCancelActivity('REJECTED')).toBe(false);
    expect(canCancelActivity(null)).toBe(false);
  });

  it('montre le motif des contributions refusées ou annulées seulement', () => {
    expect(showsModerationReason('REJECTED')).toBe(true);
    expect(showsModerationReason('CANCELLED')).toBe(true);
    expect(showsModerationReason('PUBLISHED')).toBe(false);
  });
});
