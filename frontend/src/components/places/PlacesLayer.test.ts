import { describe, expect, it } from "vitest";
import { placesWithinBoundsUrl } from "./PlacesLayer";

describe("placesWithinBoundsUrl", () => {
    it("construit la requête des lieux publiés de la zone", () => {
        const url = new URL(placesWithinBoundsUrl({ south: 43.9, west: 4.7, north: 44, east: 4.9 }), "http://localhost");
        expect(url.pathname).toBe("/api/v1/places/within-bounds");
        expect(url.searchParams.get("minLat")).toBe("43.9");
        expect(url.searchParams.get("maxLat")).toBe("44");
        expect(url.searchParams.get("minLng")).toBe("4.7");
        expect(url.searchParams.get("maxLng")).toBe("4.9");
    });

    it("ramène les longitudes d'une carte dézoomée dans les bornes valides", () => {
        const url = new URL(placesWithinBoundsUrl({ south: -10, west: -400, north: 10, east: 400 }), "http://localhost");
        expect(url.searchParams.get("minLng")).toBe("-180");
        expect(url.searchParams.get("maxLng")).toBe("180");
    });
});
