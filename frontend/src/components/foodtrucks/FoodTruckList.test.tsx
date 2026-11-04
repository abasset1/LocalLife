import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import PlaceFoodTrucks from "../places/PlaceFoodTrucks";
import type { FoodTruck } from "../explorer/types";
import FoodTruckList from "./FoodTruckList";
import FoodTruckPopup from "./FoodTruckPopup";
import userEvent from "@testing-library/user-event";

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

    it("montre photo, type de cuisine, description et pastille, et ouvre la fiche au clic", async () => {
        const onSelect = vi.fn();
        const pizza: FoodTruck = { ...TACOS, name: "Pizza Nomade", category: "Pizzas au feu de bois",
            cuisineType: "PIZZA", markerEmoji: null, description: "Pâte maison", photoUrl: "/api/v1/foodtrucks/1/photo?v=1" };
        const { container } = render(<FoodTruckList foodTrucks={[pizza]} onSelectFoodTruck={onSelect} />);
        expect(screen.getByText("🍕 Pizza Nomade")).toBeInTheDocument();
        expect(screen.getByText("Pizzas · Pizzas au feu de bois")).toBeInTheDocument();
        expect(screen.getByText("Pâte maison")).toBeInTheDocument();
        expect(container.querySelector("img")).toHaveAttribute("src", "/api/v1/foodtrucks/1/photo?v=1");
        await userEvent.click(screen.getByRole("button", { name: /Pizza Nomade/ }));
        expect(onSelect).toHaveBeenCalledWith(pizza);
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

describe("FoodTruckPopup", () => {
    it("présente un food truck comme la popup d'une activité et fait défiler ceux du même emplacement", async () => {
        const user = userEvent.setup();
        const onOpen = vi.fn();
        const crepes: FoodTruck = { ...TACOS, id: 2, presenceId: 12, name: "Crêpes du Rhône", markerEmoji: "🥞" };
        render(<FoodTruckPopup foodTrucks={[TACOS, crepes]} onOpenDetail={onOpen} />);
        expect(screen.getByText("Aujourd'hui · 08h00 – 13h00")).toBeInTheDocument();
        expect(screen.getByText("Tacos Loco")).toBeInTheDocument();
        expect(screen.getByText("1 / 2")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Food truck suivant" }));
        expect(screen.getByText("Crêpes du Rhône")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Voir le food truck →" }));
        expect(onOpen).toHaveBeenCalledWith(crepes);
    });

    it("affiche l'adresse exacte et les liens Instagram et Facebook", () => {
        const truck: FoodTruck = { ...TACOS, spotAddress: "12 rue des Lilas, 35000 Rennes",
            instagramUrl: "https://www.instagram.com/tacosloco", facebookUrl: "https://www.facebook.com/tacosloco" };
        render(<FoodTruckPopup foodTrucks={[truck]} onOpenDetail={vi.fn()} />);
        expect(screen.getByText("🏠 12 rue des Lilas, 35000 Rennes")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Instagram" })).toHaveAttribute("href", "https://www.instagram.com/tacosloco");
        expect(screen.getByRole("link", { name: "Facebook" })).toHaveAttribute("href", "https://www.facebook.com/tacosloco");
    });

    it("n'affiche pas un lien qui ne mène pas au réseau annoncé", () => {
        render(<FoodTruckPopup foodTrucks={[{ ...TACOS, instagramUrl: "javascript:alert(1)",
            facebookUrl: "https://evil.example/facebook.com" }]} onOpenDetail={vi.fn()} />);
        expect(screen.queryByRole("link", { name: "Instagram" })).not.toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "Facebook" })).not.toBeInTheDocument();
    });
});

describe("FoodTruckList · adresse", () => {
    it("montre l'adresse exacte sous l'emplacement, sauf si elle sert déjà de nom", () => {
        render(<FoodTruckList foodTrucks={[{ ...TACOS, spotAddress: "Place Pie, 84000 Avignon" },
            { ...TACOS, presenceId: 11, spotName: "3 rue Neuve", spotAddress: "3 rue Neuve" }]} />);
        expect(screen.getByText("🏠 Place Pie, 84000 Avignon")).toBeInTheDocument();
        expect(screen.queryByText("🏠 3 rue Neuve")).not.toBeInTheDocument();
    });
});
