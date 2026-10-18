import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import PlaceFoodTrucks from "../places/PlaceFoodTrucks";
import type { FoodTruck } from "../explorer/types";
import FoodTruckList from "./FoodTruckList";

const TACOS: FoodTruck = {
    id: 1, name: "Tacos Loco", category: "Tacos", latitude: 43.9, longitude: 4.8, presenceId: 10,
    spotName: "Place Pie", spotKind: "MARKET", startTime: "08:00:00", endTime: "13:00:00", placeId: 42,
};

afterEach(() => vi.unstubAllGlobals());

describe("FoodTruckList", () => {
    it("liste une ligne par présence, avec l'emplacement et les horaires", () => {
        render(<FoodTruckList foodTrucks={[TACOS, { ...TACOS, presenceId: 11, spotName: "Parking", spotKind: "PRIVATE",
            startTime: "18:00:00", endTime: "22:00:00" }]} />);
        expect(screen.getAllByText("🚚 Tacos Loco")).toHaveLength(2);
        expect(screen.getByText("Marché · Place Pie · 08h00 – 13h00")).toBeInTheDocument();
        expect(screen.getByText("Espace privé · Parking · 18h00 – 22h00")).toBeInTheDocument();
    });

    it("annonce l'absence de food truck dans la zone", () => {
        render(<FoodTruckList foodTrucks={[]} />);
        expect(screen.getByText("Aucun food truck présent aujourd'hui dans cette zone.")).toBeInTheDocument();
    });
});

describe("PlaceFoodTrucks", () => {
    it("montre les food trucks présents aujourd'hui sur la fiche d'un lieu", async () => {
        const fetchMock = vi.fn(async () => new Response(JSON.stringify([TACOS]), { status: 200 }));
        vi.stubGlobal("fetch", fetchMock);
        render(<PlaceFoodTrucks placeId={42} />);
        expect(await screen.findByText("Food trucks présents aujourd'hui")).toBeInTheDocument();
        expect(screen.getByText("🚚 Tacos Loco · Tacos · 08h00 – 13h00")).toBeInTheDocument();
        expect(fetchMock).toHaveBeenCalledWith("/api/v1/places/42/foodtrucks", expect.anything());
    });
});
