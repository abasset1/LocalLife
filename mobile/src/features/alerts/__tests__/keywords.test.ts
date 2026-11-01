import { addKeywords, comparableKeyword, formatKeywords } from '../keywords';

describe('mots-clés des alertes', () => {
  it('compare sans majuscules, accents ni ponctuation', () => {
    expect(comparableKeyword('  Marché de NOËL ! ')).toBe('marche de noel');
  });

  it('ajoute sans doublon et garde l’ordre de saisie', () => {
    expect(addKeywords(['Noël'], 'jazz, noel ,  marché   de Noël')).toEqual({
      keywords: ['Noël', 'jazz', 'marché de Noël'],
    });
  });

  it('refuse un mot-clé trop court, sans lettre, ou plus de 10', () => {
    expect(addKeywords([], 'a')).toEqual({ error: 'Un mot-clé fait de 2 à 60 caractères.' });
    expect(addKeywords([], '!!!')).toEqual({ error: 'Un mot-clé doit contenir au moins une lettre ou un chiffre.' });
    const ten = Array.from({ length: 10 }, (_, index) => `mot${index}`);
    expect(addKeywords(ten, 'encore')).toEqual({ error: 'Une alerte a 10 mots-clés au plus.' });
  });

  it('affiche les mots-clés entre guillemets', () => {
    expect(formatKeywords(['jazz', 'marché de Noël'])).toBe('« jazz », « marché de Noël »');
    expect(formatKeywords(undefined)).toBe('');
  });
});
