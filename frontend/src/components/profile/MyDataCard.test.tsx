import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { saveToken } from "../../auth/authStorage";
import MyDataCard from "./MyDataCard";

afterEach(() => {
    vi.unstubAllGlobals();
    delete (URL as Partial<typeof URL>).createObjectURL;
    delete (URL as Partial<typeof URL>).revokeObjectURL;
    localStorage.clear();
});

describe("MyDataCard", () => {
    it("télécharge l'export JSON avec le jeton de connexion", async () => {
        const user = userEvent.setup();
        saveToken("jwt-test");
        const fetchMock = vi.fn(async () => new Response(JSON.stringify({ account: { id: 1 } }), { status: 200 }));
        vi.stubGlobal("fetch", fetchMock);
        const createObjectURL = vi.fn((_blob: Blob) => "blob:locallife-export");
        // jsdom ne fournit pas URL.createObjectURL : ajouté pour ce test, retiré dans afterEach.
        Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() });
        let downloadedAs: string | null = null;
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            downloadedAs = this.download;
        });
        render(<MemoryRouter><MyDataCard /></MemoryRouter>);

        expect(screen.getByText(/supprimé après 30 jours/)).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Télécharger mes données" }));

        expect(await screen.findByText(/Fichier locallife-mes-donnees.json téléchargé/)).toBeInTheDocument();
        const [input, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
        expect(input).toBe("/api/v1/users/me/export");
        expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-test");
        expect(downloadedAs).toBe("locallife-mes-donnees.json");
        const blob = createObjectURL.mock.calls[0][0];
        expect(blob.type).toBe("application/json");
        expect(blob.size).toBe('{"account":{"id":1}}'.length);
    });
});
