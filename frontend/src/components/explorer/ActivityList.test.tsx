import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ActivityList from "./ActivityList";
import type { Activity, Category } from "./types";

const CATEGORIES: Category[] = [{ code: "CULTURE", label: "Culture", description: null, active: true }];

function activity(id: number, overrides: Partial<Activity> = {}): Activity {
    return {
        id,
        title: `Activité ${id}`,
        category: "",
        categoryCodes: ["CULTURE"],
        latitude: 45.76,
        longitude: 4.83,
        startDate: "2026-11-02T20:00:00",
        status: "PUBLISHED",
        sourceName: "",
        address: "1 rue de la République",
        city: "Lyon",
        createdBy: null,
        createdByUsername: null,
        likedByCurrentUser: false,
        mainImageUrl: null,
        ...overrides,
    };
}

describe("ActivityList — résultats de recherche (ordered)", () => {
    it("garde l'ordre reçu, affiche lieu, distance, date et catégories", () => {
        render(
            <ActivityList
                activities={[
                    activity(1, { city: "Paris", distanceMeters: 1234 }),
                    activity(2, { city: "Lyon", distanceMeters: 850 }),
                ]}
                categories={CATEGORIES}
                error={null}
                isLoading={false}
                label="Résultats de la recherche"
                onSelectActivity={() => undefined}
                ordered
            />,
        );
        const items = within(screen.getByRole("list", { name: "Résultats de la recherche" })).getAllByRole("listitem");
        // Pas de regroupement par ville : Paris reste avant Lyon (ordre de pertinence).
        expect(items[0]).toHaveTextContent("Activité 1");
        expect(items[0]).toHaveTextContent("1 rue de la République, Paris · à 1,2 km");
        expect(items[0]).toHaveTextContent("02/11/2026 · 20:00 · Culture");
        expect(items[1]).toHaveTextContent("à 850 m");
        expect(screen.queryByRole("heading")).toBeNull();
    });

    it("résultat sans lieu, sans distance et sans date", () => {
        render(
            <ActivityList
                activities={[activity(3, {
                    address: null, city: null, latitude: Number.NaN, longitude: Number.NaN, startDate: "",
                    distanceMeters: null,
                })]}
                categories={CATEGORIES}
                error={null}
                isLoading={false}
                onSelectActivity={() => undefined}
                ordered
            />,
        );
        const item = screen.getByRole("listitem");
        expect(item).toHaveTextContent("Lieu non précisé");
        expect(item).toHaveTextContent("Date non précisée");
        expect(item).not.toHaveTextContent("NaN");
        expect(item).not.toHaveTextContent(" à ");
    });

    it("chaque résultat est un bouton activable au clavier", async () => {
        const onSelect = vi.fn();
        const user = userEvent.setup();
        render(
            <ActivityList activities={[activity(4)]} categories={CATEGORIES} error={null} isLoading={false}
                onSelectActivity={onSelect} ordered />,
        );
        screen.getByRole("button", { name: /Activité 4/ }).focus();
        await user.keyboard("{Enter}");
        expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 4 }));
    });
});

describe("ActivityList — liste de la carte (non-régression)", () => {
    it("regroupe par ville, sans distance", () => {
        render(
            <ActivityList
                activities={[activity(1, { city: "Paris", distanceMeters: 1234 }), activity(2, { city: "Lyon" })]}
                categories={CATEGORIES}
                error={null}
                isLoading={false}
                onSelectActivity={() => undefined}
            />,
        );
        expect(screen.getAllByRole("heading").map((heading) => heading.textContent)).toEqual(["Lyon", "Paris"]);
        expect(screen.getByText("Activité 1").closest("button")).not.toHaveTextContent("km");
    });

    it("états chargement, erreur et vide", () => {
        const { rerender } = render(
            <ActivityList activities={[]} categories={[]} error={null} isLoading onSelectActivity={() => undefined} />,
        );
        expect(screen.getByText("Chargement des activités…")).toBeInTheDocument();
        rerender(<ActivityList activities={[]} categories={[]} error="Oups" isLoading={false} onSelectActivity={() => undefined} />);
        expect(screen.getByRole("alert")).toHaveTextContent("Oups");
        rerender(<ActivityList activities={[]} categories={[]} emptyMessage="Rien ici." error={null} isLoading={false}
            onSelectActivity={() => undefined} />);
        expect(screen.getByText("Rien ici.")).toBeInTheDocument();
    });
});
