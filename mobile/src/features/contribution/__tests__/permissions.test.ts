import { canManageActivity } from '../permissions';

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
