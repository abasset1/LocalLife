import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import PlaceList from "./PlaceList";
import type { Place } from "./types";

function place(id: number, name: string, placeType: string, city: string | null, extra: Partial<Place> = {}): Place {
    return {
        id, name, placeType, city, address: "1 rue du Lieu", postalCode: "84000", latitude: 43.95, longitude: 4.8,
        description: null, website: null, coordinatePrecision: "UNKNOWN", ...extra,
    };
}

const PLACES = [
    place(1, "Palais des Papes", "Palace", "Avignon"),
    place(2, "Musée Calvet", "Museum", "Avignon"),
    place(3, "Musée de la Lavande", "Museum", "Coustellet", { coordinatePrecision: "APPROXIMATE" }),
];

describe("PlaceList", () => {
    afterEach(() => vi.unstubAllGlobals());

    it("groupe les lieux par commune, trie les noms et filtre par type", async () => {
        render(<PlaceList onSelectActivity={vi.fn()} places={PLACES} />);

        const headings = screen.getAllByRole("heading").map((heading) => heading.textContent);
        expect(headings).toEqual(["Avignon", "Coustellet"]);
        const avignon = screen.getByRole("heading", { name: "Avignon" }).closest("section") as HTMLElement;
        expect(within(avignon).getAllByRole("button").map((button) => button.textContent)).toEqual([
            "🏛 Musée CalvetMusée", "🏛 Palais des PapesPalais",
        ]);
        expect(screen.getByText("Musée · Emplacement approximatif")).toBeInTheDocument();

        await userEvent.selectOptions(screen.getByLabelText("Type de lieu"), "Palace");
        expect(screen.queryByText("🏛 Musée Calvet")).not.toBeInTheDocument();
        expect(screen.getByText("🏛 Palais des Papes")).toBeInTheDocument();
    });

    it("déplie un lieu avec ses activités à venir", async () => {
        vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([
            { id: 9, title: "Visite nocturne", startDate: "2026-10-18T20:30:00" },
        ]))));
        const onSelect = vi.fn();
        render(<PlaceList onSelectActivity={onSelect} places={PLACES} />);

        await userEvent.click(screen.getByRole("button", { name: /Palais des Papes/ }));
        await userEvent.click(await screen.findByRole("button", { name: "Visite nocturne" }));

        expect(screen.getByRole("button", { name: /Palais des Papes/ })).toHaveAttribute("aria-expanded", "true");
        expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }));
    });

    it("l'indique quand la zone n'a aucun lieu", () => {
        render(<PlaceList onSelectActivity={vi.fn()} places={[]} />);
        expect(screen.getByText("Aucun lieu publié dans cette zone.")).toBeInTheDocument();
    });

    it("ne propose « Demander la gestion de ce lieu » qu'avec un accès professionnel", async () => {
        vi.stubGlobal("fetch", vi.fn(async () => new Response("[]")));
        const { unmount } = render(<MemoryRouter><PlaceList onSelectActivity={vi.fn()} places={PLACES} /></MemoryRouter>);
        await userEvent.click(screen.getByRole("button", { name: /Palais des Papes/ }));
        expect(screen.queryByRole("link", { name: "Demander la gestion de ce lieu" })).not.toBeInTheDocument();
        unmount();

        render(<MemoryRouter><PlaceList hasProAccess onSelectActivity={vi.fn()} places={PLACES} /></MemoryRouter>);
        await userEvent.click(screen.getByRole("button", { name: /Palais des Papes/ }));
        expect(screen.getByRole("link", { name: "Demander la gestion de ce lieu" })).toHaveAttribute("href", "/pro?claim=1");
    });
});
