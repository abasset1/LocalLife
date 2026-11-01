import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import WhatsNewPage from "./WhatsNewPage";

function login() {
    window.localStorage.setItem("locallife.jwt", `x.${btoa(JSON.stringify({ userId: 7, email: "a@b.c", role: "USER" }))}.y`);
}

function overview(plan: string) {
    return { plan, current: null, pending: null, history: [] };
}

afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
});

function renderPage() {
    return render(<MemoryRouter><WhatsNewPage /></MemoryRouter>);
}

describe("WhatsNewPage — nouveauté « Suivre »", () => {
    it("présente la fonctionnalité et invite un visiteur à créer un compte", () => {
        renderPage();
        expect(screen.getByRole("heading", { level: 1, name: "Suis tes activités et tes lieux préférés" })).toBeInTheDocument();
        expect(screen.getByText("🎁 Gratuit pour le moment")).toBeInTheDocument();
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
