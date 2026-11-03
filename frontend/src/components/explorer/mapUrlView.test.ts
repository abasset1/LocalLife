import { afterEach, describe, expect, it } from "vitest";
import { readMapUrlView, writeMapUrlView } from "./mapUrlView";

describe("readMapUrlView", () => {
    it("lit la position de la carte dans l'adresse", () => {
        expect(readMapUrlView("?lat=43.88169&lng=4.85493&zoom=7.5")).toEqual({
            latitude: 43.88169,
            longitude: 4.85493,
            zoom: 7.5,
        });
    });

    it("ignore une position incomplète ou invalide", () => {
        expect(readMapUrlView("")).toBeNull();
        expect(readMapUrlView("?lat=43.8&lng=4.8")).toBeNull();
        expect(readMapUrlView("?lat=abc&lng=4.8&zoom=9")).toBeNull();
        expect(readMapUrlView("?lat=95&lng=4.8&zoom=9")).toBeNull();
        expect(readMapUrlView("?lat=43.8&lng=200&zoom=9")).toBeNull();
        expect(readMapUrlView("?lat=43.8&lng=4.8&zoom=40")).toBeNull();
        expect(readMapUrlView("?lat=&lng=4.8&zoom=9")).toBeNull();
    });
});

describe("writeMapUrlView", () => {
    afterEach(() => window.history.replaceState(null, "", "/"));

    it("écrit la position sans toucher aux autres paramètres ni ajouter d'entrée d'historique", () => {
        window.history.replaceState({ key: "router" }, "", "/?edit=12");
        const historyLength = window.history.length;

        writeMapUrlView({ latitude: 43.881694321, longitude: 4.854931, zoom: 9.256 });

        const params = new URLSearchParams(window.location.search);
        expect(params.get("edit")).toBe("12");
        expect(params.get("lat")).toBe("43.88169");
        expect(params.get("lng")).toBe("4.85493");
        expect(params.get("zoom")).toBe("9.26");
        expect(window.history.length).toBe(historyLength);
        expect(window.history.state).toEqual({ key: "router" });
    });
});
