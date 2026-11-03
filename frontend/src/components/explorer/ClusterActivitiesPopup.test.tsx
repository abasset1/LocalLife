import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ClusterActivitiesPopup from "./ClusterActivitiesPopup";
import type { Activity } from "./types";

function activity(id: number): Activity {
    return {
        id,
        title: `Activité ${id}`,
        category: "",
        categoryCodes: [],
        latitude: 43.9,
        longitude: 4.8,
        startDate: `2026-11-0${id}T20:00:00`,
        status: "PUBLISHED",
        sourceName: "",
        address: "1 place de la Mairie",
        city: "Avignon",
        createdBy: null,
        createdByUsername: null,
        likedByCurrentUser: false,
        mainImageUrl: null,
    };
}

describe("ClusterActivitiesPopup", () => {
    it("fait défiler les activités du groupe, dans les deux sens et en boucle", async () => {
        const user = userEvent.setup();
        render(<ClusterActivitiesPopup activities={[activity(1), activity(2), activity(3)]} categories={[]} onOpenDetail={vi.fn()} />);

        expect(screen.getByText("Activité 1")).toBeInTheDocument();
        expect(screen.getByText("1 / 3")).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Activité suivante" }));
        expect(screen.getByText("Activité 2")).toBeInTheDocument();
        expect(screen.getByText("2 / 3")).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Activité précédente" }));
        await user.click(screen.getByRole("button", { name: "Activité précédente" }));
        expect(screen.getByText("Activité 3")).toBeInTheDocument();
        expect(screen.getByText("3 / 3")).toBeInTheDocument();
    });

    it("ouvre la fiche de l'activité affichée", async () => {
        const user = userEvent.setup();
        const onOpenDetail = vi.fn();
        const second = activity(2);
        render(<ClusterActivitiesPopup activities={[activity(1), second]} categories={[]} onOpenDetail={onOpenDetail} />);

        await user.click(screen.getByRole("button", { name: "Activité suivante" }));
        await user.click(screen.getByRole("button", { name: "Voir l'activité →" }));
        expect(onOpenDetail).toHaveBeenCalledWith(second);
    });

    it("sans flèches pour une seule activité", () => {
        render(<ClusterActivitiesPopup activities={[activity(1)]} categories={[]} onOpenDetail={vi.fn()} />);
        expect(screen.queryByRole("button", { name: "Activité suivante" })).toBeNull();
    });
});
