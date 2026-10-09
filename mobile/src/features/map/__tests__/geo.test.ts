import {
  boundsContain,
  isSearchable,
  pointInBounds,
  regionForRadius,
  regionForZoom,
  regionToBounds,
  zoomForRegion,
} from '../geo';

const marseille = { latitude: 43.2965, longitude: 5.3698, latitudeDelta: 0.12, longitudeDelta: 0.08 };

describe('regionToBounds', () => {
  it('convertit la région visible en zone sw < ne', () => {
    expect(regionToBounds(marseille)).toEqual({
      swLatitude: 43.2365,
      swLongitude: 5.3298,
      neLatitude: 43.3565,
      neLongitude: 5.4098,
    });
  });

  it('borne les valeurs aux limites du contrat', () => {
    const bounds = regionToBounds({ latitude: 0, longitude: 0, latitudeDelta: 400, longitudeDelta: 800 });
    expect(bounds).toEqual({ swLatitude: -90, swLongitude: -180, neLatitude: 90, neLongitude: 180 });
  });

  it('refuse une zone vide ou non numérique', () => {
    expect(regionToBounds({ ...marseille, latitudeDelta: 0 })).toBeNull();
    expect(regionToBounds({ ...marseille, longitude: NaN })).toBeNull();
  });
});

describe('boundsContain / pointInBounds', () => {
  const outer = regionToBounds(marseille)!;
  const inner = regionToBounds({ ...marseille, latitudeDelta: 0.02, longitudeDelta: 0.02 })!;

  it('détecte une zone entièrement couverte', () => {
    expect(boundsContain(outer, inner)).toBe(true);
    expect(boundsContain(inner, outer)).toBe(false);
  });

  it('situe un point par rapport à la zone', () => {
    expect(pointInBounds(outer, 43.3, 5.37)).toBe(true);
    expect(pointInBounds(outer, 48.85, 2.35)).toBe(false);
  });
});

describe('isSearchable', () => {
  it("accepte une zone à l'échelle d'une ville ou d'un département", () => {
    expect(isSearchable(regionToBounds(marseille)!)).toBe(true);
    expect(isSearchable(regionToBounds({ ...marseille, latitudeDelta: 1.8, longitudeDelta: 1.5 })!)).toBe(true);
  });

  it('refuse une zone trop étendue dans une dimension', () => {
    expect(isSearchable(regionToBounds({ ...marseille, latitudeDelta: 2.5 })!)).toBe(false);
    expect(isSearchable(regionToBounds({ ...marseille, longitudeDelta: 10 })!)).toBe(false);
  });
});

describe('zoom', () => {
  it('déduit le zoom de la largeur visible', () => {
    expect(zoomForRegion({ ...marseille, longitudeDelta: 360 })).toBe(0);
    expect(zoomForRegion({ ...marseille, longitudeDelta: 360 / 2 ** 14 })).toBe(14);
    expect(zoomForRegion({ ...marseille, longitudeDelta: 0 })).toBe(20);
  });

  it('construit une région au zoom voulu en gardant le ratio', () => {
    const region = regionForZoom(43.3, 5.37, 14, marseille);
    expect(region.longitudeDelta).toBeCloseTo(360 / 2 ** 14);
    expect(region.latitudeDelta / region.longitudeDelta).toBeCloseTo(1.5);
    expect(zoomForRegion(region)).toBe(14);
  });
});

describe('regionForRadius', () => {
  it('couvre le diamètre demandé autour de la ville', () => {
    const region = regionForRadius(45.76, 4.83, 10);
    expect(region.latitude).toBe(45.76);
    expect(region.latitudeDelta).toBeCloseTo(20 / 111.32, 5);
    // Un degré de longitude est plus court qu'un degré de latitude hors de l'équateur.
    expect(region.longitudeDelta).toBeGreaterThan(region.latitudeDelta);
  });

  it('reste cherchable au rayon maximal (50 km)', () => {
    const bounds = regionToBounds(regionForRadius(43.2965, 5.3698, 50));
    expect(bounds && isSearchable(bounds)).toBe(true);
  });
});
