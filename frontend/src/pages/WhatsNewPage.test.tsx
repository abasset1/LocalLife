import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import WhatsNewPage from "./WhatsNewPage";

// Session simulée en mémoire (le JWT vit désormais dans un cookie HttpOnly, plus dans localStorage).
const mockSession = vi.hoisted(() => ({ current: null as { userId: number; email: string; role: string } | null }));
vi.mock("../auth/authStorage", () => ({
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

function login() {
    mockSession.current = { userId: 7, email: "a@b.c", role: "USER" };
}

function overview(plan: string) {
    return { plan, current: null, pending: null, history: [] };
}

afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
    mockSession.current = null;
});

function renderPage() {
    return render(<MemoryRouter><WhatsNewPage /></MemoryRouter>);
}

describe("WhatsNewPage — nouveauté « Suivre »", () => {
    it("présente la fonctionnalité et invite un visiteur à créer un compte", () => {
        renderPage();
        expect(screen.getByRole("heading", { level: 1, name: "Suis tes activités et tes lieux préférés" })).toBeInTheDocument();
        expect(screen.getByText("🎁 Gratuit pour le moment")).toBeInTheDocument();
        expect(screen.getByRole("heading", { name: "🔎 Des alertes sur mesure, avec tes mots-clés" })).toBeInTheDocument();
        expect(screen.getByText(/une alerte à mots-clés est mise en pause/)).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Créer mon compte" })).toHaveAttribute("href", "/register");
    });

    it("passe un membre gratuit à Premium en un clic", async () => {
        login();
        const fetchMock = vi.fn((url: string, init?: RequestInit) => Promise.resolve(new Response(
            JSON.stringify(init?.method === "POST" ? overview("PREMIUM") : overview("FREE")), { status: 200 })));
        vi.stubGlobal("fetch", fetchMock);
        const user = userEvent.setup();
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Passer à Premium gratuitement" }));

        expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me/subscription/subscribe",
            expect.objectContaining({ method: "POST", body: JSON.stringify({ plan: "PREMIUM" }) }));
        expect(await screen.findByRole("heading", { name: "Tu en profites déjà" })).toBeInTheDocument();
        expect(screen.getByText(/C'est fait : tu es Premium/)).toBeInTheDocument();
    });

    it("indique à un membre Premium qu'il en profite déjà", async () => {
        login();
        vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify(overview("PREMIUM")), { status: 200 }))));
        renderPage();
        expect(await screen.findByRole("heading", { name: "Tu en profites déjà" })).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Passer à Premium gratuitement" })).toBeNull();
    });
});
