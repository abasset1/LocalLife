import type { Contribution } from '@/api';

import { EMPTY_FORM, formatWhen, fromContribution, isEditable, normalizeTime, toRequest } from '../form';

const VALID = {
  ...EMPTY_FORM,
  title: ' Food truck ',
  categoryCodes: ['GASTRONOMIE'],
  address: 'Place Bellecour, Lyon',
};

const CONTRIBUTION: Contribution = {
  id: 4,
  title: 'Food truck',
  description: 'Burgers',
  status: 'PUBLISHED',
  address: 'Place Bellecour',
  city: 'Lyon',
  startDate: '2026-11-03T11:30:00',
  endDate: '2026-11-26T14:00:00',
  categoryCodes: ['GASTRONOMIE'],
  url: null,
  phone: '0612345678',
  email: null,
  recurrence: { daysOfWeek: ['TU', 'TH'], startTime: '11:30:00', endTime: '14:00:00', from: '2026-11-01', until: null },
};

describe('toRequest', () => {
  it('builds a one-off contribution, trimming fields and dropping empty optional ones', () => {
    const result = toRequest({ ...VALID, startDate: '2026-11-03', phone: ' ' });
    expect(result).toEqual({
      ok: true,
      request: {
        title: 'Food truck',
        description: undefined,
        categoryCodes: ['GASTRONOMIE'],
        address: 'Place Bellecour, Lyon',
        url: undefined,
        phone: undefined,
        email: undefined,
        startDate: '2026-11-03',
        endDate: undefined,
      },
    });
  });

  it('builds a weekly recurrence with days in week order and normalized times', () => {
    const result = toRequest({
      ...VALID,
      recurring: true,
      days: ['TH', 'TU'],
      startTime: '11h30',
      endTime: '14',
      startDate: '2026-11-01',
    });
    expect(result.ok && result.request.recurrence).toEqual({
      daysOfWeek: ['TU', 'TH'],
      startTime: '11:30',
      endTime: '14:00',
      from: '2026-11-01',
      until: null,
    });
    expect(result.ok && result.request.startDate).toBeUndefined();
  });

  it.each([
    [{ ...VALID, title: ' ' }, 'Le titre est obligatoire.'],
    [{ ...VALID, address: '' }, "L'adresse est obligatoire."],
    [{ ...VALID, categoryCodes: [] }, 'Choisissez au moins une catégorie.'],
    [{ ...VALID, startDate: '03/11/2026' }, 'La date de début doit être au format AAAA-MM-JJ.'],
    [{ ...VALID, recurring: true, startTime: '11:30' }, 'Choisissez au moins un jour de passage.'],
    [{ ...VALID, recurring: true, days: ['MO'] }, "L'heure d'arrivée est obligatoire (ex. 11:30)."],
    [
      { ...VALID, recurring: true, days: ['MO'], startTime: '11:30', endTime: '25:00' },
      "L'heure de départ doit être au format HH:MM (ex. 14:00).",
    ],
  ])('rejects an invalid form', (form, error) => {
    expect(toRequest(form)).toEqual({ ok: false, error });
  });
});

describe('fromContribution', () => {
  it('prefills a recurring contribution from its recurrence', () => {
    expect(fromContribution(CONTRIBUTION)).toMatchObject({
      recurring: true,
      days: ['TU', 'TH'],
      startTime: '11:30',
      endTime: '14:00',
      startDate: '2026-11-01',
      endDate: '',
      phone: '0612345678',
    });
  });

  it('prefills a one-off contribution from its dates', () => {
    expect(fromContribution({ ...CONTRIBUTION, recurrence: null })).toMatchObject({
      recurring: false,
      startDate: '2026-11-03',
      endDate: '2026-11-26',
    });
  });

  it('round-trips into the same recurrence', () => {
    const result = toRequest(fromContribution(CONTRIBUTION));
    expect(result.ok && result.request.recurrence?.daysOfWeek).toEqual(['TU', 'TH']);
  });
});

describe('helpers', () => {
  it('normalizes common time spellings', () => {
    expect(normalizeTime('9h30')).toBe('09:30');
    expect(normalizeTime('9:30')).toBe('09:30');
    expect(normalizeTime('0930')).toBe('09:30');
    expect(normalizeTime('18')).toBe('18:00');
  });

  it('describes when a contribution happens', () => {
    expect(formatWhen(CONTRIBUTION)).toBe('Tous les mardis et jeudis à 11h30');
    expect(formatWhen({ ...CONTRIBUTION, recurrence: null })).toBe('Le 03/11/2026');
  });

  it('only allows editing pending, published or rejected contributions', () => {
    expect(isEditable('REJECTED')).toBe(true);
    expect(isEditable('CANCELLED')).toBe(false);
  });
});
