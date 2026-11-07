import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminAccountsSection from "./AdminAccountsSection";

vi.mock("../../auth/authStorage", () => ({
    getPayload: () => ({ userId: 1, email: "admin@example.com", role: "ADMIN" }),
    isAuthenticated: () => true,
    subscribeSession: () => () => undefined,
    refreshSession: async () => null,
    onAuthenticated: async () => null,
    clearSession: async () => undefined,
    clearSessionLocal: () => undefined,
    readCsrfToken: () => null,
}));

function account(overrides: Record<string, unknown>) {
    return {
        id: 7,
        username: "camille",
        email: "camille@example.com",
        role: "USER",
        emailVerified: true,
        createdAt: "2026-10-01T10:00:00",
        status: "ACTIVE",
        statusReason: null,
        statusChangedAt: null,
        ...overrides,
    };
}

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

type Route = (url: string, init?: RequestInit) => Response | undefined;

function stubFetch(route: Route) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const routed = route(url, init);
        if (routed) {
            return routed;
        }
        if (url.includes("/api/v1/admin/accounts/audit")) {
            return json({ items: [], total: 0, page: 0, size: 20 });
        }
        if (url.includes("/api/v1/admin/accounts?")) {
            return json({ items: [], total: 0, page: 0, size: 20 });
        }
        return json({ message: `route non simulée : ${url}` }, 500);
    });
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
}

