import { routeForNotification } from '../routing';

describe('routeForNotification', () => {
  it("ouvre la fiche quand l'alerte ne concerne qu'une activité", () => {
    expect(routeForNotification({ type: 'activity-alert', activityId: 7 })).toBe('/activite/7');
    expect(routeForNotification({ type: 'activity-alert', activityId: '12' })).toBe('/activite/12');
  });

  it("ouvre la fiche du food truck quand l'alerte ne concerne que lui", () => {
    expect(routeForNotification({ type: 'activity-alert', foodTruckId: 9 })).toBe('/foodtruck/9');
  });

  it('ouvre la carte quand il y a plusieurs activités', () => {
    expect(routeForNotification({ type: 'activity-alert' })).toBe('/');
  });

  it('ignore les autres notifications et les données invalides', () => {
    expect(routeForNotification({ type: 'autre', activityId: 7 })).toBeNull();
    expect(routeForNotification(null)).toBeNull();
    expect(routeForNotification('activity-alert')).toBeNull();
    expect(routeForNotification({ type: 'activity-alert', activityId: -3 })).toBe('/');
  });
});
