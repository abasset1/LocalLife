import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import FollowBell from "./FollowBell";
import FollowedItemsCard from "./FollowedItemsCard";
import { resetFollowsForTests } from "./followStore";

// Session simulée en mémoire (le JWT vit désormais dans un cookie HttpOnly, plus dans localStorage).
const mockSession = vi.hoisted(() => ({ current: null as { userId: number; email: string; role: string } | null }));
vi.mock("../../auth/authStorage", () => ({
    getPayload: () => mockSession.current,
    isAuthenticated: () => mockSession.current !== null,
    subscribeSession: () => () => undefined,
    refreshSession: async () => mockSession.current,
    onAuthenticated: async () => mockSession.current,
    clearSession: async () => {
        mockSession.current = null;
    },
    clearSessionLocal: () => {
        mockSession.current = null;
    },
    readCsrfToken: () => null,
}));

let followResponse: { status: number; body: unknown };
let requests: { url: string; method: string }[];

beforeEach(() => {
    resetFollowsForTests();
    requests = [];
    followResponse = { status: 200, body: { following: true } };
    mockSession.current = { userId: 7, email: "a@b.c", role: "USER" };
    vi.stubGlobal("fetch", vi.fn((url: string, init?: RequestInit) => {
        const method = init?.method ?? "GET";
        requests.push({ url, method });
        if (url === "/api/v1/users/me/follows") {
            return Promise.resolve(new Response("[]", { status: 200 }));
        }
        if (method === "DELETE") {
            return Promise.resolve(new Response(JSON.stringify({ following: false }), { status: 200 }));
        }
        return Promise.resolve(new Response(JSON.stringify(followResponse.body), { status: followResponse.status }));
    }));
});

afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
});

function renderBell() {
    return render(
        <MemoryRouter>
            <FollowBell city="Lyon" id={42} title="Concert au port" type="activity" />
            <FollowedItemsCard canFollow />
        </MemoryRouter>,
    );
}

describe("FollowBell — suivre une activité ou un lieu", () => {
    it("suit puis ne suit plus, et la liste du profil suit le mouvement", async () => {
        const user = userEvent.setup();
        renderBell();

        await user.click(await screen.findByRole("button", { name: "Suivre cette activité" }));
        expect(requests).toContainEqual({ url: "/api/v1/activities/42/follow", method: "POST" });
        const following = screen.getByRole("button", { name: "Ne plus suivre cette activité" });
        expect(following).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByText("Concert au port")).toBeInTheDocument();

        await user.click(following);
        expect(requests).toContainEqual({ url: "/api/v1/activities/42/follow", method: "DELETE" });
        expect(screen.getByRole("button", { name: "Suivre cette activité" })).toHaveAttribute("aria-pressed", "false");
        expect(screen.queryByText("Concert au port")).toBeNull();
    });

    it("affiche le refus d'un compte gratuit avec un lien vers les offres", async () => {
        followResponse = {
            status: 403,
            body: { message: "Suivre une activité ou un lieu pour être prévenu de ses mises à jour est réservé aux offres Premium et Pro." },
        };
        const user = userEvent.setup();
        renderBell();

        await user.click(await screen.findByRole("button", { name: "Suivre cette activité" }));
        expect(await screen.findByRole("alert")).toHaveTextContent("réservé aux offres Premium et Pro");
        expect(screen.getByRole("link", { name: "Voir les offres" })).toHaveAttribute("href", "/profile");
        expect(screen.getByRole("button", { name: "Suivre cette activité" })).toHaveAttribute("aria-pressed", "false");
    });

    it("n'est pas affichée sans connexion", () => {
        mockSession.current = null;
        render(<MemoryRouter><FollowBell id={5} title="Musée" type="place" /></MemoryRouter>);
        expect(screen.queryByRole("button", { name: /Suivre/ })).toBeNull();
    });
});
