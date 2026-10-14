import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import PlaceActivities from "./PlaceActivities";

function activity(id: number, title: string, startDate: string) {
    return { id, title, startDate, endDate: null, latitude: 43.95, longitude: 4.8 };
}

function stub(body: unknown, status = 200) {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), { status }));
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
}

describe("PlaceActivities", () => {
    afterEach(() => vi.unstubAllGlobals());

    it("liste les activités à venir du lieu et ouvre l'aperçu au clic", async () => {
        const fetchMock = stub([
            activity(1, "Pièce de théâtre", "2026-10-20T20:00:00"),
            ...Array.from({ length: 6 }, (_, index) => activity(10 + index, `Concert ${index}`, "2026-11-01T20:00:00")),
        ]);
        const onSelect = vi.fn();
        render(<PlaceActivities onSelectActivity={onSelect} placeId={7} />);

        await userEvent.click(await screen.findByRole("button", { name: "Pièce de théâtre" }));

        expect(fetchMock).toHaveBeenCalledWith("/api/v1/activities/at-place/7", expect.anything());
        expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
        expect(screen.getAllByRole("listitem")).toHaveLength(5);
        expect(screen.getByText("et 2 autres")).toBeInTheDocument();
    });

    it("l'indique quand rien n'est prévu", async () => {
        stub([]);
        render(<PlaceActivities placeId={7} />);
        expect(await screen.findByText("Aucune activité à venir connue ici.")).toBeInTheDocument();
    });

    it("n'affiche rien si la requête échoue", async () => {
        const fetchMock = stub({}, 500);
        const { container } = render(<PlaceActivities placeId={7} />);
        await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
        expect(container).toBeEmptyDOMElement();
    });
});
