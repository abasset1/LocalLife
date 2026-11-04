import { FEEDBACK_KINDS, feedbackMessageError, feedbackOrigin } from '../feedbackForm';

describe('formulaire « Une idée, un problème ? »', () => {
  it('propose une idée ou un problème, comme le site web', () => {
    expect(FEEDBACK_KINDS.map((kind) => kind.value)).toEqual(['SUGGESTION', 'BUG']);
  });

  it('exige quelques mots et refuse un message trop long', () => {
    expect(feedbackMessageError('')).not.toBeNull();
    expect(feedbackMessageError('   abc   ')).not.toBeNull();
    expect(feedbackMessageError('Super appli')).toBeNull();
    expect(feedbackMessageError('a'.repeat(2000))).toBeNull();
    expect(feedbackMessageError('a'.repeat(2001))).toContain('2000');
  });

  it('indique l’application et son système', () => {
    expect(feedbackOrigin('android', 34)).toBe('Application mobile (android 34)');
  });
});
