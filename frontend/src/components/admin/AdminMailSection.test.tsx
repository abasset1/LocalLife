import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminMailSection from "./AdminMailSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => vi.unstubAllGlobals());

describe("AdminMailSection", () => {
    it("envoie le test à l'adresse de l'administrateur, sans destinataire, et affiche la limite horaire", async () => {
        const user = userEvent.setup();
        let sent = 0;
        const bodies: unknown[] = [];
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            if (url === "/api/v1/admin/mail/test") {
                bodies.push(JSON.parse(String(init?.body)));
                sent += 1;
                return sent === 1
                    ? json({ ok: true, step: "envoi", message: "E-mail envoyé à admin@example.com." })
                    : json({ message: "Trop d'e-mails de test : 5 par heure au plus, réessaie plus tard." }, 429);
            }
            return json({ host: "smtp.example.com", port: 587, username: "u", passwordSet: true, auth: true,
                startTls: true, ssl: false, from: "noreply@example.com", warning: null });
        }));
        render(<AdminMailSection />);

        expect(await screen.findByText(/smtp\.example\.com:587/)).toBeInTheDocument();
        expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
        expect(screen.getByText(/envoyé à ton adresse/)).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Envoyer un e-mail de test" }));
        expect(await screen.findByText(/E-mail envoyé à admin@example\.com\./)).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Envoyer un e-mail de test" }));
        expect(await screen.findByRole("alert")).toHaveTextContent("5 par heure au plus");
        expect(bodies).toEqual([{}, {}]);
    });
});
