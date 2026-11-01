import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ProfilePage from "./ProfilePage";

beforeEach(() => {
    window.localStorage.setItem("locallife.jwt", `x.${btoa(JSON.stringify({ userId: 7, email: "a@b.c", role: "USER" }))}.y`);
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
            let body: unknown = [];
            if (url === "/api/v1/users/me") {
                body = {
                    id: 7,
                    username: "alice",
                    email: "a@b.c",
                    role: "USER",
                    createdAt: "2026-01-01T00:00:00Z",
                    hasPassword: true,
                    hasProAccess: false,
                    canContribute: false,
                };
            } else if (url.startsWith("/api/v1/users/me/subscription")) {
                body = { plan: "FREE", current: null, pending: null, history: [] };
            } else if (url.startsWith("/api/v1/users/me/preferences")) {
                body = {
                    defaultCity: null, defaultLatitude: null, defaultLongitude: null,
                    defaultRadiusKm: null, defaultCategoryCodes: [], configured: false,
                };
            }
            return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
        }),
    );
});

afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
});

describe("ProfilePage — guide de l'application", () => {
    it("propose de revoir le guide depuis le profil", async () => {
        const user = userEvent.setup();
        render(
            <MemoryRouter initialEntries={["/profile"]}>
                <Routes>
                    <Route element={<ProfilePage />} path="/profile" />
                    <Route element={<p>Page du guide</p>} path="/guide" />
                </Routes>
            </MemoryRouter>,
        );

        expect(await screen.findByRole("heading", { name: "Guide de l'application" })).toBeInTheDocument();
        await user.click(screen.getByRole("link", { name: "Revoir le guide" }));
        expect(screen.getByText("Page du guide")).toBeInTheDocument();
    });
});
