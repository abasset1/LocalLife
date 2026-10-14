import { describe, expect, it } from "vitest";
import { PLACE_TYPE_LABELS, PLACE_TYPE_OPTIONS, placeTypeLabel } from "./placeTypes";

describe("placeTypes", () => {
    it("couvre les 46 types de la liste blanche (PlaceTypeWhitelist côté backend)", () => {
        expect(Object.keys(PLACE_TYPE_LABELS)).toHaveLength(46);
        expect(PLACE_TYPE_OPTIONS).toHaveLength(46);
    });

    it("traduit un type, et garde le nom inconnu tel quel", () => {
        expect(placeTypeLabel("Church")).toBe("Église");
        expect(placeTypeLabel("Unknown")).toBe("Unknown");
        expect(placeTypeLabel(null)).toBe("Lieu");
    });
});
