import { describe, expect, it } from "vitest";
import { formatActivityLocation, isApproximateLocation } from "./activityUtils";
import type { Activity } from "./types";

const base: Activity = {
    id: 1, title: "Fête", category: "", latitude: 43.91, longitude: 5.2, startDate: "2026-11-02T20:00:00",
    status: "PUBLISHED", sourceName: "", address: null, city: "Gordes", createdBy: null, createdByUsername: null,
    likedByCurrentUser: false, mainImageUrl: null,
};

describe("formatActivityLocation — précision (ADR-0011, phase A)", () => {
    it("ajoute la mention pour APPROXIMATE seulement", () => {
        expect(formatActivityLocation({ ...base, coordinatePrecision: "APPROXIMATE" }))
            .toBe("Gordes (emplacement approximatif)");
        expect(formatActivityLocation({ ...base, coordinatePrecision: "UNKNOWN" })).toBe("Gordes");
        expect(formatActivityLocation({ ...base, coordinatePrecision: "EXACT" })).toBe("Gordes");
        expect(formatActivityLocation(base)).toBe("Gordes");
    });

    it("reconnaît une position approximative", () => {
        expect(isApproximateLocation({ coordinatePrecision: "APPROXIMATE" })).toBe(true);
        expect(isApproximateLocation({ coordinatePrecision: null })).toBe(false);
        expect(isApproximateLocation({})).toBe(false);
    });
});
