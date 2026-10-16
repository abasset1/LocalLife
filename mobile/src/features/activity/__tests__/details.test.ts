import {
  accessibilityLabels,
  formatAgeRange,
  formatOccurrence,
  formatRecurrence,
  formatSchedule,
  formatTimeRange,
  linkAction,
  upcomingOccurrences,
} from '../details';
import { buildCalendarUrl, shareMessage } from '../calendar';

describe('formatRecurrence', () => {
  it('traduit les jours de la semaine, jamais la règle brute', () => {
    expect(formatRecurrence('FREQ=WEEKLY;BYDAY=TU')).toBe('Tous les mardis');
    expect(formatRecurrence('FREQ=WEEKLY;BYDAY=MO,WE,FR')).toBe('Tous les lundis, mercredis et vendredis');
    expect(formatRecurrence('FREQ=DAILY')).toBe('Tous les jours');
    expect(formatRecurrence('FREQ=YEARLY')).toBe('Se répète régulièrement');
    expect(formatRecurrence(null)).toBeNull();
  });
});

describe('horaires', () => {
  it('formate une plage horaire', () => {
    expect(formatTimeRange('11:30:00', '14:00:00')).toBe('11h30 – 14h00');
    expect(formatTimeRange('11:30:00', null)).toBe('11h30');
    expect(formatTimeRange(null, '14:00:00')).toBeNull();
  });

  it('résume un horaire régulier', () => {
    expect(
      formatSchedule({
        id: 1,
        locationId: null,
        startTime: '11:30:00',
        endTime: '14:00:00',
        validFrom: null,
        validUntil: null,
        timezone: null,
        recurrenceRule: 'FREQ=WEEKLY;BYDAY=TU',
      }),
    ).toBe('Tous les mardis · 11h30 – 14h00');
  });
});

describe('occurrences', () => {
  const now = new Date('2026-10-16T12:00:00');
  const occ = (id: number, startAt: string, endAt: string | null = null, status = 'SCHEDULED') => ({
    id,
    scheduleId: null,
    startAt,
    endAt,
    locationId: null,
    status,
  });

  it('garde les dates à venir non annulées, triées', () => {
    const list = [
      occ(1, '2026-10-20T10:00:00'),
      occ(2, '2026-10-10T10:00:00'),
      occ(3, '2026-10-18T10:00:00', null, 'CANCELLED'),
      occ(4, '2026-10-17T10:00:00'),
      occ(5, '2026-10-16T08:00:00', '2026-10-16T18:00:00'),
    ];
    expect(upcomingOccurrences(list, now).map((o) => o.id)).toEqual([5, 4, 1]);
  });

  it('affiche le jour et les heures', () => {
    expect(formatOccurrence(occ(1, '2026-10-17T10:00:00', '2026-10-17T12:30:00'))).toBe(
      'samedi 17 octobre · 10h00 – 12h30',
    );
  });
});

describe('formatAgeRange', () => {
  it('couvre les bornes', () => {
    expect(formatAgeRange(6, 12)).toBe('De 6 à 12 ans');
    expect(formatAgeRange(6, null)).toBe('Dès 6 ans');
    expect(formatAgeRange(null, 12)).toBe("Jusqu'à 12 ans");
    expect(formatAgeRange(0, null)).toBeNull();
    expect(formatAgeRange(null, null)).toBeNull();
  });
});

describe('accessibilityLabels', () => {
  it('ne garde que les accessibilités déclarées', () => {
    expect(
      accessibilityLabels({
        motorImpairment: true,
        hearingImpairment: false,
        visualImpairment: null,
        psychicImpairment: true,
        intellectualImpairment: null,
      }),
    ).toEqual(['Handicap moteur', 'Handicap psychique']);
    expect(accessibilityLabels(null)).toEqual([]);
  });
});

describe('linkAction', () => {
  it('ouvre un site, un téléphone ou un e-mail', () => {
    expect(linkAction({ type: 'WEBSITE', label: 'Billetterie', value: 'https://x.fr' })).toEqual({
      kind: 'web',
      label: 'Billetterie',
      url: 'https://x.fr',
    });
    expect(linkAction({ type: 'PHONE', label: null, value: '04 91 00 00 00' })?.url).toBe('tel:0491000000');
    expect(linkAction({ type: 'EMAIL', label: null, value: 'a@b.fr' })?.url).toBe('mailto:a@b.fr');
  });

  it('refuse les valeurs non sûres', () => {
    expect(linkAction({ type: 'WEBSITE', label: null, value: 'javascript:alert(1)' })).toBeNull();
    expect(linkAction({ type: 'EMAIL', label: null, value: 'pas un mail' })).toBeNull();
  });
});

describe('buildCalendarUrl', () => {
  it('utilise la prochaine occurrence en UTC', () => {
    const url = buildCalendarUrl({
      title: 'Jazz',
      description: null,
      location: 'Aubagne',
      occurrenceStartAt: '2026-10-20T18:00:00Z',
      occurrenceEndAt: null,
      startDate: null,
    })!;
    const params = new URL(url).searchParams;
    expect(params.get('dates')).toBe('20261020T180000Z/20261020T200000Z');
    expect(params.get('location')).toBe('Aubagne');
    expect(params.get('text')).toBe('Jazz');
  });

  it('une date seule devient une journée entière', () => {
    const url = buildCalendarUrl({ title: 'Marché', description: null, location: null, startDate: '2026-10-31' })!;
    expect(new URL(url).searchParams.get('dates')).toBe('20261031/20261101');
  });

  it('sans date : pas de lien', () => {
    expect(buildCalendarUrl({ title: 'x', description: null, location: null, startDate: null })).toBeNull();
  });
});

describe('shareMessage', () => {
  it('assemble les informations disponibles', () => {
    expect(shareMessage({ title: 'Jazz', date: '20 octobre 2026', place: 'Aubagne', url: null })).toBe(
      'Jazz\n20 octobre 2026 · Aubagne\nDécouvert sur LocalLife',
    );
  });
});
