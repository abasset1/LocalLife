import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { followNewsPopupKey, premiumOfferOptOutKey } from "./newsStorage";
import PremiumOfferPopup from "./PremiumOfferPopup";

const NOW = Date.parse("2026-11-03T12:00:00Z");
const DAY_S = 24 * 60 * 60;
const loggedInDaysAgo = (days: number) => NOW / 1000 - days * DAY_S;

beforeEach(() => {
    // La nouveauté « Suivre » est déjà passée : l'offre peut s'afficher.
    window.localStorage.setItem(followNewsPopupKey(7), "1");
});

afterEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.unstubAllGlobals();
});

function renderPopup(props: Partial<{ userId: number | null; canFollow: boolean | null; loggedInAt?: number; blocked: boolean }> = {}) {
    return render(
        <MemoryRouter>
            <PremiumOfferPopup
                blocked={props.blocked ?? false}
                canFollow={props.canFollow === undefined ? false : props.canFollow}
                loggedInAt={"loggedInAt" in props ? props.loggedInAt : loggedInDaysAgo(1)}
                now={() => NOW}
                userId={props.userId === undefined ? 7 : props.userId}
            />
        </MemoryRouter>,
    );
}

describe("PremiumOfferPopup", () => {
    it("propose Premium à un compte gratuit, avec « Ne plus me proposer »", () => {
        renderPopup();
        const dialog = screen.getByRole("dialog", { name: "Passe à Premium" });
        expect(dialog).toHaveTextContent("Gratuit pour le moment");
        expect(dialog).toHaveTextContent("alertes par mots-clés");
        expect(screen.getByRole("button", { name: "Ne plus me proposer" })).toBeInTheDocument();
    });

    it("« Plus tard » la ferme pour la visite, elle revient à la visite suivante", async () => {
        const user = userEvent.setup();
        const first = renderPopup();
        await user.click(screen.getByRole("button", { name: "Plus tard" }));
        expect(screen.queryByRole("dialog")).toBeNull();

        // Même visite (onglet) : plus d'affichage.
        first.unmount();
        const sameVisit = renderPopup();
        expect(screen.queryByRole("dialog")).toBeNull();
        sameVisit.unmount();

        // Visite suivante (nouvel onglet) : elle revient.
        window.sessionStorage.clear();
        renderPopup();
        expect(screen.getByRole("dialog", { name: "Passe à Premium" })).toBeInTheDocument();
    });

    it("« Ne plus me proposer » l'arrête définitivement pour ce compte", async () => {
        const user = userEvent.setup();
        const first = renderPopup();
        await user.click(screen.getByRole("button", { name: "Ne plus me proposer" }));
        expect(screen.queryByRole("dialog")).toBeNull();
        expect(window.localStorage.getItem(premiumOfferOptOutKey(7))).toBe("1");

        first.unmount();
        window.sessionStorage.clear();
        renderPopup();
        expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("ne s'affiche que pendant 7 jours après la connexion", () => {
        const sixDays = renderPopup({ loggedInAt: loggedInDaysAgo(6.9) });
        expect(screen.getByRole("dialog")).toBeInTheDocument();
        sixDays.unmount();

        const eightDays = renderPopup({ loggedInAt: loggedInDaysAgo(8) });
        expect(screen.queryByRole("dialog")).toBeNull();
        eightDays.unmount();

        renderPopup({ loggedInAt: undefined });
        expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("ni aux visiteurs, ni aux comptes Premium ou Pro, ni avant la nouveauté « Suivre », ni par-dessus une autre fenêtre", () => {
        const visitor = renderPopup({ userId: null });
        expect(screen.queryByRole("dialog")).toBeNull();
        visitor.unmount();

        const premium = renderPopup({ canFollow: true });
        expect(screen.queryByRole("dialog")).toBeNull();
        premium.unmount();

        const blocked = renderPopup({ blocked: true });
        expect(screen.queryByRole("dialog")).toBeNull();
        blocked.unmount();

        window.localStorage.removeItem(followNewsPopupKey(7));
        renderPopup();
        expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("« Passer à Premium » active Premium et remercie", async () => {
        const user = userEvent.setup();
        const fetchMock = vi.fn(async () => new Response(JSON.stringify({ plan: "PREMIUM", current: null, pending: null, history: [] }),
            { headers: { "Content-Type": "application/json" } }));
        vi.stubGlobal("fetch", fetchMock);
        renderPopup();

        await user.click(screen.getByRole("button", { name: "Passer à Premium" }));

        expect(await screen.findByRole("dialog", { name: "Bienvenue dans Premium !" })).toBeInTheDocument();
        // L'écriture (apiFetch peut d'abord redemander le jeton anti-CSRF par une lecture).
        const [url, init] = (fetchMock.mock.calls as unknown as [string, RequestInit][])
            .find(([, options]) => options?.method === "POST")!;
        expect(url).toBe("/api/v1/users/me/subscription/subscribe");
        expect(init.method).toBe("POST");
        expect(JSON.parse(String(init.body))).toEqual({ plan: "PREMIUM" });
    });
});
