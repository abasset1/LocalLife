import { describe, expect, it } from "vitest";
import { buildDirectionsUrl } from "./directions";

function destinationOf(url: string): string | null {
    return new URL(url).searchParams.get("destination");
}

describe("buildDirectionsUrl", () => {
    const base = { latitude: 43.876, longitude: 5.396, address: null, city: "Apt" };

    it("vise les coordonnées quand la précision est absente, inconnue ou exacte", () => {
        expect(destinationOf(buildDirectionsUrl(base))).toBe("43.876,5.396");
        expect(destinationOf(buildDirectionsUrl({ ...base, coordinatePrecision: "UNKNOWN" }))).toBe("43.876,5.396");
        expect(destinationOf(buildDirectionsUrl({ ...base, coordinatePrecision: "EXACT" }))).toBe("43.876,5.396");
    });

    it("vise la commune, pas le point, pour un emplacement approximatif", () => {
        expect(destinationOf(buildDirectionsUrl({ ...base, coordinatePrecision: "APPROXIMATE" }))).toBe("Apt");
    });

    it("garde le point approximatif s'il n'y a ni adresse ni commune", () => {
        expect(destinationOf(buildDirectionsUrl({ ...base, city: null, coordinatePrecision: "APPROXIMATE" })))
            .toBe("43.876,5.396");
    });

    it("se rabat sur l'adresse sans coordonnées", () => {
        expect(destinationOf(buildDirectionsUrl({ latitude: null, longitude: null, address: "Rue du Roi René", city: "Avignon" })))
            .toBe("Rue du Roi René, Avignon");
    });
});
