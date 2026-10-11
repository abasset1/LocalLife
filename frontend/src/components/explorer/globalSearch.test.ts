import { describe, expect, it } from "vitest";
import { buildSearchUrl, formatDistance, hasCoordinates, toActivity } from "./globalSearch";
import type { GlobalSearchItem } from "./globalSearch";
import type { Activity } from "./types";

function params(url: string): URLSearchParams {
    expect(url.startsWith("/api/v1/activities/search?")).toBe(true);
    return new URL(url, "http://localhost").searchParams;
}

const ITEM: GlobalSearchItem = {
    id: 7,
    title: "Concert acoustique",
    description: null,
    category: "concert",
    categoryCodes: ["CULTURE"],
    city: "Lyon",
    address: "Place Bellecour",
    latitude: 45.7578,
    longitude: 4.832,
    startDate: "2026-11-02T20:00:00",
    endDate: null,
    relevance: "LOCATION_MATCH",
    distanceMeters: 1234,
};

describe("buildSearchUrl", () => {
    it("envoie le texte, la page, sans position ni filtre quand il n'y en a pas", () => {
        const search = params(
            buildSearchUrl({ query: "  concert lyon ", categoriesKey: "", date: "", dateTo: "", position: null, offset: 0 }),
        );
        expect(search.get("q")).toBe("concert lyon");
        expect(search.get("limit")).toBe("20");
        expect(search.get("offset")).toBe("0");
        expect(search.has("latitude")).toBe(false);
        expect(search.has("longitude")).toBe(false);
        expect(search.has("category")).toBe(false);
        expect(search.has("date")).toBe(false);
        // Ni rayon ni zone : la recherche n'est pas limitée à la carte.
        expect(search.has("radius")).toBe(false);
        expect(search.has("swLatitude")).toBe(false);
    });

    it("ajoute la position existante, les catégories, la période et la page", () => {
        const search = params(
            buildSearchUrl({
                query: "jazz",
                categoriesKey: "CULTURE,SPORT",
                date: "2026-11-01",
                dateTo: "2026-11-30",
                position: { latitude: 43.3, longitude: 5.37 },
                offset: 40,
            }),
        );
        expect(search.get("latitude")).toBe("43.3");
        expect(search.get("longitude")).toBe("5.37");
        expect(search.get("category")).toBe("CULTURE,SPORT");
        expect(search.get("date")).toBe("2026-11-01");
        expect(search.get("dateTo")).toBe("2026-11-30");
        expect(search.get("offset")).toBe("40");
    });

    it("n'envoie pas dateTo sans date", () => {
        const search = params(
            buildSearchUrl({ query: "jazz", categoriesKey: "", date: "", dateTo: "2026-11-30", position: null, offset: 0 }),
        );
        expect(search.has("date")).toBe(false);
        expect(search.has("dateTo")).toBe(false);
    });
});

describe("toActivity", () => {
    it("reprend le lieu retenu, la distance et l'état connu de la carte", () => {
        const known = { id: 7, sourceName: "Agenda", createdBy: 3, mainImageUrl: "/img.jpg" } as Activity;
        const activity = toActivity(ITEM, undefined, known);
        expect(activity).toMatchObject({
            id: 7,
            city: "Lyon",
            latitude: 45.7578,
            distanceMeters: 1234,
            sourceName: "Agenda",
            createdBy: 3,
            mainImageUrl: "/img.jpg",
            likedByCurrentUser: false,
        });
        expect(hasCoordinates(activity)).toBe(true);
    });

    it("garde un résultat sans lieu ni date, hors de la carte", () => {
        const activity = toActivity(
            { ...ITEM, city: null, address: null, latitude: null, longitude: null, startDate: null, distanceMeters: null },
            true,
        );
        expect(hasCoordinates(activity)).toBe(false);
        expect(activity.startDate).toBe("");
        expect(activity.distanceMeters).toBeNull();
        expect(activity.likedByCurrentUser).toBe(true);
        expect(activity.sourceName).toBe("");
    });
});

describe("formatDistance", () => {
    it("formate en mètres puis en kilomètres", () => {
        expect(formatDistance(850)).toBe("à 850 m");
        expect(formatDistance(1234)).toBe("à 1,2 km");
        expect(formatDistance(35_400)).toBe("à 35 km");
    });

    it("ne renvoie rien sans distance", () => {
        expect(formatDistance(null)).toBeNull();
        expect(formatDistance(undefined)).toBeNull();
    });
});
