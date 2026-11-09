import { describe, expect, it } from "vitest";
import { activitiesInZone, weekendRange, zoneCountLabel } from "./activityUtils";
import type { Activity } from "./types";

const inside = { id: 1, latitude: 43.95, longitude: 4.8 } as Activity;
const outside = { id: 2, latitude: 44.2, longitude: 4.8 } as Activity;
const bounds = { swLatitude: 43.9, swLongitude: 4.7, neLatitude: 44.0, neLongitude: 4.9 };

describe("activités comptées (même règle que l'application)", () => {
    it("avec un rayon : tout le rayon, quel que soit le zoom de la carte", () => {
        expect(activitiesInZone([inside, outside], bounds, 20)).toEqual([inside, outside]);
    });

    it("sans rayon : seulement celles visibles sur la carte", () => {
        expect(activitiesInZone([inside, outside], bounds, null)).toEqual([inside]);
        expect(activitiesInZone([inside, outside], null, null)).toEqual([inside, outside]);
    });

    it("dit sur quelle zone porte le nombre", () => {
        expect(zoneCountLabel(37, 20)).toBe("37 activités dans un rayon de 20 km");
        expect(zoneCountLabel(1, null)).toBe("1 activité dans cette zone");
        expect(zoneCountLabel(1, null, true)).toBe("1 activité de nuit dans cette zone");
        expect(zoneCountLabel(4, 10, true)).toBe("4 activités de nuit dans un rayon de 10 km");
    });
});

describe("« Ce week-end » (même règle que l'application)", () => {
    it("le dimanche : seulement ce jour-là, pas le week-end suivant", () => {
        expect(weekendRange(new Date(2026, 10, 8))).toEqual({ date: "2026-11-08" });
    });

    it("le samedi : samedi et dimanche ; en semaine : le prochain week-end", () => {
        expect(weekendRange(new Date(2026, 10, 7))).toEqual({ date: "2026-11-07", dateTo: "2026-11-08" });
        expect(weekendRange(new Date(2026, 10, 9))).toEqual({ date: "2026-11-14", dateTo: "2026-11-15" });
    });
});
