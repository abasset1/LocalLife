import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import LoginPage from "./LoginPage";

afterEach(() => vi.unstubAllGlobals());

describe("LoginPage", () => {
    it("affiche le message du serveur après trop de tentatives (429)", async () => {
        const user = userEvent.setup();
        vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({
            message: "Trop de tentatives de connexion : réessaie dans 15 minutes.",
        }), { status: 429 })));
        render(<MemoryRouter><LoginPage /></MemoryRouter>);

        await user.type(screen.getByLabelText("Email"), "admin@example.com");
        await user.type(screen.getByLabelText(/Mot de passe/), "faux");
        await user.click(screen.getByRole("button", { name: /Se connecter/ }));

        expect(await screen.findByText("Trop de tentatives de connexion : réessaie dans 15 minutes.")).toBeInTheDocument();
    });
});
