import { afterEach, describe, expect, it } from "vitest";
import { activityFromLinkResponse, clearActivityLink, readActivityLinkId } from "./activityLink";

describe("activityLink", () => {
    afterEach(() => window.history.replaceState(null, "", "/"));

    it("lit l'identifiant de l'activité demandée, ignore une valeur invalide", () => {
        expect(readActivityLinkId("?activity=42&lat=43.9&lng=4.8&zoom=15")).toBe(42);
        expect(readActivityLinkId("")).toBeNull();
        expect(readActivityLinkId("?activity=")).toBeNull();
        expect(readActivityLinkId("?activity=abc")).toBeNull();
        expect(readActivityLinkId("?activity=-3")).toBeNull();
        expect(readActivityLinkId("?activity=0")).toBeNull();
    });

    it("construit l'activité depuis la fiche détaillée ou depuis le résumé", () => {
        const detail = activityFromLinkResponse({
            id: 7, title: "Concert", latitude: 43.9, longitude: 4.8, startDate: "2026-11-22T20:30:00",
            city: "Avignon", media: [{ url: "https://img/1.jpg" }], priceType: "PAID", schedules: [],
        });
        expect(detail).toMatchObject({
            id: 7, title: "Concert", startDate: "2026-11-22T20:30:00", city: "Avignon",
            mainImageUrl: "https://img/1.jpg", priceType: "PAID", likedByCurrentUser: false,
        });

        const summary = activityFromLinkResponse({
            id: 8, title: "Marché", date: "2026-11-23T08:00:00", mainImageUrl: null, latitude: 1, longitude: 2,
        });
        expect(summary).toMatchObject({ id: 8, startDate: "2026-11-23T08:00:00", mainImageUrl: null });

        expect(activityFromLinkResponse(null)).toBeNull();
        expect(activityFromLinkResponse({ id: "x" })).toBeNull();
    });

    it("retire le lien de l'adresse en gardant la position de la carte", () => {
        window.history.replaceState(null, "", "/?activity=7&lat=43.9&lng=4.8&zoom=15");

        clearActivityLink();

        expect(window.location.search).toBe("?lat=43.9&lng=4.8&zoom=15");
    });
});
