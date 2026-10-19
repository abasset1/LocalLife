import { describeSchedule, describeToday, formatDays, formatHours } from '../presence';

describe('presence', () => {
  const market = {
    spotName: 'Place Pie', spotKind: 'MARKET' as const, daysOfWeek: 'TU,SA', startTime: '08:00:00',
    endTime: '13:00:00',
  };

  it('décrit la présence du jour', () => {
    expect(describeToday(market)).toBe('Marché · Place Pie · 08h00 – 13h00');
  });

  it('décrit une ligne du planning', () => {
    expect(describeSchedule(market)).toBe('mar., sam. · 08h00 – 13h00 · Marché · Place Pie');
  });

  it('gère les horaires et les jours non précisés', () => {
    expect(formatHours({ startTime: null, endTime: null })).toBeNull();
    expect(formatDays(null)).toBe('Tous les jours');
    expect(describeToday({ ...market, spotName: null, startTime: null, endTime: null })).toBe('Marché');
  });
});
