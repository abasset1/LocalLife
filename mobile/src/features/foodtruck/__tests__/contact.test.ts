import { contactAction } from '../contact';

describe('contactAction', () => {
  it('retourne null sans contact', () => {
    expect(contactAction(null)).toBeNull();
    expect(contactAction('   ')).toBeNull();
  });

  it('reconnaît un lien web', () => {
    expect(contactAction(' https://exemple.fr ')).toEqual({
      kind: 'web',
      url: 'https://exemple.fr',
      label: 'Ouvrir le lien',
    });
  });

  it('reconnaît un numéro de téléphone et le nettoie', () => {
    expect(contactAction('+33 6 12 34 56 78')).toMatchObject({ kind: 'phone', url: 'tel:+33612345678' });
    expect(contactAction('06.12.34.56.78')).toMatchObject({ kind: 'phone', url: 'tel:0612345678' });
  });

  it('reconnaît une adresse e-mail', () => {
    expect(contactAction('contact@exemple.fr')).toMatchObject({
      kind: 'email',
      url: 'mailto:contact@exemple.fr',
    });
  });

  it('laisse le reste en texte simple', () => {
    expect(contactAction('@montruck sur Instagram')).toEqual({ kind: 'text' });
    expect(contactAction('javascript:alert(1)')).toEqual({ kind: 'text' });
  });
});