describe("AdminAccountsSection", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("affiche l'offre de chaque compte à côté de son rôle, et une demande Pro en attente", async () => {
        stubFetch((url) => url.includes("/api/v1/admin/accounts?")
            ? json({
                items: [
                    account({ id: 7, username: "camille", email: "camille@example.com", plan: "PREMIUM" }),
                    account({ id: 8, username: "lou", email: "lou@example.com", plan: "FREE", requestedPlan: "PRO" }),
                ],
                total: 2, page: 0, size: 20,
            })
            : undefined);
        render(<AdminAccountsSection />);

        const camille = (await screen.findByText("camille")).closest("tr") as HTMLElement;
        expect(within(camille).getByText("Utilisateur")).toBeInTheDocument();
        expect(within(camille).getByText("Premium")).toHaveClass("admin-plan-badge-premium");
        const lou = screen.getByText("lou").closest("tr") as HTMLElement;
        expect(within(lou).getByText("Gratuit")).toBeInTheDocument();
        expect(within(lou).getByText("Pro demandé")).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: "Rôle" })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: "Type de compte" })).toBeInTheDocument();
    });

    it("affiche le nombre de connexions et la date de la dernière", async () => {
        stubFetch((url) => url.includes("/api/v1/admin/accounts?")
            ? json({
                items: [
                    account({ id: 7, username: "camille", loginCount: 12, lastLoginAt: "2026-11-03T18:42:00" }),
                    account({ id: 8, username: "lou", email: "lou@example.com", loginCount: 0, lastLoginAt: null }),
                ],
                total: 2, page: 0, size: 20,
            })
            : undefined);
        render(<AdminAccountsSection />);

        const camille = (await screen.findByText("camille")).closest("tr") as HTMLElement;
        const cells = within(camille).getAllByRole("cell").map((cell) => cell.textContent);
        expect(cells).toContain("12");
        expect(cells).toContain(new Date("2026-11-03T18:42:00").toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }));
        const lou = screen.getByText("lou").closest("tr") as HTMLElement;
        expect(within(lou).getAllByRole("cell").map((cell) => cell.textContent)).toEqual(expect.arrayContaining(["0", "—"]));
        expect(screen.getByRole("columnheader", { name: "Connexions" })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: "Dernière connexion" })).toBeInTheDocument();
    });

    it("affiche l'état vide", async () => {
        stubFetch(() => undefined);
        render(<AdminAccountsSection />);

        expect(await screen.findByText("Aucun compte ne correspond.")).toBeInTheDocument();
    });

    it("envoie la recherche et les filtres à l'API", async () => {
        const fetchMock = stubFetch(() => undefined);
        render(<AdminAccountsSection />);

        await userEvent.type(screen.getByPlaceholderText("e-mail, nom ou identifiant"), "camille");
        await userEvent.selectOptions(screen.getByLabelText(/Rôle/), "LOCALLIFE");
        await userEvent.selectOptions(screen.getByLabelText(/Statut/), "SUSPENDED");
        await userEvent.click(screen.getByRole("button", { name: "Rechercher" }));

        await vi.waitFor(() =>
            expect(fetchMock).toHaveBeenCalledWith(
                expect.stringContaining("/api/v1/admin/accounts?page=0&size=20&q=camille&role=LOCALLIFE&status=SUSPENDED"),
                expect.anything(),
            ),
        );
    });

    it("ne propose aucune action sur un administrateur", async () => {
        stubFetch((url) =>
            url.includes("/api/v1/admin/accounts?")
                ? json({ items: [account({ role: "ADMIN", email: "admin@example.com" })], total: 1, page: 0, size: 20 })
                : undefined,
        );
        render(<AdminAccountsSection />);

        const row = (await screen.findByText("admin@example.com")).closest("tr") as HTMLElement;
        expect(within(row).queryByRole("button", { name: "Suspendre" })).not.toBeInTheDocument();
        expect(within(row).queryByRole("button", { name: "Rôle Neiri" })).not.toBeInTheDocument();
        expect(within(row).queryByRole("button", { name: "Type de compte" })).not.toBeInTheDocument();
        expect(within(row).getByRole("button", { name: "Historique" })).toBeInTheDocument();
    });

    it("exige un motif pour suspendre, puis affiche le résultat", async () => {
        let suspended = false;
        const fetchMock = stubFetch((url, init) => {
            if (url.includes("/accounts/7/suspend") && init?.method === "POST") {
                suspended = true;
                return json(account({ status: "SUSPENDED", statusReason: "Spam répété" }));
            }
            if (url.includes("/api/v1/admin/accounts?")) {
                const current = suspended ? account({ status: "SUSPENDED", statusReason: "Spam répété" }) : account({});
                return json({ items: [current], total: 1, page: 0, size: 20 });
            }
            return undefined;
        });
        vi.stubGlobal("confirm", vi.fn(() => true));
        render(<AdminAccountsSection />);

        await userEvent.click(await screen.findByRole("button", { name: "Suspendre" }));
        const confirmButton = screen.getByRole("button", { name: "Confirmer la suspension" });
        expect(confirmButton).toBeDisabled();
        await userEvent.type(screen.getByLabelText(/Motif/), "Spam répété");
        await userEvent.click(confirmButton);
        await userEvent.type(screen.getByLabelText("Ton mot de passe (confirmation)"), "secret-admin");
        await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Suspendre" }));

        expect(await screen.findByText("camille@example.com est suspendu.")).toBeInTheDocument();
        expect(await screen.findByText("Motif : Spam répété")).toBeInTheDocument();
        const call = fetchMock.mock.calls.find(([url]) => String(url).includes("/suspend"));
        expect(JSON.parse(String(call?.[1]?.body))).toEqual({ reason: "Spam répété" });
        expect(new Headers(call?.[1]?.headers).get("X-Confirm-Password")).toBe("secret-admin");
    });

    it("affiche le refus de l'API tel qu'elle l'explique", async () => {
        stubFetch((url, init) => {
            if (url.includes("/accounts/7/role") && init?.method === "PATCH") {
                return json({ message: "Le rôle ADMIN ne s'attribue pas depuis l'application." }, 403);
            }
            if (url.includes("/api/v1/admin/accounts?")) {
                return json({ items: [account({})], total: 1, page: 0, size: 20 });
            }
            return undefined;
        });
        vi.stubGlobal("confirm", vi.fn(() => true));
        render(<AdminAccountsSection />);

        await userEvent.click(await screen.findByRole("button", { name: "Rôle Neiri" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Le rôle ADMIN ne s'attribue pas");
    });

    it("donne le rôle Neiri, et change le type de compte avec une date de fin facultative", async () => {
        const fetchMock = stubFetch((url, init) => {
            if (url.includes("/accounts/7/role") && init?.method === "PATCH") {
                return json(account({ role: "LOCALLIFE" }));
            }
            if (url.includes("/accounts/7/plan") && init?.method === "PUT") {
                return json(account({ plan: "PREMIUM" }));
            }
            if (url.includes("/api/v1/admin/accounts?")) {
                return json({ items: [account({ plan: "FREE" })], total: 1, page: 0, size: 20 });
            }
            return undefined;
        });
        vi.stubGlobal("confirm", vi.fn(() => true));
        render(<AdminAccountsSection />);

        await userEvent.click(await screen.findByRole("button", { name: "Rôle Neiri" }));
        expect(await screen.findByText("camille@example.com fait partie de l'équipe Neiri.")).toBeInTheDocument();
        const roleCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/role"));
        expect(JSON.parse(String(roleCall?.[1]?.body))).toEqual({ role: "LOCALLIFE" });

        await userEvent.click(screen.getByRole("button", { name: "Type de compte" }));
        await userEvent.selectOptions(screen.getByLabelText("Type de compte"), "PREMIUM");
        await userEvent.type(screen.getByLabelText(/Fin d'accès/), "2026-12-31");
        await userEvent.click(screen.getByRole("button", { name: "Appliquer" }));

        expect(await screen.findByText(/camille@example.com : compte Premium jusqu'au/)).toBeInTheDocument();
        const planCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/plan"));
        expect(JSON.parse(String(planCall?.[1]?.body))).toEqual({ plan: "PREMIUM", endsOn: "2026-12-31" });
    });

    it("affiche une erreur de chargement", async () => {
        stubFetch((url) =>
            url.includes("/api/v1/admin/accounts?") ? json({ message: "Accès refusé : rôle insuffisant" }, 403) : undefined,
        );
        render(<AdminAccountsSection />);

        expect(await screen.findByRole("alert")).toHaveTextContent("Accès refusé");
    });

    it("supprime un compte avec un motif et le mot de passe, et déconnecte partout", async () => {
        const fetchMock = stubFetch((url, init) => {
            if (url.includes("/accounts/7/deletion") && init?.method === "POST") {
                return new Response(null, { status: 204 });
            }
            if (url.includes("/accounts/7/logout-everywhere") && init?.method === "POST") {
                return new Response(null, { status: 204 });
            }
            if (url.includes("/api/v1/admin/accounts?")) {
                return json({ items: [account({})], total: 1, page: 0, size: 20 });
            }
            return undefined;
        });
        vi.stubGlobal("confirm", vi.fn(() => true));
        render(<AdminAccountsSection />);

        await userEvent.click(await screen.findByRole("button", { name: "Déconnecter partout" }));
        expect(await screen.findByText("camille@example.com est déconnecté partout.")).toBeInTheDocument();

        await userEvent.click(screen.getByRole("button", { name: "Supprimer le compte" }));
        const dialog = screen.getByRole("dialog");
        const submit = within(dialog).getByRole("button", { name: "Supprimer le compte" });
        await userEvent.type(within(dialog).getByLabelText("Motif (journalisé)"), "ok");
        await userEvent.type(within(dialog).getByLabelText("Ton mot de passe (confirmation)"), "secret-admin");
        expect(submit).toBeDisabled();
        await userEvent.type(within(dialog).getByLabelText("Motif (journalisé)"), " fraude");
        await userEvent.click(submit);

        expect(await screen.findByText("Le compte camille@example.com est supprimé.")).toBeInTheDocument();
        const call = fetchMock.mock.calls.find(([url]) => String(url).includes("/deletion"));
        expect(JSON.parse(String(call?.[1]?.body))).toEqual({ reason: "ok fraude" });
        expect(new Headers(call?.[1]?.headers).get("X-Confirm-Password")).toBe("secret-admin");
    });
});
