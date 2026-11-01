import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import OnboardingModal from "./OnboardingModal";

beforeEach(() => {
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string, init?: RequestInit) => {
            let body: unknown = {};
            if (url.startsWith("/api/v1/users/me/preferences/city")) {
                body = { city: "Lyon", latitude: 45.75, longitude: 4.83 };
            } else if (url.startsWith("/api/v1/users/me/preferences") && init?.method === "PUT") {
                body = {
                    defaultCity: "Lyon",
                    defaultLatitude: 45.75,
                    defaultLongitude: 4.83,
                    defaultRadiusKm: 10,
                    defaultCategoryCodes: ["CULTURE"],
                    configured: true,
                };
            }
            return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
        }),
    );
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe("OnboardingModal — lien vers le guide en fin d'accueil", () => {
    it("la dernière étape propose le guide, qui termine l'accueil et ouvre /guide", async () => {
        const user = userEvent.setup();
        const onFinish = vi.fn();
        render(
            <MemoryRouter initialEntries={["/"]}>
                <Routes>
                    <Route
                        element={(
                            <OnboardingModal
                                categories={[{ code: "CULTURE", label: "Culture", description: null, active: true }]}
                                onFinish={onFinish}
                                onLogout={vi.fn()}
                                onPostpone={vi.fn()}
                                onPreferencesSaved={vi.fn()}
                            />
                        )}
                        path="/"
                    />
                    <Route element={<p>Page du guide</p>} path="/guide" />
                </Routes>
            </MemoryRouter>,
        );

        const dialog = screen.getByRole("dialog", { name: /Bienvenue sur LocalLife/ });
        expect(within(dialog).queryByRole("link", { name: /guide/ })).toBeNull();

        await user.type(within(dialog).getByRole("textbox", { name: /Ta ville/ }), "lyon");
        await user.click(within(dialog).getByRole("checkbox", { name: "Culture" }));
        await user.click(within(dialog).getByRole("button", { name: "Continuer" }));

        const link = await screen.findByRole("link", { name: "Voir le guide de l'application" });
        expect(link).toHaveAttribute("href", "/guide");
        expect(screen.getByRole("dialog", { name: "Créer une alerte ?" })).toBeInTheDocument();

        await user.click(link);
        expect(onFinish).toHaveBeenCalledTimes(1);
        expect(screen.getByText("Page du guide")).toBeInTheDocument();
    });
});
