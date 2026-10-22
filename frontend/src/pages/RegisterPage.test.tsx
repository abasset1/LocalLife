import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import RegisterPage from "./RegisterPage";

afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
});

const CONSENT = /J'ai au moins 15 ans et j'accepte les conditions d'utilisation et la politique de confidentialité/;

describe("RegisterPage", () => {
    it("exige la case de consentement et envoie acceptTerms: true", async () => {
        const user = userEvent.setup();
        const fetchMock = vi.fn(async (input: string) => input.includes("/auth/register")
            ? new Response("{}", { status: 201 })
            : new Response(JSON.stringify({ available: true }), { status: 200 }));
        vi.stubGlobal("fetch", fetchMock);
        render(<MemoryRouter><RegisterPage /></MemoryRouter>);

        await user.type(screen.getByLabelText("Nom d'utilisateur"), "alex");
        await user.type(screen.getByLabelText("Email"), "alex@example.com");
        await user.type(screen.getByLabelText("Mot de passe"), "motdepasse");

        const consent = screen.getByRole("checkbox", { name: CONSENT });
        const submit = screen.getByRole("button", { name: "Créer mon compte" });
        expect(consent).not.toBeChecked();
        expect(submit).toBeDisabled();
        expect(screen.getByRole("link", { name: "conditions d'utilisation" })).toHaveAttribute("href", "/conditions");
        expect(screen.getByRole("link", { name: "politique de confidentialité" }))
            .toHaveAttribute("href", "/confidentialite");

        await user.click(consent);
        expect(submit).toBeEnabled();
        await user.click(submit);

        expect(await screen.findByText(/Compte créé/)).toBeInTheDocument();
        const registerCall = fetchMock.mock.calls.find(([input]) => input === "/api/v1/auth/register");
        const init = registerCall?.[1 as never] as unknown as RequestInit;
        expect(JSON.parse(init.body as string)).toEqual({
            username: "alex", email: "alex@example.com", password: "motdepasse", acceptTerms: true,
        });
    });

    it("n'active le bouton Google qu'une fois la case cochée, sans charger le script Google", async () => {
        const user = userEvent.setup();
        vi.stubEnv("VITE_GOOGLE_CLIENT_ID", "client-test");
        vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ available: true }))));
        render(<MemoryRouter><RegisterPage /></MemoryRouter>);

        const google = screen.getByRole("button", { name: /Continuer avec Google/ });
        expect(google).toBeDisabled();
        expect(screen.getByText("Google recevra ton adresse e-mail.")).toBeInTheDocument();
        expect(document.querySelector('script[src="https://accounts.google.com/gsi/client"]')).toBeNull();

        await user.click(screen.getByRole("checkbox", { name: CONSENT }));
        expect(google).toBeEnabled();
    });
});
