import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminCategoriesSection from "./AdminCategoriesSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

const CATEGORIES = [
    {
        code: "CULTURE", label: "Culture", description: "Spectacles", active: true,
        keywords: [
            { id: 10, keyword: "concert", scope: "ALL", active: true },
            { id: 11, keyword: "conférence", scope: "ALL", active: false },
        ],
    },
    { code: "SPORT", label: "Sport", description: null, active: false, keywords: [] },
];

function json(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function stubApi(writeStatus = 200) {
    const calls: { url: string; init?: RequestInit }[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url, init });
        if (!init?.method || init.method === "GET") {
            return json(CATEGORIES);
        }
        return writeStatus < 400 ? json({}, writeStatus) : json({ message: "Le mot-clé existe déjà." }, writeStatus);
    }));
    return calls;
}

function writes(calls: { url: string; init?: RequestInit }[]) {
    return calls.filter((call) => call.init?.method && call.init.method !== "GET")
        .map((call) => ({ url: call.url, method: call.init?.method, body: JSON.parse(call.init?.body as string) }));
}

describe("AdminCategoriesSection", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it("liste les catégories, leur état et leurs mots-clés", async () => {
        stubApi();
        render(<AdminCategoriesSection />);

        expect(await screen.findByText("Mots-clés : 1 actif(s) sur 2")).toBeInTheDocument();
        expect(screen.getByText("Désactivée")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Réactiver la catégorie Sport" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Désactiver le mot-clé concert" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Réactiver le mot-clé conférence" })).toBeInTheDocument();
    });

    it("ajoute une catégorie", async () => {
        const calls = stubApi(201);
        render(<AdminCategoriesSection />);
        await screen.findByText("Mots-clés : 1 actif(s) sur 2");

        const form = screen.getByRole("form", { name: "Nouvelle catégorie" });
        await userEvent.type(within(form).getByLabelText("Code"), "bien_etre");
        await userEvent.type(within(form).getByLabelText("Libellé"), "Bien-être");
        await userEvent.click(within(form).getByRole("button", { name: "Ajouter la catégorie" }));

        await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Catégorie « Bien-être » ajoutée."));
        expect(writes(calls)).toEqual([{
            url: "/api/v1/admin/categories", method: "POST",
            body: { code: "BIEN_ETRE", label: "Bien-être", description: "" },
        }]);
    });

    it("désactive une catégorie après confirmation et un mot-clé directement", async () => {
        const calls = stubApi();
        vi.spyOn(window, "confirm").mockReturnValue(true);
        render(<AdminCategoriesSection />);

        await userEvent.click(await screen.findByRole("button", { name: "Désactiver la catégorie Culture" }));
        await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Catégorie « Culture » désactivée."));
        await userEvent.click(screen.getByRole("button", { name: "Désactiver le mot-clé concert" }));
        await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Mot-clé « concert » désactivé."));

        expect(writes(calls)).toEqual([
            { url: "/api/v1/admin/categories/CULTURE", method: "PATCH", body: { active: false } },
            { url: "/api/v1/admin/categories/keywords/10", method: "PATCH", body: { active: false } },
        ]);
    });

    it("ajoute un mot-clé avec sa portée et affiche l'erreur du serveur", async () => {
        const calls = stubApi(409);
        render(<AdminCategoriesSection />);

        await userEvent.type(await screen.findByLabelText("Mot-clé à ajouter à Culture"), "opéra");
        await userEvent.selectOptions(screen.getByLabelText("Portée du mot-clé pour Culture"), "SOURCE");
        await userEvent.click(within(screen.getByRole("form", { name: "Nouveau mot-clé pour Culture" }))
            .getByRole("button", { name: "Ajouter" }));

        await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Le mot-clé existe déjà."));
        expect(writes(calls)).toEqual([{
            url: "/api/v1/admin/categories/CULTURE/keywords", method: "POST", body: { keyword: "opéra", scope: "SOURCE" },
        }]);
        expect(screen.getByLabelText("Mot-clé à ajouter à Culture")).toHaveValue("opéra");
    });

    it("filtre les mots-clés par recherche", async () => {
        stubApi();
        render(<AdminCategoriesSection />);
        await screen.findByText("Mots-clés : 1 actif(s) sur 2");

        await userEvent.type(screen.getByLabelText("Rechercher un mot-clé"), "CONC");

        expect(screen.getByText("concert")).toBeInTheDocument();
        expect(screen.queryByText("conférence")).not.toBeInTheDocument();
        expect(screen.queryByText("Sport")).not.toBeInTheDocument();
    });
});
