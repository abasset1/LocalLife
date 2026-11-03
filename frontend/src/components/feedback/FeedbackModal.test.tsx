import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import FeedbackModal from "./FeedbackModal";

afterEach(() => vi.unstubAllGlobals());

function stubFetch(status: number, body: unknown) {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), {
        status, headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
}

describe("FeedbackModal", () => {
    it("envoie une anomalie avec la page d'origine, puis remercie", async () => {
        const user = userEvent.setup();
        window.history.replaceState(null, "", "/?lat=43.9&lng=4.8&zoom=9");
        const fetchMock = stubFetch(201, { id: 12 });
        render(<FeedbackModal onClose={vi.fn()} />);

        await user.click(screen.getByRole("radio", { name: "🐞 Un problème" }));
        await user.type(screen.getByLabelText("Ton message"), "La carte reste grise sur mon téléphone");
        await user.click(screen.getByRole("button", { name: "Envoyer" }));

        expect(await screen.findByText("🙏 Merci !")).toBeInTheDocument();
        const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
        expect(url).toBe("/api/v1/feedback");
        expect(JSON.parse(String(init.body))).toEqual({
            kind: "BUG",
            message: "La carte reste grise sur mon téléphone",
            pageUrl: "/?lat=43.9&lng=4.8&zoom=9",
        });
        window.history.replaceState(null, "", "/");
    });

    it("refuse un message trop court sans appeler le serveur, et affiche l'erreur du serveur", async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch(400, { message: "Tu as déjà envoyé beaucoup de messages : réessaie dans une heure." });
        render(<FeedbackModal onClose={vi.fn()} />);

        await user.type(screen.getByLabelText("Ton message"), "ok");
        await user.click(screen.getByRole("button", { name: "Envoyer" }));
        expect(screen.getByRole("alert")).toHaveTextContent("Écris quelques mots");
        expect(fetchMock).not.toHaveBeenCalled();

        await user.type(screen.getByLabelText("Ton message"), " un mode sombre");
        await user.click(screen.getByRole("button", { name: "Envoyer" }));
        expect(await screen.findByRole("alert")).toHaveTextContent("réessaie dans une heure");
    });

    it("se ferme avec Échap", async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        render(<FeedbackModal onClose={onClose} />);
        await user.keyboard("{Escape}");
        expect(onClose).toHaveBeenCalled();
    });
});
