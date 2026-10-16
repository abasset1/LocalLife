import { getPeriod, setPeriod, subscribe } from '../periodStore';

describe('periodStore', () => {
  afterEach(() => setPeriod('all'));

  it('partage la période et prévient chaque écran abonné', () => {
    const map = jest.fn();
    const list = jest.fn();
    const unsubscribeMap = subscribe(map);
    subscribe(list);

    setPeriod('weekend');

    expect(getPeriod()).toBe('weekend');
    expect(map).toHaveBeenCalledTimes(1);
    expect(list).toHaveBeenCalledTimes(1);

    unsubscribeMap();
    setPeriod('today');
    expect(map).toHaveBeenCalledTimes(1);
    expect(list).toHaveBeenCalledTimes(2);
  });

  it("ne prévient personne si la période ne change pas", () => {
    const listener = jest.fn();
    subscribe(listener);
    setPeriod('all');
    expect(listener).not.toHaveBeenCalled();
  });
});
