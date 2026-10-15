import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminEventLocationDuplicatesSection from "./AdminEventLocationDuplicatesSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function location(id: number, address: string, activityCount: number, linkedPlaceId: number | null = null) {
    return {
        id, name: null, address, postalCode: "84120", city: "Pertuis", coordinatePrecision: "UNKNOWN",
        activityCount, linkedPlaceId,
    };
}

function stubApi() {
    const urls: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
        urls.push(url);
        const certain = url.includes("level=CERTAIN");
        return new Response(JSON.stringify({
            clustersByLevel: { CERTAIN: 1, PROBABLE: 1 },
            locationsByLevel: { CERTAIN: 2, PROBABLE: 2 },
            extraRowsByLevel: { CERTAIN: 1, PROBABLE: 1 },
            activitiesByLevel: { CERTAIN: 7, PROBABLE: 3 },
            clusters: certain
                ? [{ level: "CERTAIN", maxDistanceMeters: 0, activityCount: 7, locations: [
                    location(11, "90 Boulevard Victor Hugo Théâtre de Pertuis", 5, 42),
                    location(60, "90 boulevard Victor Hugo Théâtre de Pertuis", 2),
                ] }]
                : [{ level: "PROBABLE", maxDistanceMeters: 70, activityCount: 3, locations: [
                    location(13, "Salle Georges Jouvin", 1),
                    location(22, "Espace Georges Jouvin Rue Henry Silvy", 2),
                ] }],
        }), { status: 200 });
    }));
    return urls;
}

describe("AdminEventLocationDuplicatesSection", () => {
    afterEach(() => vi.unstubAllGlobals());

    it("montre les doublons certains du Vaucluse, sans action d'écriture", async () => {
        const urls = stubApi();
        render(<AdminEventLocationDuplicatesSection />);

        expect(await screen.findByText(/n° 60/)).toBeInTheDocument();
        expect(urls[0]).toBe("/api/v1/admin/places/event-location-duplicates?level=CERTAIN&limit=200&department=84");
        expect(screen.getByRole("status")).toHaveTextContent(
            "1 lieu(x) en double, 1 ligne(s) en trop, 7 activité(s) concernée(s).");
        expect(screen.getByText(/relié au lieu permanent n° 42/)).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /fusionner|relier|supprimer/i })).not.toBeInTheDocument();
    });

    it("passe aux paires probables à relire", async () => {
        const urls = stubApi();
        render(<AdminEventLocationDuplicatesSection />);

        await userEvent.click(await screen.findByRole("button", { name: "Probables (1)" }));

        await waitFor(() => expect(screen.getByText(/n° 13/)).toBeInTheDocument());
        expect(urls.at(-1)).toContain("level=PROBABLE");
        expect(screen.getByRole("status")).toHaveTextContent("1 paire(s) à relire, 3 activité(s) concernée(s).");
    });
});
