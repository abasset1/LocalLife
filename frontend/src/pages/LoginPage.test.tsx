import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getToken } from "../auth/authStorage";
import LoginPage from "./LoginPage";

afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    delete window.google;
    document.querySelectorAll('script[src="https://accounts.google.com/gsi/client"]').forEach((node) => node.remove());
    localStorage.clear();
});

const CONSENT_MESSAGE =
    "Coche la case : tu dois avoir au moins 15 ans et accepter les conditions d'utilisation et la politique de confidentialité.";

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

    it("Google : sur 428, affiche la case puis relance l'appel avec acceptTerms: true", async () => {
        const user = userEvent.setup();
        vi.stubEnv("VITE_GOOGLE_CLIENT_ID", "client-test");
        // Script Google déjà présent et API simulée : le clic ne fait aucun appel réseau réel.
        const script = document.createElement("script");
        script.src = "https://accounts.google.com/gsi/client";
        document.head.appendChild(script);
        let googleCallback: ((response: { credential: string }) => void) | undefined;
        const prompt = vi.fn();
        window.google = {
            accounts: {
                id: {
                    initialize: vi.fn((config) => {
                        googleCallback = config.callback;
                    }),
                    renderButton: vi.fn(),
                    prompt,
                },
            },
        };
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(JSON.stringify({ message: CONSENT_MESSAGE }), { status: 428 }))
            .mockResolvedValueOnce(new Response(JSON.stringify({ token: "jwt-locallife" }), { status: 200 }));
        vi.stubGlobal("fetch", fetchMock);
        render(<MemoryRouter><LoginPage /></MemoryRouter>);

        expect(screen.getByText("Google recevra ton adresse e-mail.")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: /Continuer avec Google/ }));
        expect(prompt).toHaveBeenCalled();

        googleCallback?.({ credential: "id-token-google" });
        expect(await screen.findByText(CONSENT_MESSAGE)).toBeInTheDocument();
        expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ idToken: "id-token-google" });

        const retry = screen.getByRole("button", { name: "Créer mon compte avec Google" });
        expect(retry).toBeDisabled();
        await user.click(screen.getByRole("checkbox", { name: /J'ai au moins 15 ans/ }));
        await user.click(retry);

        await vi.waitFor(() => expect(getToken()).toBe("jwt-locallife"));
        expect(fetchMock).toHaveBeenCalledTimes(2);
        expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/auth/google");
        expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ idToken: "id-token-google", acceptTerms: true });
    });
});
